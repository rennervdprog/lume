package app.lume.imaging

import android.graphics.Bitmap
import android.opengl.GLES20
import app.lume.core.Recipe
import jp.co.cyberagent.android.gpuimage.GPUImageRenderer
import jp.co.cyberagent.android.gpuimage.PixelBuffer
import jp.co.cyberagent.android.gpuimage.filter.GPUImageBilateralBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageColorBalanceFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageContrastFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageExposureFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilterGroup
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGammaFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageGaussianBlurFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHazeFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHueFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageLevelsFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageRGBFilter
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
 * → balanço de branco → contraste → saturação → vibração → níveis →
 * balanço de cor → gama → giro de cor → RGB → HSL → curva composta
 * (master + faixas tonais + desbotado) → LUT de acabamento → efeito
 * artístico → suavizar pele → desfoque → claridade → mistura (2ª
 * foto) → névoa → vinheta. O grão NÃO está na cadeia: a GPUImage não
 * tem filtro de grão e o campo [Recipe.grain] fica preservado nos
 * dados para um filtro próprio futuro — sem slider falso na UI.
 *
 * O efeito artístico é o único filtro que TROCA de instância: o
 * grupo é reconstruído quando o efeito muda ([rebuildGroup]) e o
 * renderer recebe o grupo novo ([GPUImageRenderer.setFilter] — o
 * mesmo gesto da câmera ao vivo ao trocar de filtro). As texturas
 * de LUT e mistura são reenviadas depois da reconstrução (os
 * rastreadores são zerados junto).
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
    private val levelsFilter = GPUImageLevelsFilter()
    private val colorBalanceFilter = GPUImageColorBalanceFilter()
    private val gammaFilter = GPUImageGammaFilter()
    private val hueFilter = GPUImageHueFilter()
    private val rgbFilter = GPUImageRGBFilter()
    private val hslFilter = HslMixerFilter()
    private val toneCurveFilter = GPUImageToneCurveFilter()
    private val lutFilter = LutFilter()
    private val bilateralFilter = GPUImageBilateralBlurFilter()
    private val gaussianFilter = GPUImageGaussianBlurFilter()
    private val sharpenFilter = GPUImageSharpenFilter()
    private val blendFilter = BlendFilter()
    private val hazeFilter = GPUImageHazeFilter()
    private val vignetteFilter = GPUImageVignetteFilter()

    /** Uma instância por efeito artístico, criada sob demanda. */
    private val artisticFilters = mutableMapOf<String, GPUImageFilter>()
    private var currentArtisticId: String? = null

    private var group: GPUImageFilterGroup = buildGroup(null)

    private var buffer: PixelBuffer? = null
    private var renderer: GPUImageRenderer? = null
    private var bufferWidth: Int = 0
    private var bufferHeight: Int = 0
    private var maxTextureSize: Int = 0
    private var staticsSet: Boolean = false

    /**
     * A grade LUT cuja textura já está na GPU. Subir a textura a
     * cada render custaria um upload de 1 MB por gesto de slider —
     * só sobe quando a instância muda. Zerado quando o grupo é
     * destruído (a textura morre com o contexto).
     */
    private var lastLutGrid: Bitmap? = null

    /** A segunda foto cuja textura já está na GPU (mesma regra do LUT). */
    private var lastBlendBitmap: Bitmap? = null

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
     * desliga o LUT sem tocar na textura. [blendBitmap] é a segunda
     * foto da mistura (resolvida pelo chamador): null, sem modo ou
     * opacidade 0 desliga a mistura.
     */
    fun render(
        recipe: Recipe,
        lutGrid: Bitmap? = null,
        blendBitmap: Bitmap? = null
    ): Bitmap {
        val active = buffer ?: throw IllegalStateException("setSource não chamado.")
        applyRecipe(recipe.clamped(), lutGrid, blendBitmap)
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
        staticsSet = false
        lastLutGrid = null
        lastBlendBitmap = null
        currentArtisticId = null
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
        // O grupo foi destruído acima: as texturas de LUT e mistura
        // morreram com o contexto antigo — o próximo render sobe as
        // grades de novo.
        lastLutGrid = null
        lastBlendBitmap = null
        if (!staticsSet) {
            // O centro padrão do filtro é (0,0) — o canto. Centro real
            // é o meio da foto; cor preta, igual ao motor anterior.
            vignetteFilter.setVignetteCenter(PointF(0.5f, 0.5f))
            vignetteFilter.setVignetteColor(floatArrayOf(0f, 0f, 0f))
            // O balanço de cor preserva a luminosidade: mexer no
            // quente/frio de uma faixa não clareia nem escurece ela.
            colorBalanceFilter.setPreserveLuminosity(true)
            staticsSet = true
        }
    }

    /**
     * Monta o grupo com a cadeia base + o efeito artístico pedido
     * (null = sem efeito). Os filtros base são as MESMAS instâncias
     * em todo grupo: ao reconstruir, o programa antigo morre e o
     * filtro re-inicializa no primeiro desenho, reaplicando os
     * valores guardados nos campos (padrão da própria biblioteca).
     */
    private fun buildGroup(artistic: GPUImageFilter?): GPUImageFilterGroup {
        val filters = mutableListOf<GPUImageFilter>(
            exposureFilter,
            whiteBalanceFilter,
            contrastFilter,
            saturationFilter,
            vibranceFilter,
            levelsFilter,
            colorBalanceFilter,
            gammaFilter,
            hueFilter,
            rgbFilter,
            hslFilter,
            toneCurveFilter,
            lutFilter
        )
        if (artistic != null) {
            filters.add(artistic)
        }
        filters.add(bilateralFilter)
        filters.add(gaussianFilter)
        filters.add(sharpenFilter)
        filters.add(blendFilter)
        filters.add(hazeFilter)
        filters.add(vignetteFilter)
        return GPUImageFilterGroup(filters)
    }

    /**
     * Troca o efeito artístico: destrói o grupo, monta outro com a
     * instância do efeito novo e entrega ao renderer. As texturas de
     * LUT e mistura são zeradas aqui: o filtro próprio perdeu o
     * `pendingBitmap` no onDestroy e precisa do reenvio.
     */
    private fun rebuildGroup(artisticId: String?) {
        val artistic = artisticId?.let { id ->
            val effect = ArtisticEffect.fromId(id) ?: return@let null
            artisticFilters.getOrPut(id) { createArtisticFilter(effect) }
        }
        try {
            group.destroy()
        } catch (ignored: Exception) {
            // Melhor esforço: o contexto pode já estar morto.
        }
        group = buildGroup(artistic)
        renderer?.setFilter(group)
        lastLutGrid = null
        lastBlendBitmap = null
        currentArtisticId = artisticId
    }

    /**
     * Aplica a receita aos filtros. Cada setter enfileira um runnable
     * (runOnDraw) que roda no próximo desenho — por isso isto pode ser
     * chamado de qualquer thread, mas na prática roda na thread GL.
     * As faixas de cada filtro foram lidas no fonte da GPUImage 2.1.0.
     */
    private fun applyRecipe(recipe: Recipe, lutGrid: Bitmap?, blendBitmap: Bitmap?) {
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
        // Níveis: preto 0..1, branco 0..1, meio-tom 0.1..1.9 (1 =
        // neutro). O shader divide por (max-min) e por mid: as duas
        // guardas evitam divisão por zero quando os sliders se cruzam.
        var levelsMin = (recipe.levelsBlack / 100f).coerceIn(0f, 0.95f)
        var levelsMax = (1f - recipe.levelsWhite / 100f).coerceIn(0.05f, 1f)
        if (levelsMax - levelsMin < 0.05f) {
            // Sliders cruzados: mantém a janela mínima de 0.05
            // onde couber — empurra o branco, senão recua o preto.
            levelsMax = (levelsMin + 0.05f).coerceAtMost(1f)
            if (levelsMax - levelsMin < 0.05f) {
                levelsMin = (levelsMax - 0.05f).coerceAtLeast(0f)
            }
        }
        val levelsMid = (1f + recipe.levelsMid / 100f).coerceIn(0.1f, 1.9f)
        levelsFilter.setMin(levelsMin, levelsMid, levelsMax)
        // Balanço de cor por faixa: o slider -100..100 vira um
        // deslocamento quente/frio (±0.5 no vermelho, inverso no
        // azul, verde parado). O nome do setter tem erro de grafia
        // na biblioteca (setShowdows) — é a API real, não erro nosso.
        val shadowShift = recipe.cbShadows / 100f * 0.5f
        val midShift = recipe.cbMidtones / 100f * 0.5f
        val highlightShift = recipe.cbHighlights / 100f * 0.5f
        colorBalanceFilter.setShowdows(floatArrayOf(shadowShift, 0f, -shadowShift))
        colorBalanceFilter.setMidtones(floatArrayOf(midShift, 0f, -midShift))
        colorBalanceFilter.setHighlights(floatArrayOf(highlightShift, 0f, -highlightShift))
        // Gama: o shader eleva a cor a `gamma`. Slider +100 clareia
        // os meios-tons (gama 0.5), -100 escurece (gama 1.5).
        gammaFilter.setGamma((1f - recipe.gamma / 100f * 0.5f).coerceIn(0.1f, 1.9f))
        // Giro de cor global: ±100 do slider = ±180° de matiz.
        hueFilter.setHue(recipe.hueShift / 100f * 180f)
        // RGB: ganho por canal, 1 é neutro; 0 = canal zerado, 2 = dobro.
        rgbFilter.setRed(1f + recipe.rgbR / 100f)
        rgbFilter.setGreen(1f + recipe.rgbG / 100f)
        rgbFilter.setBlue(1f + recipe.rgbB / 100f)
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
        // Efeito artístico: intensidade 0 conta como "sem efeito"
        // (o grupo sem ele é mais barato que o filtro no mínimo).
        val effectiveArtisticId =
            if (recipe.artisticEffect != null && recipe.artisticIntensity > 0.001f) {
                recipe.artisticEffect
            } else {
                null
            }
        if (effectiveArtisticId != currentArtisticId) {
            rebuildGroup(effectiveArtisticId)
        }
        effectiveArtisticId?.let { id ->
            val filter = artisticFilters[id]
            val effect = ArtisticEffect.fromId(id)
            if (filter != null && effect != null) {
                applyArtisticIntensity(
                    filter,
                    effect,
                    recipe.artisticIntensity,
                    bufferWidth,
                    bufferHeight
                )
            }
        }
        // Suavizar pele (bilateral): o fator da biblioteca é
        // INVERTIDO — maior fator = MENOS suavização (mais borda
        // preservada). Slider 0 = fator 8 (padrão, sem efeito),
        // 100 = fator 1 (suavização máxima). Em foto grande o raio
        // fixo de ±4 texels é sutil; no preview o efeito é visível.
        bilateralFilter.setDistanceNormalizationFactor(8f - recipe.smoothSkin / 100f * 7f)
        // Desfoque gaussiano: tamanho 0..4 (0 = identidade, o
        // kernel de 9 amostras lê o mesmo texel).
        gaussianFilter.setBlurSize(recipe.blur / 100f * 4f)
        // Claridade via nitidez: -4..4 no filtro, usamos ±2. É uma
        // aproximação honesta (o motor anterior também aproximava).
        sharpenFilter.setSharpness(recipe.clarity / 100f * 2f)
        // Mistura (dupla exposição): sem segunda foto, sem modo ou
        // opacidade 0, o filtro vira passagem direta. A textura só
        // sobe quando a foto muda de instância.
        val blendMode = BlendMode.fromId(recipe.blendMode)
        if (blendBitmap != null && blendMode != null && recipe.blendOpacity > 0f) {
            if (blendBitmap !== lastBlendBitmap) {
                blendFilter.setBlendBitmap(blendBitmap)
                lastBlendBitmap = blendBitmap
            }
            blendFilter.setMode(blendMode)
            blendFilter.setOpacity(recipe.blendOpacity)
        } else {
            blendFilter.setOpacity(0f)
        }
        // Névoa: o shader subtrai um véu branco e divide por (1-d).
        // Distância POSITIVA desembaça (escurece, mais contraste);
        // negativa adiciona névoa (clareia, menos contraste). Slider
        // +100 = desembaçar total, -100 = névoa total.
        hazeFilter.setDistance(recipe.haze / 100f * 0.25f)
        hazeFilter.setSlope(0f)
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
