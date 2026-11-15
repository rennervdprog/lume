package app.lume.core

import kotlinx.serialization.Serializable

/**
 * Receita paramétrica versionada — a ÚNICA fonte de verdade da edição.
 *
 * A foto original nunca é alterada. A receita descreve ajustes; o preview
 * e a exportação aplicam a MESMA receita (ver [RecipeColorMatrix]).
 * Presets são receitas nomeadas. O histórico é uma lista de receitas.
 *
 * Intervalos (iguais aos sliders da UI):
 * - exposure: -2..+2 (passos de exposição)
 * - demais: -100..+100
 *
 * Ao adicionar um campo, incremente [CURRENT_VERSION] e escreva a migração
 * em [RecipeJson]. Nunca quebre receitas/presets antigos sem migração.
 */
@Serializable
data class Recipe(
    val version: Int = CURRENT_VERSION,
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val vibrance: Float = 0f,
    val saturation: Float = 0f
) {
    fun isIdentity(): Boolean = this == Recipe(version = version)

    /** Interpola campo a campo entre esta receita e [other]. Usado pela
     * intensidade de preset (0..1) e por qualquer transição de looks. */
    fun interpolatedTo(other: Recipe, fraction: Float): Recipe {
        val f = fraction.coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float): Float = a + (b - a) * f
        return Recipe(
            version = CURRENT_VERSION,
            exposure = lerp(exposure, other.exposure),
            contrast = lerp(contrast, other.contrast),
            highlights = lerp(highlights, other.highlights),
            shadows = lerp(shadows, other.shadows),
            whites = lerp(whites, other.whites),
            blacks = lerp(blacks, other.blacks),
            temperature = lerp(temperature, other.temperature),
            tint = lerp(tint, other.tint),
            vibrance = lerp(vibrance, other.vibrance),
            saturation = lerp(saturation, other.saturation)
        )
    }

    /** Garante intervalos válidos. Receitas vindas de JSON/importação
     * passam sempre por aqui antes de entrar no editor. */
    fun clamped(): Recipe = Recipe(
        version = CURRENT_VERSION,
        exposure = exposure.coerceIn(-2f, 2f),
        contrast = contrast.coerceIn(-100f, 100f),
        highlights = highlights.coerceIn(-100f, 100f),
        shadows = shadows.coerceIn(-100f, 100f),
        whites = whites.coerceIn(-100f, 100f),
        blacks = blacks.coerceIn(-100f, 100f),
        temperature = temperature.coerceIn(-100f, 100f),
        tint = tint.coerceIn(-100f, 100f),
        vibrance = vibrance.coerceIn(-100f, 100f),
        saturation = saturation.coerceIn(-100f, 100f)
    )

    companion object {
        const val CURRENT_VERSION: Int = 1
        val IDENTITY: Recipe = Recipe()
    }
}
