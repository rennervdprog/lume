package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeEngineTest {

    @Test
    fun v1Json_decodesWithV2Defaults() {
        // JSON de receita v1 (só luz/cor): sem hsl/curve/efeitos.
        val v1 = """{"version":1,"exposure":0.5,"contrast":20.0,"saturation":-10.0}"""
        val decoded = RecipeJson.decodeRecipe(v1)
        assertNotNull(decoded)
        val recipe = decoded!!
        assertEquals(Recipe.CURRENT_VERSION, recipe.version)
        assertEquals(0.5f, recipe.exposure, 0.001f)
        assertEquals(20f, recipe.contrast, 0.001f)
        assertEquals(-10f, recipe.saturation, 0.001f)
        assertEquals(HslChannels.COUNT, recipe.hsl.size)
        assertTrue(recipe.hsl.all { it.isIdentity() })
        assertTrue(recipe.curve.isEffectiveIdentity())
        assertEquals(0f, recipe.clarity, 0.001f)
        assertEquals(0f, recipe.vignette, 0.001f)
        assertEquals(0f, recipe.grain, 0.001f)
        assertEquals(0f, recipe.fade, 0.001f)
    }

    @Test
    fun v2Json_roundTrips() {
        val original = Recipe(
            exposure = 0.3f,
            hsl = listOf(
                HslChannel(hue = 20f, saturation = -15f, luminance = 10f),
                HslChannel(),
                HslChannel(),
                HslChannel(),
                HslChannel(),
                HslChannel(),
                HslChannel(),
                HslChannel(luminance = -30f)
            ),
            curve = ToneCurve(master = ToneCurve.PRESET_CONTRAST),
            clarity = 40f,
            vignette = -20f,
            grain = 15f,
            fade = 10f
        ).clamped()
        val decoded = RecipeJson.decodeRecipe(RecipeJson.encodeRecipe(original))
        assertEquals(original, decoded)
    }

    @Test
    fun clamped_normalizesHslSize() {
        val short = Recipe(hsl = listOf(HslChannel(hue = 50f))).clamped()
        assertEquals(HslChannels.COUNT, short.hsl.size)
        assertEquals(50f, short.hsl[0].hue, 0.001f)
        assertTrue(short.hsl.drop(1).all { it.isIdentity() })

        val long = Recipe(
            hsl = List(10) { HslChannel(hue = 10f) }
        ).clamped()
        assertEquals(HslChannels.COUNT, long.hsl.size)
    }

    @Test
    fun clamped_clampsEffectsAndHsl() {
        val recipe = Recipe(
            clarity = 500f,
            vignette = -500f,
            hsl = listOf(HslChannel(hue = 200f, saturation = -200f))
        ).clamped()
        assertEquals(100f, recipe.clarity, 0.001f)
        assertEquals(-100f, recipe.vignette, 0.001f)
        assertEquals(100f, recipe.hsl[0].hue, 0.001f)
        assertEquals(-100f, recipe.hsl[0].saturation, 0.001f)
    }

    @Test
    fun interpolatedTo_blendsHslEffectsAndCurve() {
        // Mesma grade de x (5 pontos) nos dois lados: a curva interpola.
        val start = Recipe(curve = ToneCurve(master = ToneCurve.PRESET_LINEAR))
        val end = Recipe(
            hsl = listOf(
                HslChannel(hue = 40f),
                HslChannel(),
                HslChannel(),
                HslChannel(),
                HslChannel(),
                HslChannel(),
                HslChannel(),
                HslChannel()
            ),
            curve = ToneCurve(master = ToneCurve.PRESET_CONTRAST),
            clarity = 80f
        )
        val mid = start.interpolatedTo(end, 0.5f)
        assertEquals(20f, mid.hsl[0].hue, 0.001f)
        assertEquals(40f, mid.clarity, 0.001f)
        // Ponto x=0.25: (0.25 + 0.18) / 2 = 0.215.
        assertEquals(0.215f, mid.curve.master[1].y, 0.001f)
        assertEquals(0.25f, mid.curve.master[1].x, 0.001f)
    }

    @Test
    fun interpolatedTo_snapsCurveOnDifferentGrids() {
        val start = Recipe(curve = ToneCurve(master = ToneCurve.IDENTITY_POINTS))
        val end = Recipe(curve = ToneCurve(master = ToneCurve.PRESET_CONTRAST))
        val before = start.interpolatedTo(end, 0.4f)
        assertEquals(ToneCurve.IDENTITY_POINTS, before.curve.master)
        val after = start.interpolatedTo(end, 0.5f)
        assertEquals(ToneCurve.PRESET_CONTRAST, after.curve.master)
    }

    @Test
    fun identity_hasNoV2Adjustments() {
        assertTrue(Recipe.IDENTITY.isIdentity())
        assertFalse(Recipe(clarity = 1f).isIdentity())
        assertFalse(
            Recipe(curve = ToneCurve(master = ToneCurve.PRESET_FADE)).isIdentity()
        )
        assertFalse(
            Recipe(
                hsl = listOf(
                    HslChannel(hue = 1f),
                    HslChannel(),
                    HslChannel(),
                    HslChannel(),
                    HslChannel(),
                    HslChannel(),
                    HslChannel(),
                    HslChannel()
                )
            ).isIdentity()
        )
    }
}
