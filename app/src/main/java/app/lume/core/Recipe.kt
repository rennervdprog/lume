package app.lume.core

import kotlinx.serialization.Serializable

/**
 * Receita paramétrica versionada — a ÚNICA fonte de verdade da edição.
 *
 * A foto original nunca é alterada. A receita descreve ajustes; o preview
 * e a exportação aplicam a MESMA receita (ver [app.lume.imaging.GlEngine],
 * com fallback em [RecipeColorMatrix]). Presets são receitas nomeadas.
 * O histórico é uma lista de receitas.
 *
 * Intervalos (iguais aos sliders da UI):
 * - exposure: -2..+2 (passos de exposição)
 * - demais escalares: -100..+100
 * - hsl: 8 canais (ver [HslChannels]), cada um com matiz/saturação/
 *   luminância em -100..+100
 * - curve: pontos de controle em 0..1 (ver [ToneCurve])
 *
 * Versões: v1 tinha só luz/cor global. A v2 (Fase 1) adiciona HSL,
 * curva e efeitos — todos com default = identidade, então JSON v1
 * decodifica direto (ver [RecipeJson]) e [clamped] normaliza a versão.
 * Ao adicionar um campo, incremente [CURRENT_VERSION], dê default
 * identidade e estenda [interpolatedTo] e [clamped]. Nunca quebre
 * receitas/presets antigos sem migração.
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
    val saturation: Float = 0f,
    val hsl: List<HslChannel> = List(HslChannels.COUNT) { HslChannel() },
    val curve: ToneCurve = ToneCurve(),
    val clarity: Float = 0f,
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val fade: Float = 0f
) {
    fun isIdentity(): Boolean = this == Recipe(version = version)

    /** Interpola campo a campo entre esta receita e [other]. Usado pela
     * intensidade de preset (0..1) e por qualquer transição de looks. */
    fun interpolatedTo(other: Recipe, fraction: Float): Recipe {
        val f = fraction.coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float): Float = a + (b - a) * f
        val ownHsl = HslChannels.normalize(hsl)
        val otherHsl = HslChannels.normalize(other.hsl)
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
            saturation = lerp(saturation, other.saturation),
            hsl = ownHsl.zip(otherHsl) { a, b -> a.interpolatedTo(b, f) },
            curve = curve.interpolatedTo(other.curve, f),
            clarity = lerp(clarity, other.clarity),
            vignette = lerp(vignette, other.vignette),
            grain = lerp(grain, other.grain),
            fade = lerp(fade, other.fade)
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
        saturation = saturation.coerceIn(-100f, 100f),
        hsl = HslChannels.normalize(hsl),
        curve = curve.clamped(),
        clarity = clarity.coerceIn(-100f, 100f),
        vignette = vignette.coerceIn(-100f, 100f),
        grain = grain.coerceIn(-100f, 100f),
        fade = fade.coerceIn(-100f, 100f)
    )

    companion object {
        const val CURRENT_VERSION: Int = 2
        val IDENTITY: Recipe = Recipe()
    }
}
