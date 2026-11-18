package app.lume.imaging

import android.graphics.Bitmap
import android.graphics.PointF
import android.opengl.GLES20
import app.lume.core.Recipe
import jp.co.cyberagent.android.gpuimage.GPUImageRenderer
import jp.co.cyberagent.android.gpuimage.PixelBuffer
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageExposureFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSaturationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSharpenFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToneCurveFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVibranceFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageVignetteFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageWhiteBalanceFilter

/**
 * O motor de render: uma cadeia de filtros da GPUImage (biblioteca
 * gratuita, Apache 2.0 — a mesma base que o Polish usa) desenhada
 * offscreen num pbuffer persistente.
 *
 * Por que persistente: a API de um tiro da GPUImage
 * (`getBitmapWithFilterApplied`) recria o contexto EGL e recompila
 * todos os shaders a cada chamada — lento demais para arrastar um
 * slider. Aqui o [PixelBuffer] e o [GPUImageRenderer] vivem entre
 * renders (o mesmo padrão que a própria GPUImage usa em
 * `getBitmapForMultipleFilters`): os programas compilam uma vez, a
 * textura da foto fica na GPU e cada gesto só atualiza parâmetros.
 *
 * CONFINAMENTO DE THREAD: o contexto EGL pertence à thread que o
 * criou, e o PixelBuffer recusa trabalhar em outra thread. TODOS os
 * métodos desta classe devem ser chamados sempre da mesma thread —
 * quem garante isso é o [GlEngine]. Chamar de outra thread devolve
 * null no getBitmap e vira exceção (o chamador cai para o fallback).
 *
 * Ordem da cadeia (contrato de portabilidade de presets): exposição
 * → balanço de branco → contraste → saturação → vibração → HSL →
 * curva composta (master + faixas tonais + desbotado) → LUT de
 * acabamento → claridade → vinheta. O grão NÃO está na cadeia: a
 * GPUImage não tem filtro de grão e o campo [Recipe.grain] fica
 * preservado nos dados para um filtro próprio futuro — sem slider
 * falso na UI.
 *
 * Limite honesto: fotos maiores que o limite de textura da GPU
 * ([GLES20.GL_MAX_TEXTURE_SIZE]) estouram em [setSource] — o chamador
 * cai para o caminho da matriz (CPU, sem limite de tamanho). Tiles
 * ficam para uma fase futura.
 */
internal class GpuImagePipeline {

    private val exposureFilter = GPUImageExposureFilter()
    private val whiteBalanceFilter = GPUImageWhiteBalanceFilter()
    private val contrastFilter = GPUImageContrastFilter()
    private val saturationFilter = GPUImageSaturationFilter()
    private val vibranceFilter = GPUImageVibranceFilter()
    private val hslFilter = HslMixerFilter()
    private val toneCurveFilter = GPUImageToneCurveFilter()
    private val lutFilter = LutFilter()
    private val sharpenFilter = GPUImageSharpenFilter()
    private val vignetteFilter = GPUImageVignetteFilter()

    private val group = GPUImageFilterGroup(
        listOf(
            exposureFilter,
            whiteBalanceFilter,
            contrastFilter,
            saturationFilter,
            vibranceFilter,
            hslFilter,
            toneCurveFilter,
            lutFilter,
            sharpenFilter,
            vignetteFilter
        )
    )

    private var buffer: PixelBuffer? = null
    private var renderer: GPUImageRenderer? = null
    private var bufferWidth: Int = 0
    private var bufferHeight: Int = 0
    private var maxTextureSize: Int = 0
    private var vignetteStaticSet: Boolean = false

    /**
     * A grade LUT cuja textura já está na GPU. Subir a textura a
     * cada render custaria um upload de 1 MB por gesto de slider —
     * só sobe quando a instância muda. Zerado quando o grupo é
     * destruído (a textura morre com o contexto).
     */
    private var lastLutGrid: Bitmap? = null

    /**
     * Sobe [bitmap] como textura. Recria o pbuffer se o tamanho mudou.
     * A textura é uma cópia na GPU: reciclar o bitmap depois é seguro.
     * O bitmap NUNCA é reciclado aqui (quem gerencia é o chamador).
     */
    fun setSource(bitmap: Bitmap) {
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap vazio." }
        if (buffer == null || bufferWidth != bitmap.width || bufferHeight != bitmap.height) {
            recreateBuffer(bitmap.width, bitmap.height)
        }
        renderer?.setImageBitmap(bitmap, false)
    }

    /**
     * Renderiza [recipe] sobre a foto de [setSource] e devolve um
     * bitmap novo. [lutGrid] é o bitmap 512×512 da grade do LUT da
     * receita (resolvido pelo chamador): null ou intensidade 0
     * desliga o LUT sem tocar na textura.
     */
    fun render(recipe: Recipe, lutGrid: Bitmap? = null): Bitmap {
        val active = buffer ?: throw IllegalStateException("setSource não chamado.")
        applyRecipe(recipe.clamped(), lutGrid)
        return active.getBitmap()
            ?: throw IllegalStateException("PixelBuffer sem renderer (thread errada?).")
    }

