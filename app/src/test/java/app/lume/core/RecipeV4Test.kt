package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeV4Test {

    @Test
    fun padraoV4EIdentidade() {
        val recipe = Recipe()
        assertEquals(4, Recipe.CURRENT_VERSION)
        assertEquals(0f, recipe.levelsBlack, 0f)
        assertEquals(0f, recipe.levelsMid, 0f)
        assertEquals(0f, recipe.levelsWhite, 0f)
        assertEquals(0f, recipe.cbShadows, 0f)
        assertEquals(0f, recipe.cbMidtones, 0f)
        assertEquals(0f, recipe.cbHighlights, 0f)
        assertEquals(0f, recipe.haze, 0f)
        assertEquals(0f, recipe.gamma, 0f)
        assertEquals(0f, recipe.hueShift, 0f)
        assertEquals(0f, recipe.rgbR, 0f)
        assertEquals(0f, recipe.rgbG, 0f)
        assertEquals(0f, recipe.rgbB, 0f)
        assertEquals(0f, recipe.blur, 0f)
        assertEquals(0f, recipe.smoothSkin, 0f)
        assertNull(recipe.artisticEffect)
        assertEquals(1f, recipe.artisticIntensity, 0f)
        assertNull(recipe.blendMode)
        assertEquals(1f, recipe.blendOpacity, 0f)
        assertTrue(recipe.isIdentity())
    }

    @Test
    fun clampedAparaOsNovosCampos() {
        val clamped = Recipe(
            levelsBlack = 150f,
            levelsMid = -200f,
            levelsWhite = 150f,
            cbShadows = 200f,
            haze = -150f,
            gamma = 150f,
            hueShift = -150f,
            rgbR = 150f,
            blur = -10f,
            smoothSkin = 150f,
            artisticEffect = "  ",
            artisticIntensity = 2f,
            blendMode = "  ",
            blendOpacity = -1f
        ).clamped()
        assertEquals(100f, clamped.levelsBlack, 0f)
        assertEquals(-100f, clamped.levelsMid, 0f)
        assertEquals(100f, clamped.levelsWhite, 0f)
        assertEquals(100f, clamped.cbShadows, 0f)
        assertEquals(-100f, clamped.haze, 0f)
        assertEquals(100f, clamped.gamma, 0f)
        assertEquals(-100f, clamped.hueShift, 0f)
        assertEquals(100f, clamped.rgbR, 0f)
        assertEquals(0f, clamped.blur, 0f)
        assertEquals(100f, clamped.smoothSkin, 0f)
        assertNull(clamped.artisticEffect)
        assertEquals(1f, clamped.artisticIntensity, 0f)
        assertNull(clamped.blendMode)
        assertEquals(0f, clamped.blendOpacity, 0f)
    }

    @Test
    fun interpolacaoInterpolaEscalaresEDiscretos() {
        val own = Recipe(levelsBlack = 0f, haze = 0f, artisticEffect = null, blendMode = null)
        val other = Recipe(
            levelsBlack = 100f,
            haze = -100f,
            artisticEffect = "sepia",
            artisticIntensity = 0.6f,
            blendMode = "multiply",
            blendOpacity = 0.8f
        )
        val half = own.interpolatedTo(other, 0.5f)
        assertEquals(50f, half.levelsBlack, 0.001f)
        assertEquals(-50f, half.haze, 0.001f)
        assertEquals("sepia", half.artisticEffect)
        assertEquals(0.8f, half.artisticIntensity, 0.001f)
        assertEquals("multiply", half.blendMode)
        assertEquals(0.9f, half.blendOpacity, 0.001f)
        // Em fração 0, o efeito e a mistura atuais ficam.
        val zero = other.interpolatedTo(own, 0f)
        assertEquals("sepia", zero.artisticEffect)
        assertEquals("multiply", zero.blendMode)
    }

    @Test
    fun jsonIdaEVoltaPreservaV4() {
        val recipe = Recipe(
            levelsBlack = 20f,
            levelsMid = -10f,
            cbHighlights = 30f,
            haze = 40f,
            gamma = -20f,
            hueShift = 50f,
            rgbR = 10f,
            blur = 25f,
            smoothSkin = 60f,
            artisticEffect = "toon",
            artisticIntensity = 0.7f,
            blendMode = "screen",
            blendOpacity = 0.5f
        )
        val decoded = RecipeJson.decodeRecipe(RecipeJson.encodeRecipe(recipe))
        assertEquals(20f, decoded?.levelsBlack ?: -1f, 0f)
        assertEquals(-10f, decoded?.levelsMid ?: -1f, 0f)
        assertEquals(30f, decoded?.cbHighlights ?: -1f, 0f)
        assertEquals(40f, decoded?.haze ?: -1f, 0f)
        assertEquals(-20f, decoded?.gamma ?: -1f, 0f)
        assertEquals(50f, decoded?.hueShift ?: -1f, 0f)
        assertEquals(10f, decoded?.rgbR ?: -1f, 0f)
        assertEquals(25f, decoded?.blur ?: -1f, 0f)
        assertEquals(60f, decoded?.smoothSkin ?: -1f, 0f)
        assertEquals("toon", decoded?.artisticEffect)
        assertEquals(0.7f, decoded?.artisticIntensity ?: -1f, 0f)
        assertEquals("screen", decoded?.blendMode)
        assertEquals(0.5f, decoded?.blendOpacity ?: -1f, 0f)
    }

    @Test
    fun jsonAntigoV3DecodificaComPadraoV4() {
        // Receita v3, sem os campos v4: decodifica com os defaults.
        val oldJson = """{"version":3,"exposure":0.5,"lutId":"lut-golden","lutIntensity":0.8}"""
        val decoded = RecipeJson.decodeRecipe(oldJson)
        assertEquals(0f, decoded?.levelsBlack ?: -1f, 0f)
        assertEquals(0f, decoded?.haze ?: -1f, 0f)
        assertNull(decoded?.artisticEffect)
        assertNull(decoded?.blendMode)
        assertEquals("lut-golden", decoded?.lutId)
        assertEquals(0.5f, decoded?.exposure ?: -9f, 0f)
    }

    @Test
    fun receitaComCampoV4NaoEIdentidade() {
        assertFalse(Recipe(haze = 10f).isIdentity())
        assertFalse(Recipe(artisticEffect = "sketch").isIdentity())
        assertFalse(Recipe(blendMode = "overlay").isIdentity())
        assertFalse(Recipe(rgbB = -5f).isIdentity())
    }
}
