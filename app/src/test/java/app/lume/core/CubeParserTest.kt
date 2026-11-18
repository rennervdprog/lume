package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CubeParserTest {

    @Test
    fun leCubo2x2IdentidadeNaOrdemCerta() {
        val text = """
            # Comentário ignorado
            TITLE "Identidade 2"
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
        val result = CubeParser.parse(text)
        assertTrue(result is CubeParseResult.Ok)
        val cube = (result as CubeParseResult.Ok).cube
        assertEquals(2, cube.size)
        assertEquals("Identidade 2", cube.title)
        assertEquals(24, cube.data.size)
        // Vermelho varia mais rápido: o 2º ponto é (1,0,0).
        assertEquals(1f, cube.data[3], 0f)
        assertEquals(0f, cube.data[4], 0f)
        // O 3º ponto é (0,1,0).
        assertEquals(0f, cube.data[6], 0f)
        assertEquals(1f, cube.data[7], 0f)
        // O 5º ponto é (0,0,1).
        assertEquals(0f, cube.data[12], 0f)
        assertEquals(1f, cube.data[14], 0f)
    }

    @Test
    fun leDominioDeclarado() {
        val text = """
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
        val result = CubeParser.parse(text)
        assertTrue(result is CubeParseResult.Ok)
        val cube = (result as CubeParseResult.Ok).cube
        assertEquals(0.5f, cube.domainMax[0], 0f)
        assertEquals(0f, cube.domainMin[2], 0f)
    }

    @Test
    fun dominioPadraoEZeroAUm() {
        val cube = parseIdentity2()
        assertEquals(0f, cube.domainMin[0], 0f)
        assertEquals(1f, cube.domainMax[1], 0f)
        assertNull(cube.title)
    }

    @Test
    fun rejeitaLut1d() {
        val result = CubeParser.parse("LUT_1D_SIZE 16\n0.0 0.0 0.0\n")
        assertTrue(result is CubeParseResult.Fail)
    }

    @Test
    fun rejeitaSemTamanho() {
        val result = CubeParser.parse("0.0 0.0 0.0\n1.0 0.0 0.0\n")
        assertTrue(result is CubeParseResult.Fail)
    }

    @Test
    fun rejeitaContagemErrada() {
        val text = "LUT_3D_SIZE 2\n0.0 0.0 0.0\n1.0 0.0 0.0\n"
        val result = CubeParser.parse(text)
        assertTrue(result is CubeParseResult.Fail)
        val reason = (result as CubeParseResult.Fail).reason
        assertTrue(reason.contains("8"))
    }

    @Test
    fun rejeitaTamanhoForaDoSuportado() {
        assertTrue(CubeParser.parse("LUT_3D_SIZE 1\n") is CubeParseResult.Fail)
        assertTrue(CubeParser.parse("LUT_3D_SIZE 65\n") is CubeParseResult.Fail)
    }

    @Test
    fun ignoraPalavraChaveDesconhecida() {
        val text = """
            LUT_3D_SIZE 2
            COMMENT_TOOL alguma coisa
            0.0 0.0 0.0
            1.0 0.0 0.0
            0.0 1.0 0.0
            1.0 1.0 0.0
            0.0 0.0 1.0
            1.0 0.0 1.0
            0.0 1.0 1.0
            1.0 1.0 1.0
        """.trimIndent()
        assertTrue(CubeParser.parse(text) is CubeParseResult.Ok)
    }

    @Test
    fun rejeitaTextoVazio() {
        assertTrue(CubeParser.parse("") is CubeParseResult.Fail)
        assertTrue(CubeParser.parse("não é um cube") is CubeParseResult.Fail)
    }

    private fun parseIdentity2(): ParsedCube {
        val text = """
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
        val result = CubeParser.parse(text)
        assertTrue(result is CubeParseResult.Ok)
        return (result as CubeParseResult.Ok).cube
    }
}
