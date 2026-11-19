package app.lume.imaging

import android.graphics.Bitmap
import app.lume.core.Recipe
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ponto único de acesso ao motor GL — e a fronteira do fallback.
 *
 * O motor é a GPUImage (biblioteca gratuita, Apache 2.0): uma cadeia
 * de filtros dela desenhada offscreen por [GpuImagePipeline]. O shader
 * próprio da Fase 1 foi aposentado em 17/11/2026 — o HSL dele não
 * funcionava no aparelho (uniforms em array voltavam -1 em algumas
 * GPUs, em silêncio). O único filtro próprio que restou é o mixer
 * HSL ([HslMixerFilter]), sem arrays de uniforms.
 *
 * - Todo trabalho GL roda numa thread dedicada ([glDispatcher]): o
 *   contexto EGL é criado e usado sempre na mesma thread, como exige
 *   o EGL e o PixelBuffer. Preview e exportação compartilham a
 *   thread (renders são sequenciais e curtos); cada um usa a mesma
 *   instância de [GpuImagePipeline], que guarda a textura da foto
 *   atual.
 * - [render] NUNCA estoura: qualquer falha (sem EGL, shader, textura
 *   acima do limite da GPU) retorna null e o chamador cai para o
 *   caminho da matriz ([PhotoExporter.render], CPU). O app nunca quebra
 *   por causa do motor — no pior caso, HSL/curva/efeitos ficam sem
 *   efeito naquela foto, e a exportação continua funcionando.
 * - A textura da foto atual fica na GPU entre renders (o gesto de
 *   slider só redesenha). Trocar de foto faz upload da nova textura;
 *   [release] libera tudo (chamado ao sair do editor).
 */
object GlEngine {

    // Executor de uma thread só, vivo enquanto o app viver: o contexto
    // EGL é criado e usado sempre nesta thread. (newSingleThreadContext
    // faria o mesmo, mas é API delicada; o executor é API estável.)
    private val glDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LumeGL")
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + glDispatcher)

    private var pipeline: GpuImagePipeline? = null
    private var currentSource: Bitmap? = null

    /**
     * Renderiza [recipe] sobre [source] via GPUImage. Retorna o bitmap
     * editado, ou null se o motor falhar (chamador usa o fallback).
     * [lutGrid] é o bitmap 512×512 da grade do LUT da receita,
     * resolvido pelo chamador ([LutGrids]): o motor só sobe a
     * textura — nunca lê arquivo nem gera grade na thread GL.
     * [blendBitmap] é a segunda foto da mistura, resolvida pelo
     * chamador: o motor só sobe a textura.
     */
    suspend fun render(
        source: Bitmap,
        recipe: Recipe,
        lutGrid: Bitmap? = null,
        blendBitmap: Bitmap? = null
    ): Bitmap? =
        withContext(glDispatcher) {
            try {
                val active = pipeline ?: GpuImagePipeline().also {
                    pipeline = it
                }
                if (currentSource !== source) {
                    active.setSource(source)
                    currentSource = source
                }
                active.render(recipe, lutGrid, blendBitmap)
            } catch (e: CancellationException) {
                // Cancelamento de gesto (novo render entrou): nunca
                // engolir — senão o render velho atualiza a tela.
                throw e
            } catch (e: Exception) {
                // Motor fora: descarta o pipeline (pode estar
                // parcialmente inicializado) e deixa o fallback agir.
                // A próxima chamada tenta inicializar de novo.
                try {
                    pipeline?.release()
                } catch (ignored: Exception) {
                    // Liberar nunca pode estourar para o chamador.
                }
                pipeline = null
                currentSource = null
                null
            }
        }

    /** Libera o contexto EGL e as texturas. Assíncrono, nunca bloqueia. */
    fun release() {
        scope.launch {
            try {
                pipeline?.release()
            } catch (ignored: Exception) {
                // Melhor esforço.
            }
            pipeline = null
            currentSource = null
        }
    }
}
