package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToneCurveLutTest {

    private fun assertLutNear(expected: FloatArray, actual: FloatArray, delta: Float) {
        assertEquals(expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("índice $i", expected[i], actual[i], delta)
        }
    }

    @Test
    fun identityPoints_buildRamp() {
        val lut = ToneCurveLut.build(ToneCurve.IDENTITY_POINTS)
        assertEquals(ToneCurveLut.SIZE, lut.size)
        assertEquals(0f, lut[0], 0.001f)
        assertEquals(1f, lut[255], 0.001f)
        assertEquals(0.5f, lut[127], 0.01f)
        assertTrue(ToneCurveLut.isIdentity(ToneCurve.IDENTITY_POINTS))
    }

    @Test
    fun emptyPoints_buildRamp() {
        val lut = ToneCurveLut.build(emptyList())
        assertEquals(0f, lut[0], 0.001f)
        assertEquals(1f, lut[255], 0.001f)
    }

    @Test
    fun singlePoint_buildsConstant() {
        val lut = ToneCurveLut.build(listOf(CurvePoint(0.5f, 0.7f)))
        assertLutNear(FloatArray(ToneCurveLut.SIZE) { 0.7f }, lut, 0.001f)
    }

    @Test
    fun contrastPreset_darkensShadowsAndBrightensHighlights() {
        val lut = ToneCurveLut.build(ToneCurve.PRESET_CONTRAST)
        // S-curve: abaixo da diagonal nas sombras, acima nos realces.
        assertTrue("sombra abaixo da diagonal", lut[64] < 64f / 255f)
        assertTrue("realce acima da diagonal", lut[192] > 192f / 255f)
        assertEquals(0.5f, lut[127], 0.01f)
        assertFalse(ToneCurveLut.isIdentity(ToneCurve.PRESET_CONTRAST))
    }

    @Test
    fun fadePreset_liftsBlacksAndLowersWhites() {
        val lut = ToneCurveLut.build(ToneCurve.PRESET_FADE)
        assertEquals(0.12f, lut[0], 0.001f)
        assertEquals(0.92f, lut[255], 0.001f)
    }

    @Test
    fun linearPreset_isIdentity() {
        assertTrue(ToneCurveLut.isIdentity(ToneCurve.PRESET_LINEAR))
    }

    @Test
    fun unsortedPoints_areSorted() {
        val lut = ToneCurveLut.build(
            listOf(CurvePoint(1f, 1f), CurvePoint(0f, 0f))
        )
        assertEquals(0f, lut[0], 0.001f)
        assertEquals(1f, lut[255], 0.001f)
    }

    @Test
    fun output_isClamped() {
        val lut = ToneCurveLut.build(
            listOf(CurvePoint(0f, -0.5f), CurvePoint(1f, 1.5f))
        )
        assertEquals(0f, lut[0], 0.001f)
        assertEquals(1f, lut[255], 0.001f)
    }

    @Test
    fun buildRgba_packsChannelsInOrder() {
        val curve = ToneCurve(
            master = ToneCurve.PRESET_FADE,
            red = ToneCurve.IDENTITY_POINTS,
            green = ToneCurve.PRESET_CONTRAST,
            blue = listOf(CurvePoint(0f, 0.25f), CurvePoint(1f, 0.25f))
        )
        val bytes = ToneCurveLut.buildRgba(curve)
        assertEquals(ToneCurveLut.SIZE * 4, bytes.size)
        // Primeiro texel: R=0 (identidade), G=0 (contraste), B=0.25, A=0.12 (fade).
        assertEquals(0, bytes[0].toInt() and 0xFF)
        assertEquals(0, bytes[1].toInt() and 0xFF)
        assertEquals(64, bytes[2].toInt() and 0xFF)
        assertEquals(31, bytes[3].toInt() and 0xFF)
        // Último texel: R=255, G=255, B=0.25, A=0.92.
        val last = (ToneCurveLut.SIZE - 1) * 4
        assertEquals(255, bytes[last].toInt() and 0xFF)
        assertEquals(255, bytes[last + 1].toInt() and 0xFF)
        assertEquals(64, bytes[last + 2].toInt() and 0xFF)
        assertEquals(235, bytes[last + 3].toInt() and 0xFF)
    }

    @Test
    fun curve_isEffectiveIdentity_onlyWhenAllChannelsIdentity() {
        assertTrue(ToneCurve().isEffectiveIdentity())
        val withMaster = ToneCurve(master = ToneCurve.PRESET_CONTRAST)
        assertFalse(withMaster.isEffectiveIdentity())
        val withRed = ToneCurve(red = ToneCurve.PRESET_FADE)
        assertFalse(withRed.isEffectiveIdentity())
    }

    @Test
    fun presetNameFor_recognizesChips() {
        assertEquals("Contraste", ToneCurve.presetNameFor(ToneCurve.PRESET_CONTRAST))
        assertEquals("Fade", ToneCurve.presetNameFor(ToneCurve.PRESET_FADE))
        assertEquals("Linear", ToneCurve.presetNameFor(ToneCurve.IDENTITY_POINTS))
        assertEquals(
            null,
            ToneCurve.presetNameFor(listOf(CurvePoint(0f, 0.1f), CurvePoint(1f, 0.9f)))
        )
    }
}
