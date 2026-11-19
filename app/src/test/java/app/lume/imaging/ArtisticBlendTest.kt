package app.lume.imaging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtisticBlendTest {

    @Test
    fun artisticEffects_temDezComIdsUnicos() {
        val ids = ArtisticEffect.entries.map { it.id }
        assertEquals(10, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(ArtisticEffect.TOON, ArtisticEffect.fromId("toon"))
        assertEquals(ArtisticEffect.SKETCH, ArtisticEffect.fromId("sketch"))
        assertEquals(ArtisticEffect.SEPIA, ArtisticEffect.fromId("sepia"))
        assertNull(ArtisticEffect.fromId("inexistente"))
        assertNull(ArtisticEffect.fromId(null))
    }

    @Test
    fun sketchNaoTemIntensidadeOutrosTem() {
        assertTrue(ArtisticEffect.entries.filter { it.hasIntensity }.size == 9)
        assertTrue(!ArtisticEffect.SKETCH.hasIntensity)
    }

    @Test
    fun blendModes_temOitoComIdsUnicos() {
        val ids = BlendMode.entries.map { it.id }
        assertEquals(8, ids.size)
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(BlendMode.MULTIPLY, BlendMode.fromId("multiply"))
        assertEquals(BlendMode.SCREEN, BlendMode.fromId("screen"))
        assertEquals(BlendMode.COLOR, BlendMode.fromId("color"))
        assertEquals(BlendMode.LUMINOSITY, BlendMode.fromId("luminosity"))
        assertEquals(BlendMode.LIGHTEN, BlendMode.fromId("lighten"))
        assertNull(BlendMode.fromId("inexistente"))
        assertNull(BlendMode.fromId(null))
    }
}
