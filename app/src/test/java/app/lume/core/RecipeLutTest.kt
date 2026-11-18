package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeLutTest {

    @Test
    fun padraoSemLutEVersao3() {
        val recipe = Recipe()
        assertNull(recipe.lutId)
        assertEquals(0f, recipe.lutIntensity, 0f)
        assertEquals(3, Recipe.CURRENT_VERSION)
        assertEquals(Recipe.CURRENT_VERSION, recipe.version)
        assertTrue(recipe.isIdentity())
    }

    @Test
    fun receitaComLutNaoEIdentidade() {
        assertFalse(Recipe(lutId = "lut-teal-orange", lutIntensity = 1f).isIdentity())
    }

    @Test
    fun clampedAparaIntensidadeELimpaIdVazio() {
        val clamped = Recipe(lutId = "  ", lutIntensity = 1.5f).clamped()
        assertNull(clamped.lutId)
        assertEquals(1f, clamped.lutIntensity, 0f)
        assertEquals(0f, Recipe(lutIntensity = -0.5f).clamped().lutIntensity, 0f)
    }

    @Test
    fun interpolacaoEmZeroMantemOLutAtual() {
        val own = Recipe(lutId = "lut-golden", lutIntensity = 0.8f)
        val other = Recipe(lutId = "lut-bw-contrast", lutIntensity = 0.4f)
        val blended = own.interpolatedTo(other, 0f)
        assertEquals("lut-golden", blended.lutId)
        assertEquals(0.8f, blended.lutIntensity, 0f)
    }

    @Test
    fun interpolacaoTomaOLutDoDestinoComIntensidadeInterpolada() {
        val own = Recipe(lutId = null, lutIntensity = 0f)
        val other = Recipe(lutId = "lut-vintage", lutIntensity = 0.8f)
        val half = own.interpolatedTo(other, 0.5f)
        assertEquals("lut-vintage", half.lutId)
        assertEquals(0.4f, half.lutIntensity, 0.001f)
        val full = own.interpolatedTo(other, 1f)
        assertEquals("lut-vintage", full.lutId)
        assertEquals(0.8f, full.lutIntensity, 0f)
    }

    @Test
    fun jsonIdaEVoltaPreservaOLut() {
        val recipe = Recipe(lutId = "lut-teal-orange", lutIntensity = 0.75f)
        val decoded = RecipeJson.decodeRecipe(RecipeJson.encodeRecipe(recipe))
        assertEquals("lut-teal-orange", decoded?.lutId)
        assertEquals(0.75f, decoded?.lutIntensity ?: -1f, 0f)
    }

    @Test
    fun jsonAntigoSemLutDecodificaComPadrao() {
        // Receita v2, sem os campos de LUT: decodifica com os defaults.
        val oldJson = """{"version":2,"exposure":0.5,"contrast":10.0}"""
        val decoded = RecipeJson.decodeRecipe(oldJson)
        assertNull(decoded?.lutId)
        assertEquals(0f, decoded?.lutIntensity ?: -1f, 0f)
        assertEquals(0.5f, decoded?.exposure ?: -9f, 0f)
    }
}
