package app.lume.imaging

import android.graphics.Bitmap
import android.graphics.PointF
import app.lume.core.Recipe
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
 * Grupo de filtros da câmera ao vivo (0.14.0): a MESMA cadeia do
 * [GpuImagePipeline], montada do zero a cada troca de preset — a
 * câmera não tem sliders, então não precisa de instâncias
 * persistentes. A receita do preset entra inteira (v1–v4), menos a
 * mistura: sem segunda foto no visor, não há o que misturar. As
 * faixas são as mesmas do motor ([GpuImagePipeline.applyRecipe] é a
 * referência — se mudar lá, mudar aqui).
 *
 * Instâncias novas a cada chamada: o [GPUImageFilterGroup] é
 * entregue ao visor ([GPUImageView.setFilter]) e inicializado na
 * thread GL dele; nada aqui toca em GL.
 */
fun buildCameraFilterGroup(recipe: Recipe, lutGrid: Bitmap?): GPUImageFilterGroup {
    val r = recipe.clamped()
    val exposure = GPUImageExposureFilter().apply { setExposure(r.exposure) }
    val whiteBalance = GPUImageWhiteBalanceFilter().apply {
        setTemperature(5000f + r.temperature * 30f)
        setTint(r.tint)
    }
    val contrast = GPUImageContrastFilter().apply {
        setContrast(1f + r.contrast * 0.9f / 100f)
    }
    val saturation = GPUImageSaturationFilter().apply {
        setSaturation(1f + r.saturation / 100f)
    }
    val vibrance = GPUImageVibranceFilter().apply { setVibrance(r.vibrance / 100f) }
    var levelsMin = (r.levelsBlack / 100f).coerceIn(0f, 0.95f)
    var levelsMax = (1f - r.levelsWhite / 100f).coerceIn(0.05f, 1f)
    if (levelsMax - levelsMin < 0.05f) {
        levelsMax = (levelsMin + 0.05f).coerceAtMost(1f)
        if (levelsMax - levelsMin < 0.05f) {
            levelsMin = (levelsMax - 0.05f).coerceAtLeast(0f)
        }
    }
    val levelsMid = (1f + r.levelsMid / 100f).coerceIn(0.1f, 1.9f)
    val levels = GPUImageLevelsFilter().apply {
        setMin(levelsMin, levelsMid, levelsMax)
    }
    val colorBalance = GPUImageColorBalanceFilter().apply {
        val shadowShift = r.cbShadows / 100f * 0.5f
        val midShift = r.cbMidtones / 100f * 0.5f
        val highlightShift = r.cbHighlights / 100f * 0.5f
        setShowdows(floatArrayOf(shadowShift, 0f, -shadowShift))
        setMidtones(floatArrayOf(midShift, 0f, -midShift))
        setHighlights(floatArrayOf(highlightShift, 0f, -highlightShift))
        setPreserveLuminosity(true)
    }
    val gamma = GPUImageGammaFilter().apply {
        setGamma((1f - r.gamma / 100f * 0.5f).coerceIn(0.1f, 1.9f))
    }
    val hue = GPUImageHueFilter().apply { setHue(r.hueShift / 100f * 180f) }
    val rgb = GPUImageRGBFilter().apply {
        setRed(1f + r.rgbR / 100f)
        setGreen(1f + r.rgbG / 100f)
        setBlue(1f + r.rgbB / 100f)
    }
    val hsl = HslMixerFilter().apply { setHsl(r.hsl) }
    val toneCurve = GPUImageToneCurveFilter().apply {
        val points = ComposedCurve.points(r)
            .map { PointF(it.x, it.y) }
            .toTypedArray()
        setRgbCompositeControlPoints(points)
    }
    val lut = LutFilter().apply {
        if (lutGrid != null && r.lutIntensity > 0f) {
            setLutBitmap(lutGrid)
            setIntensity(r.lutIntensity)
        } else {
            setIntensity(0f)
        }
    }
    val filters = mutableListOf<GPUImageFilter>(
        exposure,
        whiteBalance,
        contrast,
        saturation,
        vibrance,
        levels,
        colorBalance,
        gamma,
        hue,
        rgb,
        hsl,
        toneCurve,
        lut
    )
    val effect = ArtisticEffect.fromId(r.artisticEffect)
    if (effect != null && r.artisticIntensity > 0.001f) {
        val artistic = createArtisticFilter(effect)
        // Sem o tamanho do quadro aqui: a proporção do Meio-tom
        // fica para o onOutputSizeChanged do próprio filtro.
        applyArtisticIntensity(artistic, effect, r.artisticIntensity, 0, 0)
        filters.add(artistic)
    }
    val bilateral = GPUImageBilateralBlurFilter().apply {
        setDistanceNormalizationFactor(8f - r.smoothSkin / 100f * 7f)
    }
    val gaussian = GPUImageGaussianBlurFilter().apply {
        setBlurSize(r.blur / 100f * 4f)
    }
    val sharpen = GPUImageSharpenFilter().apply {
        setSharpness(r.clarity / 100f * 2f)
    }
    val haze = GPUImageHazeFilter().apply {
        setDistance(r.haze / 100f * 0.25f)
        setSlope(0f)
    }
    val vignette = GPUImageVignetteFilter().apply {
        setVignetteCenter(PointF(0.5f, 0.5f))
        setVignetteColor(floatArrayOf(0f, 0f, 0f))
        val value = r.vignette.coerceAtLeast(0f)
        if (value <= 0f) {
            setVignetteStart(2f)
            setVignetteEnd(2f)
        } else {
            setVignetteStart(0.75f - value / 100f * 0.45f)
            setVignetteEnd(1f - value / 100f * 0.25f)
        }
    }
    filters.add(bilateral)
    filters.add(gaussian)
    filters.add(sharpen)
    filters.add(haze)
    filters.add(vignette)
    return GPUImageFilterGroup(filters)
}
