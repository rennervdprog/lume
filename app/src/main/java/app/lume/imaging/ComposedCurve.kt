package app.lume.imaging

import app.lume.core.CurvePoint
import app.lume.core.Recipe
import app.lume.core.ToneCurveLut
import kotlin.math.roundToInt

/**
 * A curva tonal final que vai para o filtro de curva da GPUImage:
 * a curva master da receita COM as faixas tonais (realces, sombras,
 * brancos, pretos) e o desbotado dobrados para dentro dela.
 *
 * Por quê dobrar: o filtro de realces/sombras da GPUImage só cobre
 * metade de cada slider (só clareia sombras, só recupera realces) —
 * dobrar as 4 faixas na curva dá o controle bidirecional completo
 * com a MESMA matemática do motor anterior, aplicada às 256 entradas
 * da LUT em vez de aos pixels. O resultado é puro Kotlin, testável
 * na JVM (ver ComposedCurveTest), e o filtro de curva só aplica.
 *
 * A ordem do pipeline (contrato): exposição → balanço de branco →
 * contraste → saturação → vibração → HSL → ESTA curva → claridade →
 * vinheta. As curvas RGB ficam identidade (reservadas, sem controle
 * falso). A grade de saída tem [DEFAULT_POINT_COUNT] pontos: densa o
 * bastante para o spline do filtro seguir a curva composta sem
 * desviar, e sem x duplicado após a conversão para 0..255 do filtro.
 */
internal object ComposedCurve {

    const val DEFAULT_POINT_COUNT: Int = 17

    /** Pontos de controle (x, y em 0..1) da curva composta. */
    fun points(recipe: Recipe, count: Int = DEFAULT_POINT_COUNT): List<CurvePoint> {
        val lut = ToneCurveLut.build(recipe.curve.master)
        val composed = FloatArray(ToneCurveLut.SIZE) { i ->
            val x = i / (ToneCurveLut.SIZE - 1f)
            var y = lut[i]
            y = applyBand(y, smoothstep(0.55f, 0.95f, x), recipe.highlights)
            y = applyBand(y, 1f - smoothstep(0.05f, 0.45f, x), recipe.shadows)
            y = applyBand(y, smoothstep(0.75f, 1f, x), recipe.whites)
            y = applyBand(y, 1f - smoothstep(0f, 0.25f, x), recipe.blacks)
            // Desbotado: mistura a saída com o cinza médio (mesma
            // fórmula do motor anterior, aplicada à LUT).
            val fade = recipe.fade / 100f * 0.5f
            y += (0.5f - y) * fade
            y.coerceIn(0f, 1f)
        }
        val n = count.coerceAtLeast(2)
        return List(n) { k ->
            val x = k / (n - 1f)
            val index = (x * (ToneCurveLut.SIZE - 1)).roundToInt()
            CurvePoint(x, composed[index])
        }
    }

    /**
     * Ajuste de faixa: valor positivo levanta em direção ao branco,
     * negativo empurra em direção ao preto, ponderado por [weight].
     * Idêntico ao applyBand do motor anterior.
     */
    private fun applyBand(y: Float, weight: Float, amount100: Float): Float {
        val a = amount100 / 100f
        return if (a >= 0f) {
            y + (1f - y) * a * weight
        } else {
            y * (1f + a * weight)
        }
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
