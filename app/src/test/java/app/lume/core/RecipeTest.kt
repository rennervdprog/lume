package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeTest {

    @Test
    fun identityRecipe_isIdentity() {
        assertTrue(Recipe.IDENTITY.isIdentity())
        assertFalse(Recipe(exposure = 0.1f).isIdentity())
    }

    @Test
    fun interpolation_atZero_returnsStart() {
        val start = Recipe(exposure = 0.5f, contrast = 20f)
        val end = Recipe(exposure = -1f, contrast = -40f)
        assertEquals(start, start.interpolatedTo(end, 0f))
    }

    @Test
    fun interpolation_atOne_returnsEnd() {
        val start = Recipe(exposure = 0.5f, contrast = 20f)
        val end = Recipe(exposure = -1f, contrast = -40f)
        assertEquals(end, start.interpolatedTo(end, 1f))
    }

    @Test
    fun interpolation_atHalf_averagesFields() {
        val start = Recipe(exposure = 0f, contrast = 0f)
        val end = Recipe(exposure = 1f, contrast = 100f)
        val mid = start.interpolatedTo(end, 0.5f)
        assertEquals(0.5f, mid.exposure, 0.001f)
        assertEquals(50f, mid.contrast, 0.001f)
    }

    @Test
    fun clamped_keepsValuesInsideRanges() {
        val clamped = Recipe(
            exposure = 9f,
            contrast = -500f,
            saturation = 250f
        ).clamped()
        assertEquals(2f, clamped.exposure, 0.001f)
        assertEquals(-100f, clamped.contrast, 0.001f)
        assertEquals(100f, clamped.saturation, 0.001f)
    }

    @Test
    fun json_roundTrip_preservesRecipe() {
        val recipe = Recipe(
            exposure = 0.3f,
            temperature = -12f,
            saturation = 40f
        )
        val decoded = RecipeJson.decodeRecipe(RecipeJson.encodeRecipe(recipe))
        assertEquals(recipe, decoded)
    }

    @Test
    fun json_invalid_returnsNull() {
        assertEquals(null, RecipeJson.decodeRecipe("não é json"))
        assertEquals(null, RecipeJson.decodeRecipe(""))
        assertEquals(null, RecipeJson.decodeRecipe(null))
    }

    @Test
    fun builtInPresets_haveUniqueIdsAndNames() {
        val ids = BuiltInPresets.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        val names = BuiltInPresets.all.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(BuiltInPresets.all.isNotEmpty())
    }
}
