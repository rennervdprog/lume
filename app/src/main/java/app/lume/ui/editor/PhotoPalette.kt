package app.lume.ui.editor

import android.graphics.Bitmap
import androidx.palette.graphics.Palette

/**
 * Cores da foto (Fase 1): a paleta da foto atual, extraída no
 * aparelho com o AndroidX Palette — sem rede, sem custo, offline.
 * A extração roda fora da thread de UI (o ViewModel chama em
 * Dispatchers.Default); a lista vai para o estado e as bolinhas
 * do Texto usam as cores. A faixa no Ajustar saiu na 0.15.6.
 */
object PhotoPalette {

    /** Seis amostras, como o preview aprovado. */
    const val SWATCH_COUNT: Int = 6

    /**
     * Extrai até [SWATCH_COUNT] cores do [bitmap]. Primeiro os 6
     * perfis do Palette (vibrante/claro/escuro, suave/claro/escuro);
     * o que faltar, completa com as amostras mais populosas.
     * Chamar fora da thread de UI.
     */
    fun extract(bitmap: Bitmap): List<Int> {
        val palette = Palette.from(bitmap).maximumColorCount(16).generate()
        val profiles = listOfNotNull(
            palette.vibrantSwatch,
            palette.lightVibrantSwatch,
            palette.darkVibrantSwatch,
            palette.mutedSwatch,
            palette.lightMutedSwatch,
            palette.darkMutedSwatch
        ).map { it.rgb }
        val byPopulation = palette.swatches
            .sortedByDescending { it.population }
            .map { it.rgb }
        return selectSwatchColors(profiles, byPopulation)
    }

    /**
     * Regra pura de escolha das amostras (testável sem Bitmap):
     * os perfis na ordem, completando com as mais populosas, sem
     * repetir cor, até [count].
     */
    fun selectSwatchColors(
        profiles: List<Int>,
        byPopulation: List<Int>,
        count: Int = SWATCH_COUNT
    ): List<Int> {
        return (profiles + byPopulation).distinct().take(count)
    }
}

/** "#rrggbb" em minúsculas, como o preview (sem o alfa). */
internal fun hexString(argb: Int): String {
    return "#%06x".format(argb and 0xFFFFFF)
}
