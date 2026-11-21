package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextOverlayTest {

    @Test
    fun defaultOverlay_isEmpty() {
        assertTrue(TextOverlay().isEmpty)
        assertFalse(TextOverlay(text = "Olá").isEmpty)
        assertTrue(TextOverlay(text = "   ").isEmpty)
    }

    @Test
    fun defaults_matchPreview() {
        val overlay = TextOverlay(text = "Verão")
        assertEquals(TextFont.DANCING_SCRIPT, overlay.font)
        assertEquals(0.08f, overlay.sizeFraction, 0.0001f)
        assertEquals(-1, overlay.colorArgb)
        assertEquals(0.5f, overlay.centerX, 0.0001f)
        assertEquals(0.35f, overlay.centerY, 0.0001f)
    }

    @Test
    fun fitScale_fits_returnsOne() {
        assertEquals(1f, fitScale(100f, 200f), 0.0001f)
        assertEquals(1f, fitScale(200f, 200f), 0.0001f)
    }

    @Test
    fun fitScale_tooWide_shrinks() {
        assertEquals(0.5f, fitScale(400f, 200f), 0.0001f)
    }

    @Test
    fun fitScale_invalid_returnsOne() {
        assertEquals(1f, fitScale(0f, 200f), 0.0001f)
        assertEquals(1f, fitScale(100f, 0f), 0.0001f)
        assertEquals(1f, fitScale(-5f, 200f), 0.0001f)
    }

    @Test
    fun centeredBaselineOffset_centers() {
        // ascent -10, descent 2: a baseline fica 4px abaixo do centro.
        assertEquals(4f, centeredBaselineOffset(-10f, 2f), 0.0001f)
    }

    @Test
    fun fiveFonts_distinctResources() {
        val fonts = TextFont.entries
        assertEquals(5, fonts.size)
        assertEquals(5, fonts.map { it.fontRes }.distinct().size)
        assertEquals(5, fonts.map { it.displayName }.distinct().size)
    }
}
