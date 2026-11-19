package app.lume.imaging

import jp.co.cyberagent.android.gpuimage.filter.GPUImageCrosshatchFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageEmbossFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageHalftoneFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageKuwaharaFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImagePixelationFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImagePosterizeFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSepiaToneFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSketchFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageSolarizeFilter
import jp.co.cyberagent.android.gpuimage.filter.GPUImageToonFilter
import kotlin.math.roundToInt

/**
 * Efeitos artísticos (0.14.0): os 10 filtros de estilo da GPUImage
 * 2.1.0 que fazem sentido numa foto — sem Sobel (só linhas, pouco
 * útil) e sem os de distorção (lente, redemoinho). Todos são Pro.
 *
 * O id é o que a [app.lume.core.Recipe] guarda (o campo
 * `artisticEffect`); a intensidade é 0..1 na receita e 0..100 no slider. [hasIntensity]
 * falso = efeito liga/desliga, sem slider (o Esboço não tem parâmetro
 * nenhum na biblioteca).
 */
enum class ArtisticEffect(
    val id: String,
    val label: String,
    val hasIntensity: Boolean
) {
    TOON("toon", "Toon", true),
    SKETCH("sketch", "Esboço", false),
    KUWAHARA("kuwahara", "Aquarela", true),
    HALFTONE("halftone", "Meio-tom", true),
    POSTERIZE("posterize", "Pôster", true),
    SEPIA("sepia", "Sépia", true),
    PIXELATION("pixelation", "Pixelado", true),
    EMBOSS("emboss", "Relevo", true),
    SOLARIZE("solarize", "Solarizar", true),
    CROSSHATCH("crosshatch", "Trama", true);

    companion object {
        fun fromId(id: String?): ArtisticEffect? =
            entries.firstOrNull { it.id == id }
    }
}

/**
 * Cria a instância do filtro da biblioteca para o efeito. Uma
 * instância por efeito, guardada pelo pipeline: os setters guardam
 * os valores nos campos do filtro e o `onInitialized` reaplica —
 * trocar de efeito não perde o parâmetro.
 */
internal fun createArtisticFilter(effect: ArtisticEffect): GPUImageFilter =
    when (effect) {
        ArtisticEffect.TOON -> GPUImageToonFilter()
        ArtisticEffect.SKETCH -> GPUImageSketchFilter()
        ArtisticEffect.KUWAHARA -> GPUImageKuwaharaFilter()
        ArtisticEffect.HALFTONE -> GPUImageHalftoneFilter()
        ArtisticEffect.POSTERIZE -> GPUImagePosterizeFilter()
        ArtisticEffect.SEPIA -> GPUImageSepiaToneFilter()
        ArtisticEffect.PIXELATION -> GPUImagePixelationFilter()
        ArtisticEffect.EMBOSS -> GPUImageEmbossFilter()
        ArtisticEffect.SOLARIZE -> GPUImageSolarizeFilter()
        ArtisticEffect.CROSSHATCH -> GPUImageCrosshatchFilter()
    }

/**
 * Aplica a intensidade 0..1 ao filtro. As faixas foram lidas no
 * fonte/bytecode da GPUImage 2.1.0: intensidade 0 = efeito no mais
 * suave, 1 = no mais forte. [photoWidth]/[photoHeight] só valem
 * para o Meio-tom (proporção da foto).
 */
internal fun applyArtisticIntensity(
    filter: GPUImageFilter,
    effect: ArtisticEffect,
    intensity: Float,
    photoWidth: Int,
    photoHeight: Int
) {
    val v = intensity.coerceIn(0f, 1f)
    when (effect) {
        ArtisticEffect.TOON -> {
            val toon = filter as GPUImageToonFilter
            toon.setThreshold(0.2f)
            // Níveis de quantização 10..2: menos níveis = mais plano.
            toon.setQuantizationLevels(10f - v * 8f)
        }
        ArtisticEffect.SKETCH -> {
            // Sem parâmetro: liga/desliga.
        }
        ArtisticEffect.KUWAHARA -> {
            // Raio 1..6: maior raio = manchas maiores de aquarela.
            (filter as GPUImageKuwaharaFilter).setRadius(1 + (v * 5f).roundToInt())
        }
        ArtisticEffect.HALFTONE -> {
            val halftone = filter as GPUImageHalftoneFilter
            // Largura fracionária do ponto 0.01..0.05: ponto maior.
            halftone.setFractionalWidthOfAPixel(0.01f + v * 0.04f)
            if (photoWidth > 0 && photoHeight > 0) {
                halftone.setAspectRatio(photoWidth.toFloat() / photoHeight.toFloat())
            }
        }
        ArtisticEffect.POSTERIZE -> {
            // Níveis de cor 10..2: menos níveis = mais pôster.
            (filter as GPUImagePosterizeFilter).setColorLevels(10 - (v * 8f).roundToInt())
        }
        ArtisticEffect.SEPIA -> {
            // O filtro herda setIntensity do ColorMatrixFilter:
            // 0 = original, 1 = sépia total.
            (filter as GPUImageSepiaToneFilter).setIntensity(v)
        }
        ArtisticEffect.PIXELATION -> {
            // Tamanho do pixel 1..12.
            (filter as GPUImagePixelationFilter).setPixel(1f + v * 11f)
        }
        ArtisticEffect.EMBOSS -> {
            // Intensidade 0..2.
            (filter as GPUImageEmbossFilter).setIntensity(v * 2f)
        }
        ArtisticEffect.SOLARIZE -> {
            // Limiar 0.9..0.1: mais baixo = mais área invertida.
            (filter as GPUImageSolarizeFilter).setThreshold(0.9f - v * 0.8f)
        }
        ArtisticEffect.CROSSHATCH -> {
            val crosshatch = filter as GPUImageCrosshatchFilter
            // Espaçamento 0.06..0.01: mais denso com a intensidade.
            crosshatch.setCrossHatchSpacing(0.06f - v * 0.05f)
            crosshatch.setLineWidth(0.003f)
        }
    }
}
