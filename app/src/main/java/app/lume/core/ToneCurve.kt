package app.lume.core

import kotlinx.serialization.Serializable

/**
 * Um ponto de controle da curva tonal: entrada [x] e saída [y], ambos
 * em 0..1. A curva é a interpolação linear entre os pontos ordenados
 * por x (ver [ToneCurveLut]).
 */
@Serializable
data class CurvePoint(
    val x: Float = 0f,
    val y: Float = 0f
)

/**
 * Curva tonal paramétrica: 4 curvas independentes (master + RGB).
 *
 * A curva master age sobre a luminância (preserva o matiz); as curvas
 * RGB agem por canal e permitem gradação de cor (ex.: sombras frias).
 * A UI desta fase edita só a master (chips de curva); as RGB ficam
 * reservadas para o editor de curvas completo, sem controle falso.
 *
 * Curvas com grades de x diferentes não interpolam ponto a ponto: a
 * intensidade de preset usa a LUT resultante (ver [interpolatedTo]).
 */
@Serializable
data class ToneCurve(
    val master: List<CurvePoint> = IDENTITY_POINTS,
    val red: List<CurvePoint> = IDENTITY_POINTS,
    val green: List<CurvePoint> = IDENTITY_POINTS,
    val blue: List<CurvePoint> = IDENTITY_POINTS
) {
    /** True quando as 4 LUTs resultantes são a rampa identidade. */
    fun isEffectiveIdentity(): Boolean =
        ToneCurveLut.isIdentity(master) &&
            ToneCurveLut.isIdentity(red) &&
            ToneCurveLut.isIdentity(green) &&
            ToneCurveLut.isIdentity(blue)

    /** Ordena por x, prende em 0..1 e garante ao menos 2 pontos. */
    fun clamped(): ToneCurve = ToneCurve(
        master = clampPoints(master),
        red = clampPoints(red),
        green = clampPoints(green),
        blue = clampPoints(blue)
    )

    /**
     * Interpola para [other] (intensidade de preset, 0..1).
     * Curvas com a mesma grade de x interpolam os y ponto a ponto; com
     * grades diferentes, o resultado "pula" para [other] na metade do
     * caminho — a alternativa (interpolar LUTs) perderia os pontos
     * editáveis. Os chips de curva usam a mesma grade, então o caso
     * comum interpola de verdade.
     */
    fun interpolatedTo(other: ToneCurve, fraction: Float): ToneCurve {
        val f = fraction.coerceIn(0f, 1f)
        return ToneCurve(
            master = interpolatePoints(master, other.master, f),
            red = interpolatePoints(red, other.red, f),
            green = interpolatePoints(green, other.green, f),
            blue = interpolatePoints(blue, other.blue, f)
        )
    }

    private fun interpolatePoints(
        from: List<CurvePoint>,
        to: List<CurvePoint>,
        fraction: Float
    ): List<CurvePoint> {
        val sameGrid = from.size == to.size &&
            from.zip(to).all { (a, b) -> a.x == b.x }
        if (!sameGrid) {
            return if (fraction >= 0.5f) to else from
        }
        return from.zip(to).map { (a, b) ->
            CurvePoint(a.x, a.y + (b.y - a.y) * fraction)
        }
    }

    private fun clampPoints(points: List<CurvePoint>): List<CurvePoint> {
        val cleaned = points
            .map { CurvePoint(it.x.coerceIn(0f, 1f), it.y.coerceIn(0f, 1f)) }
            .sortedBy { it.x }
        if (cleaned.size < 2) return IDENTITY_POINTS
        return cleaned
    }

    companion object {
        val IDENTITY_POINTS: List<CurvePoint> = listOf(
            CurvePoint(0f, 0f),
            CurvePoint(1f, 1f)
        )

        /** Grade de x compartilhada pelos chips de curva (5 pontos). */
        private val GRID_X: List<Float> = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)

        private fun grid(ys: List<Float>): List<CurvePoint> =
            GRID_X.zip(ys).map { (x, y) -> CurvePoint(x, y) }

        /** Curvas master prontas (chips do painel Luz, modo Pro). */
        val PRESET_LINEAR: List<CurvePoint> = grid(listOf(0f, 0.25f, 0.5f, 0.75f, 1f))
        val PRESET_CONTRAST: List<CurvePoint> = grid(listOf(0f, 0.18f, 0.5f, 0.82f, 1f))
        val PRESET_FADE: List<CurvePoint> = grid(listOf(0.12f, 0.32f, 0.55f, 0.78f, 0.92f))
        val PRESET_GENTLE: List<CurvePoint> = grid(listOf(0.08f, 0.30f, 0.52f, 0.76f, 1f))

        /** Nome do chip cuja grade é [points], ou null se personalizada. */
        fun presetNameFor(points: List<CurvePoint>): String? = when (points) {
            PRESET_LINEAR -> "Linear"
            PRESET_CONTRAST -> "Contraste"
            PRESET_FADE -> "Fade"
            PRESET_GENTLE -> "Suave"
            IDENTITY_POINTS -> "Linear"
            else -> null
        }
    }
}
