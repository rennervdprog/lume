package app.lume.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoPaletteTest {

    @Test
    fun selectSwatchColors_sixProfiles_returnsSix() {
        val profiles = listOf(1, 2, 3, 4, 5, 6)
        assertEquals(profiles, PhotoPalette.selectSwatchColors(profiles, listOf(7, 8)))
    }

    @Test
    fun selectSwatchColors_fewProfiles_fillsByPopulation() {
        val result = PhotoPalette.selectSwatchColors(
            profiles = listOf(1, 2),
            byPopulation = listOf(3, 4, 5, 6, 7)
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6), result)
    }

    @Test
    fun selectSwatchColors_duplicates_removed() {
        val result = PhotoPalette.selectSwatchColors(
            profiles = listOf(1, 2),
            byPopulation = listOf(2, 3, 1, 4, 5, 6)
        )
        assertEquals(listOf(1, 2, 3, 4, 5, 6), result)
    }

    @Test
    fun selectSwatchColors_empty_returnsEmpty() {
        assertEquals(
            emptyList<Int>(),
            PhotoPalette.selectSwatchColors(emptyList(), emptyList())
        )
    }

    @Test
    fun hexString_formatsLowercase() {
        assertEquals("#1e170b", hexString(0xFF1E170B.toInt()))
        assertEquals("#ffffff", hexString(-1))
        assertEquals("#000000", hexString(0xFF000000.toInt()))
    }
}
