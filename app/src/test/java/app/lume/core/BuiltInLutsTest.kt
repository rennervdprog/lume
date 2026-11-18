package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInLutsTest {

    @Test
    fun seisLutsComIdsUnicosENomes() {
        assertEquals(6, BuiltInLuts.all.size)
        val ids = BuiltInLuts.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(BuiltInLuts.all.all { it.name.isNotBlank() })
        assertTrue(ids.all { it.startsWith("lut-") })
    }

    @Test
    fun pbDevolveCinza() {
        val lut = BuiltInLuts.definition("lut-bw-contrast")!!
        val out = lut.transform(0.9f, 0.2f, 0.4f)
        assertEquals(out.r, out.g, 0.001f)
        assertEquals(out.g, out.b, 0.001f)
    }

    @Test
    fun filmeDesbotadoLevantaOPreto() {
        val lut = BuiltInLuts.definition("lut-faded-film")!!
        val out = lut.transform(0f, 0f, 0f)
        assertTrue("Preto devia levantar: $out", out.r > 0.05f && out.g > 0.05f)
        val white = lut.transform(1f, 1f, 1f)
        assertTrue("Branco devia comprimir: $white", white.r < 1f)
    }

    @Test
    fun douradoEsquentaECoolEsfria() {
        val golden = BuiltInLuts.definition("lut-golden")!!
        val warm = golden.transform(0.5f, 0.5f, 0.5f)
        assertTrue("Dourado devia esquentar: $warm", warm.r > warm.b)
        val urban = BuiltInLuts.definition("lut-cool-urban")!!
        val cool = urban.transform(0.5f, 0.5f, 0.5f)
        assertTrue("Urbano devia esfriar: $cool", cool.b > cool.r)
    }

    @Test
    fun tealOrangeSeparaSombraERealce() {
        val lut = BuiltInLuts.definition("lut-teal-orange")!!
        val shadow = lut.transform(0.1f, 0.1f, 0.1f)
        assertTrue("Sombra devia puxar azul: $shadow", shadow.b > shadow.r)
        val highlight = lut.transform(0.9f, 0.9f, 0.9f)
        assertTrue("Realce devia puxar laranja: $highlight", highlight.r > highlight.b)
    }

    @Test
    fun vintageEsquentaSemEstourar() {
        val lut = BuiltInLuts.definition("lut-vintage")!!
        val out = lut.transform(0.5f, 0.5f, 0.5f)
        assertTrue("Vintage devia esquentar: $out", out.r > out.b)
        assertTrue(out.r in 0f..1f && out.g in 0f..1f && out.b in 0f..1f)
    }

    @Test
    fun idDesconhecidoVoltaNull() {
        assertNull(BuiltInLuts.definition("lut-que-nao-existe"))
        assertNull(BuiltInLuts.definition(null))
    }
}
