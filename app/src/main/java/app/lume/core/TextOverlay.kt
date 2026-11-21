package app.lume.core

import app.lume.R

/**
 * Fontes do Texto na foto (Fase 1): as 5 do Google Fonts, todas
 * SIL Open Font License 1.1, embutidas em res/font — funcionam
 * offline, sem baixar nada. O Manrope é a 5ª fonte: o Solarize
 * não tinha nenhuma fonte embutida antes desta versão (o Manrope
 * via Compose resources era do Constância, não daqui), então ele
 * veio junto com as outras 4 do repositório google/fonts.
 */
enum class TextFont(val displayName: String, val fontRes: Int) {
    BEBAS_NEUE("Bebas Neue", R.font.bebas_neue),
    PLAYFAIR_DISPLAY("Playfair Display", R.font.playfair_display),
    DANCING_SCRIPT("Dancing Script", R.font.dancing_script),
    ROBOTO_CONDENSED("Roboto Condensed", R.font.roboto_condensed),
    MANROPE("Manrope", R.font.manrope)
}

/**
 * Texto sobre a foto (Fase 1). Vive só na sessão do Editor
 * (rememberSaveable): não entra na receita nem no rascunho.
 *
 * A posição é o CENTRO do texto em frações da foto final
 * (geometria aplicada): 0..1 nos dois eixos. Fração, e não
 * pixel, para a tela e a exportação baterem — a tela desenha
 * sobre o preview e a exportação sobre o bitmap final, que têm
 * tamanhos diferentes. O tamanho também é fração: da largura
 * da foto final.
 */
data class TextOverlay(
    val text: String = "",
    val font: TextFont = TextFont.DANCING_SCRIPT,
    val sizeFraction: Float = DEFAULT_SIZE_FRACTION,
    /** Cor em ARGB (android.graphics.Color). Branco por padrão. */
    val colorArgb: Int = DEFAULT_COLOR_ARGB,
    val centerX: Float = DEFAULT_CENTER_X,
    val centerY: Float = DEFAULT_CENTER_Y
) {
    val isEmpty: Boolean get() = text.isBlank()

    companion object {
        /** Tamanho padrão: 8% da largura da foto. */
        const val DEFAULT_SIZE_FRACTION: Float = 0.08f
        const val MIN_SIZE_FRACTION: Float = 0.03f
        const val MAX_SIZE_FRACTION: Float = 0.20f
        /** Branco opaco. */
        const val DEFAULT_COLOR_ARGB: Int = -1
        /** Centro horizontal; 35% da altura (como o preview). */
        const val DEFAULT_CENTER_X: Float = 0.5f
        const val DEFAULT_CENTER_Y: Float = 0.35f
        /** O texto nunca passa de 92% da largura da foto. */
        const val MAX_WIDTH_FRACTION: Float = 0.92f
    }
}

/**
 * Escala do tamanho para o texto caber em [maxWidthPx]: 1 quando
 * cabe, menor quando não. A mesma regra na tela (TextMeasurer) e
 * na exportação (Paint.measureText), para as duas baterem.
 */
fun fitScale(textWidthPx: Float, maxWidthPx: Float): Float {
    if (textWidthPx <= 0f || maxWidthPx <= 0f) return 1f
    return (maxWidthPx / textWidthPx).coerceIn(0f, 1f)
}

/**
 * Deslocamento da baseline para o texto ficar centrado na vertical
 * em torno de [centerYPx]: o drawText recebe a baseline, não o
 * centro — sem isso o texto sai meio tamanho para baixo.
 */
fun centeredBaselineOffset(fontMetricsAscent: Float, fontMetricsDescent: Float): Float {
    return -(fontMetricsAscent + fontMetricsDescent) / 2f
}
