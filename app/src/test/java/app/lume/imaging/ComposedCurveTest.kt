package app.lume.imaging

import app.lume.core.Recipe
import app.lume.core.ToneCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposedCurveTest {

    private fun yAt(points: List<app.lume.core.CurvePoint>, x: Float): Float =
        points.minByOrNull { kotlin.math.abs(it.x - x) }!!.y

    @Test
    fun identidadeDevolveDiagonal() {
        val points = ComposedCurve.points(Recipe())
        assertEquals(17, points.size)
        points.forEachIndexed { index, point ->
            assertEquals(index / 16f, point.x, 0.001f)
            assertEquals(point.x, point.y, 0.01f)
        }
    }

    @Test
    fun saidaOrdenadaEDentroDeZeroUm() {
        val recipe = Recipe(
            highlights = 80f,
            shadows = -70f,
            whites = 60f,
            blacks = -50f,
            fade = 40f,
            curve = ToneCurve(master = ToneCurve.PRESET_CONTRAST)
        )
        val points = ComposedCurve.points(recipe)
        assertEquals(17, points.size)
        points.zipWithNext { a, b ->
            assertTrue("x fora de ordem: ${a.x} >= ${b.x}", a.x < b.x)
        }
        points.forEach { point ->
            assertTrue("x fora de 0..1: ${point.x}", point.x in 0f..1f)
            assertTrue("y fora de 0..1: ${point.y}", point.y in 0f..1f)
        }
    }

    @Test
    fun desbotadoMisturaComCinzaMedio() {
        val points = ComposedCurve.points(Recipe(fade = 100f))
        // fade 100 = mistura de 50%: y = x + (0.5 - x) * 0.5.
        assertEquals(0.25f, yAt(points, 0f), 0.01f)
        assertEquals(0.5f, yAt(points, 0.5f), 0.01f)
        assertEquals(0.75f, yAt(points, 1f), 0.01f)
    }

    @Test
    fun brancosLevantamOTopoEPretoesOChao() {
        val whites = ComposedCurve.points(Recipe(whites = 100f))
        assertEquals(0.9375f, yAt(whites, 0.875f), 0.02f)
        assertEquals(0.5f, yAt(whites, 0.5f), 0.01f)

        val blacksUp = ComposedCurve.points(Recipe(blacks = 100f))
        assertEquals(0.5625f, yAt(blacksUp, 0.125f), 0.02f)

        val blacksDown = ComposedCurve.points(Recipe(blacks = -100f))
        assertEquals(0.0625f, yAt(blacksDown, 0.125f), 0.02f)
    }

    @Test
    fun realcesESombrasAgemNasFaixasCertas() {
        val highlights = ComposedCurve.points(Recipe(highlights = -100f))
        assertEquals(0.4375f, yAt(highlights, 0.875f), 0.02f)
        assertEquals(0.125f, yAt(highlights, 0.125f), 0.01f)

        val shadows = ComposedCurve.points(Recipe(shadows = 100f))
        assertEquals(0.92f, yAt(shadows, 0.125f), 0.02f)
        assertEquals(0.875f, yAt(shadows, 0.875f), 0.01f)
    }

    @Test
    fun curvaContrasteFazS() {
        val points = ComposedCurve.points(
            Recipe(curve = ToneCurve(master = ToneCurve.PRESET_CONTRAST))
        )
        assertTrue("S não escureceu os escuros: ${yAt(points, 0.25f)}", yAt(points, 0.25f) < 0.25f)
        assertEquals(0.5f, yAt(points, 0.5f), 0.01f)
        assertTrue("S não clareou os claros: ${yAt(points, 0.75f)}", yAt(points, 0.75f) > 0.75f)
    }

    @Test
    fun quantidadeDePontosRespeitada() {
        val points = ComposedCurve.points(Recipe(), count = 5)
        assertEquals(5, points.size)
        assertEquals(0f, points.first().x, 0.001f)
        assertEquals(1f, points.last().x, 0.001f)
    }
}