    /** Libera tudo. Nunca estoura — falhar ao liberar não pode quebrar a edição. */
    fun release() {
        try {
            group.destroy()
        } catch (ignored: Exception) {
            // Melhor esforço.
        }
        try {
            renderer?.deleteImage()
        } catch (ignored: Exception) {
            // Melhor esforço.
        }
        try {
            buffer?.destroy()
        } catch (ignored: Exception) {
            // Melhor esforço.
        }
        buffer = null
        renderer = null
        bufferWidth = 0
        bufferHeight = 0
        maxTextureSize = 0
        vignetteStaticSet = false
        lastLutGrid = null
    }

    private fun recreateBuffer(width: Int, height: Int) {
        // Destrói o grupo no contexto ANTIGO (programas + FBOs morrem
        // lá); os filtros re-inicializam no primeiro desenho do novo
        // contexto, via ifNeedInit do onSurfaceCreated.
        try {
            group.destroy()
        } catch (ignored: Exception) {
            // Melhor esforço: o contexto pode já estar morto.
        }
        try {
            buffer?.destroy()
        } catch (ignored: Exception) {
            // Melhor esforço.
        }
        buffer = null
        renderer = null
        val freshBuffer = PixelBuffer(width, height)
        val freshRenderer = GPUImageRenderer(group)
        freshBuffer.setRenderer(freshRenderer)
        if (maxTextureSize == 0) {
            val value = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, value, 0)
            maxTextureSize = value[0]
        }
        if (maxTextureSize > 0 && (width > maxTextureSize || height > maxTextureSize)) {
            try {
                freshBuffer.destroy()
            } catch (ignored: Exception) {
                // Melhor esforço.
            }
            throw IllegalStateException(
                "Foto maior que o limite de textura da GPU ($maxTextureSize)."
            )
        }
        buffer = freshBuffer
        renderer = freshRenderer
        bufferWidth = width
        bufferHeight = height
        // O grupo foi destruído acima: a textura do LUT morreu com
        // o contexto antigo — o próximo render sobe a grade de novo.
        lastLutGrid = null
        if (!vignetteStaticSet) {
            // O centro padrão do filtro é (0,0) — o canto. Centro real
            // é o meio da foto; cor preta, igual ao motor anterior.
            vignetteFilter.setVignetteCenter(PointF(0.5f, 0.5f))
            vignetteFilter.setVignetteColor(floatArrayOf(0f, 0f, 0f))
            vignetteStaticSet = true
        }
    }

    /**
     * Aplica a receita aos filtros. Cada setter enfileira um runnable
     * (runOnDraw) que roda no próximo desenho — por isso isto pode ser
     * chamado de qualquer thread, mas na prática roda na thread GL.
     * As faixas de cada filtro foram lidas no fonte da GPUImage 2.1.0.
     */
    private fun applyRecipe(recipe: Recipe, lutGrid: Bitmap?) {
        // Exposição: o shader multiplica por 2^exposure — a receita já
        // está em EV (-2..2), sem conversão.
        exposureFilter.setExposure(recipe.exposure)
        // Balanço de branco: 5000K é neutro; o setter normaliza para
        // a mistura do shader. ±100 do slider = ±3000K.
        whiteBalanceFilter.setTemperature(5000f + recipe.temperature * 30f)
        whiteBalanceFilter.setTint(recipe.tint)
        // Contraste: 1 é neutro; mesma inclinação do motor anterior.
        contrastFilter.setContrast(1f + recipe.contrast * 0.9f / 100f)
        // Saturação: 1 é neutro; 0 = cinza, 2 = dobro.
        saturationFilter.setSaturation(1f + recipe.saturation / 100f)
        // Vibração: 0 é neutro; faixa -1..1.
        vibranceFilter.setVibrance(recipe.vibrance / 100f)
        hslFilter.setHsl(recipe.hsl)
        // Curva composta (master + faixas + desbotado). As curvas RGB
        // ficam na identidade padrão do filtro (reservadas).
        val points = ComposedCurve.points(recipe)
            .map { PointF(it.x, it.y) }
            .toTypedArray()
        toneCurveFilter.setRgbCompositeControlPoints(points)
        // LUT de acabamento: sem grade (LUT importado apagado, por
        // exemplo) ou intensidade 0, o filtro vira passagem direta.
        // A textura só sobe quando a grade muda de instância.
        if (lutGrid != null && recipe.lutIntensity > 0f) {
            if (lutGrid !== lastLutGrid) {
                lutFilter.setLutBitmap(lutGrid)
                lastLutGrid = lutGrid
            }
            lutFilter.setIntensity(recipe.lutIntensity)
        } else {
            lutFilter.setIntensity(0f)
        }
        // Claridade via nitidez: -4..4 no filtro, usamos ±2. É uma
        // aproximação honesta (o motor anterior também aproximava).
        sharpenFilter.setSharpness(recipe.clarity / 100f * 2f)
        // Vinheta: o filtro só escurece. Zero desliga (início=fim
        // além do canto mais distante); negativo vira zero.
        val vignette = recipe.vignette.coerceAtLeast(0f)
        if (vignette <= 0f) {
            vignetteFilter.setVignetteStart(2f)
            vignetteFilter.setVignetteEnd(2f)
        } else {
            vignetteFilter.setVignetteStart(0.75f - vignette / 100f * 0.45f)
            vignetteFilter.setVignetteEnd(1f - vignette / 100f * 0.25f)
        }
    }
}
