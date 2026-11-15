package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeColorMatrixTest {

    private val delta = 0.001f

    @Test
    fun identityRecipe_producesIdentityMatrix() {
        val values = RecipeColorMatrix.values(Recipe.IDENTITY)
        assertEquals(20, values.size)
        val expected = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
        for (i in expected.indices) {
            assertEquals(expected[i], values[i], delta)
        }
    }

    @Test
    fun exposurePlusOne_doublesRgbScale() {
        val values = RecipeColorMatrix.values(Recipe(exposure = 1f))
        assertEquals(2f, values[0], delta)
        assertEquals(2f, values[6], delta)
        assertEquals(2f, values[12], delta)
        assertEquals(0f, values[4], delta)
        assertEquals(0f, values[9], delta)
        assertEquals(0f, values[14], delta)
    }

    @Test
    fun saturationMinus100_producesGrayscaleRows() {
        val values = RecipeColorMatrix.values(Recipe(saturation = -100f))
        // Cada linha RGB vira os pesos de luminância Rec. 709.
        val expectedRow = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                assertEquals(expectedRow[col], values[row * 5 + col], delta)
            }
            assertEquals(0f, values[row * 5 + 4], delta)
        }
    }

    @Test
    fun temperatureWarm_raisesRedLowersBlue() {
        val values = RecipeColorMatrix.values(Recipe(temperature = 100f))
        // Ganho R > 1 e ganho B < 1 (diagonal da matriz).
        assertTrue(values[0] > 1f)
        assertTrue(values[12] < 1f)
    }

    @Test
    fun alphaRow_isAlwaysIdentity() {
        val values = RecipeColorMatrix.values(
            Recipe(
                exposure = 1f,
                contrast = 50f,
                saturation = -50f,
                temperature = 40f
            )
        )
        assertEquals(0f, values[15], delta)
        assertEquals(0f, values[16], delta)
        assertEquals(0f, values[17], delta)
        assertEquals(1f, values[18], delta)
        assertEquals(0f, values[19], delta)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
