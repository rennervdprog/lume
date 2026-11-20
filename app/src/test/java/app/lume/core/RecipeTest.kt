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
    fun interpolation_presetParcial_preservaAjusteManual() {
        // 0.15.2: o preset só move os campos que define — a vinheta
        // em 100 não some ao aplicar um preset sem vinheta.
        val base = Recipe(vignette = 100f, exposure = 1f)
        val preset = Recipe(temperature = 28f)
        val result = base.interpolatedTo(preset, 1f)
        assertEquals(100f, result.vignette, 0.001f)
        assertEquals(1f, result.exposure, 0.001f)
        assertEquals(28f, result.temperature, 0.001f)
    }

    @Test
    fun interpolation_presetParcial_moveOCampoQueDefine() {
        val base = Recipe(vignette = 100f)
        val preset = Recipe(vignette = 10f, temperature = 28f)
        val result = base.interpolatedTo(preset, 1f)
        assertEquals(10f, result.vignette, 0.001f)
        assertEquals(28f, result.temperature, 0.001f)
    }

    @Test
    fun interpolation_presetParcial_meiaIntensidade() {
        val base = Recipe(vignette = 100f, temperature = 0f)
        val preset = Recipe(temperature = 28f)
        val result = base.interpolatedTo(preset, 0.5f)
        assertEquals(100f, result.vignette, 0.001f)
        assertEquals(14f, result.temperature, 0.001f)
    }

    @Test
    fun interpolation_presetParcial_preservaLutDoUsuario() {
        val base = Recipe(lutId = "lut-teal-orange", lutIntensity = 0.8f)
        val semLut = Recipe(temperature = 28f)
        val kept = base.interpolatedTo(semLut, 1f)
        assertEquals("lut-teal-orange", kept.lutId)
        assertEquals(0.8f, kept.lutIntensity, 0.001f)
        val comLut = Recipe(lutId = "lut-faded-film", lutIntensity = 0.5f)
        val taken = base.interpolatedTo(comLut, 1f)
        assertEquals("lut-faded-film", taken.lutId)
        assertEquals(0.5f, taken.lutIntensity, 0.001f)
    }

    @Test
    fun interpolation_presetParcial_preservaCanaisHslNaoDefinidos() {
        val orange = 1
        val yellow = 2
        val base = Recipe(
            hsl = List(HslChannels.COUNT) { i ->
                if (i == orange) HslChannel(luminance = 20f) else HslChannel()
            }
        )
        val preset = Recipe(
            hsl = List(HslChannels.COUNT) { i ->
                if (i == yellow) HslChannel(saturation = 6f) else HslChannel()
            }
        )
        val result = base.interpolatedTo(preset, 1f)
        assertEquals(20f, result.hsl[orange].luminance, 0.001f)
        assertEquals(6f, result.hsl[yellow].saturation, 0.001f)
    }

    @Test
    fun interpolation_presetParcial_hslParcialPorComponente() {
        val orange = 1
        val base = Recipe(
            hsl = List(HslChannels.COUNT) { i ->
                if (i == orange) HslChannel(hue = 20f, saturation = 30f, luminance = 10f)
                else HslChannel()
            }
        )
        val preset = Recipe(
            hsl = List(HslChannels.COUNT) { i ->
                if (i == orange) HslChannel(saturation = 6f) else HslChannel()
            }
        )
        val result = base.interpolatedTo(preset, 1f)
        assertEquals(20f, result.hsl[orange].hue, 0.001f)
        assertEquals(6f, result.hsl[orange].saturation, 0.001f)
        assertEquals(10f, result.hsl[orange].luminance, 0.001f)
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
