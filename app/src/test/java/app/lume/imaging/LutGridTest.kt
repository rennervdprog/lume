package app.lume.imaging

import app.lume.core.CubeParseResult
import app.lume.core.CubeParser
import app.lume.core.LutColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LutGridTest {

    @Test
    fun gradeIdentidadeAmostraAPropriaCor() {
        val grid = LutGrid.identity()
        assertColor(LutColor(0f, 0f, 0f), grid.sample(0f, 0f, 0f))
        assertColor(LutColor(1f, 1f, 1f), grid.sample(1f, 1f, 1f))
        assertColor(LutColor(0.5f, 0.25f, 0.75f), grid.sample(0.5f, 0.25f, 0.75f))
        assertColor(LutColor(0.2f, 0.6f, 0.9f), grid.sample(0.2f, 0.6f, 0.9f))
    }

    @Test
    fun transformacaoSolidaAmostraSolido() {
        val grid = LutGrid.fromTransform { _, _, _ -> LutColor(1f, 0f, 0f) }
        assertColor(LutColor(1f, 0f, 0f), grid.sample(0.1f, 0.5f, 0.9f))
        assertColor(LutColor(1f, 0f, 0f), grid.sample(0.9f, 0.9f, 0.1f))
    }

    @Test
    fun transformacaoTrocaCanaisAmostraTrocado() {
        // (r,g,b) -> (g,b,r): prova o mapeamento dos 3 eixos na grade.
        val grid = LutGrid.fromTransform { r, g, b -> LutColor(g, b, r) }
        assertColor(LutColor(0.4f, 0.6f, 0.2f), grid.sample(0.2f, 0.4f, 0.6f))
        assertColor(LutColor(0.9f, 0.1f, 0.5f), grid.sample(0.5f, 0.9f, 0.1f))
    }

    @Test
    fun cuboIdentidade2x2ViraGradeIdentidade() {
        val cube = parseCube(
            """
            LUT_3D_SIZE 2
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
            """.trimIndent()
        )
        val grid = LutGrid.fromCube(cube)
        assertColor(LutColor(0.5f, 0.5f, 0.5f), grid.sample(0.5f, 0.5f, 0.5f))
        assertColor(LutColor(0.25f, 0.75f, 0.1f), grid.sample(0.25f, 0.75f, 0.1f))
    }

    @Test
    fun cuboRespeitaODominio() {
        // Grade 2³ sobre o domínio 0..0.5: a entrada 0.25 é o meio.
        val cube = parseCube(
            """
            LUT_3D_SIZE 2
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 0.5 0.5 0.5
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
            """.trimIndent()
        )
        val grid = LutGrid.fromCube(cube)
        assertColor(LutColor(0.5f, 0f, 0f), grid.sample(0.25f, 0f, 0f), delta = 0.02f)
        // Fora do domínio (acima do máximo): apara na borda.
        assertColor(LutColor(1f, 0f, 0f), grid.sample(0.9f, 0f, 0f), delta = 0.02f)
    }

    @Test
    fun amostraAparaEntradasForaDeZeroAUm() {
        val grid = LutGrid.identity()
        assertColor(LutColor(0f, 1f, 0.5f), grid.sample(-1f, 2f, 0.5f))
    }

    private fun parseCube(text: String): app.lume.core.ParsedCube {
        val result = CubeParser.parse(text)
        assertTrue(result is CubeParseResult.Ok)
        return (result as CubeParseResult.Ok).cube
    }

    private fun assertColor(expected: LutColor, actual: LutColor, delta: Float = 0.01f) {
        assertEquals(expected.r, actual.r, delta)
        assertEquals(expected.g, actual.g, delta)
        assertEquals(expected.b, actual.b, delta)
    }
}
