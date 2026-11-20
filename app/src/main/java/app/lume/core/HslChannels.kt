package app.lume.core

import kotlinx.serialization.Serializable

/**
 * Um canal HSL: ajuste de [hue] (matiz), [saturation] e [luminance]
 * aplicado só aos pixels próximos da cor do canal. Intervalos: os três
 * em -100..+100, iguais aos sliders da UI.
 */
@Serializable
data class HslChannel(
    val hue: Float = 0f,
    val saturation: Float = 0f,
    val luminance: Float = 0f
) {
    fun isIdentity(): Boolean = hue == 0f && saturation == 0f && luminance == 0f

    fun clamped(): HslChannel = HslChannel(
        hue = hue.coerceIn(-100f, 100f),
        saturation = saturation.coerceIn(-100f, 100f),
        luminance = luminance.coerceIn(-100f, 100f)
    )

    /** Interpola para [other]. Parcial (0.15.2): só move o componente
     * que o destino define (diferente de 0) — igual à regra do
     * [Recipe.interpolatedTo] para os escalares. */
    fun interpolatedTo(other: HslChannel, fraction: Float): HslChannel {
        val f = fraction.coerceIn(0f, 1f)
        fun lerp(a: Float, b: Float): Float = a + (b - a) * f
        return HslChannel(
            hue = if (other.hue == 0f) hue else lerp(hue, other.hue),
            saturation = if (other.saturation == 0f) saturation else lerp(saturation, other.saturation),
            luminance = if (other.luminance == 0f) luminance else lerp(luminance, other.luminance)
        )
    }
}

/**
 * Os 8 canais HSL, em ordem fixa — a ordem é contrato: o índice na
 * lista [Recipe.hsl] é o índice nos arrays de uniforms do shader e na
 * fileira de pontos da UI.
 *
 * Centros em graus no círculo de matiz (0=vermelho, crescendo no
 * sentido do espectro). Pele vive entre laranja e amarelo — o seletor
 * "toque na foto" (Pro, Fase 1) usa estes centros para escolher o canal.
 */
object HslChannels {

    const val COUNT: Int = 8

    /** Nomes em PT-BR, na ordem dos índices. */
    val NAMES: List<String> = listOf(
        "Vermelho", "Laranja", "Amarelo", "Verde",
        "Ciano", "Azul", "Roxo", "Magenta"
    )

    /** Centro de cada canal, em graus (0..360). */
    val CENTERS_DEGREES: FloatArray = floatArrayOf(
        0f, 30f, 60f, 120f, 180f, 210f, 270f, 300f
    )

    /**
     * Deslocamento máximo de matiz, em graus, quando o slider está em
     * ±100. ±30° move a cor sem virar outra cor — o ajuste fica
     * previsível e não cria artefatos em pele.
     */
    const val MAX_HUE_SHIFT_DEGREES: Float = 30f

    /** Normaliza qualquer lista para exatamente 8 canais. */
    fun normalize(channels: List<HslChannel>): List<HslChannel> {
        if (channels.size == COUNT) return channels.map { it.clamped() }
        return List(COUNT) { index ->
            channels.getOrNull(index)?.clamped() ?: HslChannel()
        }
    }
}
