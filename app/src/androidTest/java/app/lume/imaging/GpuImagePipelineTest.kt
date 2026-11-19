package app.lume.imaging

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lume.core.HslChannel
import app.lume.core.LutColor
import app.lume.core.Recipe
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prova de verdade do motor: renderiza bitmaps pequenos pela cadeia
 * completa da GPUImage no emulador e confere os pixels. Estes testes
 * existem porque o motor anterior "passava" em tudo sem rodar — o HSL
 * dele não funcionava no aparelho e nenhum teste percebia. Aqui, se o
 * GL falhar, o render volta null e o teste FALHA (não cai no fallback
 * silencioso: o fallback vive no chamador, não no motor).
 */
@RunWith(AndroidJUnit4::class)
class GpuImagePipelineTest {

    @After
    fun tearDown() {
        GlEngine.release()
    }

    @Test
    fun renderIdentidadePreservaPixel() = runBlocking {
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val out = GlEngine.render(source, Recipe())
        assertNotNull("Motor GL não renderizou", out)
        out!!
        assertEquals(64, out.width)
        assertEquals(64, out.height)
        val pixel = out.getPixel(32, 32)
        assertTrue("R mudou demais: ${Color.red(pixel)}", abs(Color.red(pixel) - 120) <= 8)
        assertTrue("G mudou demais: ${Color.green(pixel)}", abs(Color.green(pixel) - 60) <= 8)
        assertTrue("B mudou demais: ${Color.blue(pixel)}", abs(Color.blue(pixel) - 30) <= 8)
    }

    @Test
    fun hslDessaturaVermelho() = runBlocking {
        val channels = List(8) { index ->
            if (index == 0) HslChannel(hue = 0f, saturation = -100f, luminance = 0f)
            else HslChannel()
        }
        val source = solidBitmap(64, 64, Color.rgb(255, 0, 0))
        val out = GlEngine.render(source, Recipe(hsl = channels))
        assertNotNull("Motor GL não renderizou", out)
        val pixel = out!!.getPixel(32, 32)
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        // Vermelho puro com saturação -100 no canal Vermelho: a
        // matemática prevê ~(154, 101, 101) — o peso do vizinho
        // Laranja dilui o efeito (normalização por peso total).
        assertTrue("HSL não dessaturou: ($r,$g,$b)", r in 140..170)
        assertTrue("HSL não dessaturou: ($r,$g,$b)", g in 85..115)
        assertTrue("HSL não dessaturou: ($r,$g,$b)", b in 85..115)
    }

    @Test
    fun exposicaoMaisUmDobraBrilho() = runBlocking {
        val source = solidBitmap(64, 64, Color.rgb(100, 100, 100))
        val out = GlEngine.render(source, Recipe(exposure = 1f))
        assertNotNull("Motor GL não renderizou", out)
        val pixel = out!!.getPixel(32, 32)
        val r = Color.red(pixel)
        assertTrue("Exposição não dobrou: $r", r in 185..215)
    }

    @Test
    fun lutIdentidadePreservaPixel() = runBlocking {
        val grid = LutGrid.identity().toBitmap()
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val out = GlEngine.render(
            source,
            Recipe(lutId = "test-identity", lutIntensity = 1f),
            grid
        )
        assertNotNull("Motor GL não renderizou com LUT", out)
        val pixel = out!!.getPixel(32, 32)
        assertTrue("R mudou demais: ${Color.red(pixel)}", abs(Color.red(pixel) - 120) <= 8)
        assertTrue("G mudou demais: ${Color.green(pixel)}", abs(Color.green(pixel) - 60) <= 8)
        assertTrue("B mudou demais: ${Color.blue(pixel)}", abs(Color.blue(pixel) - 30) <= 8)
    }

    @Test
    fun lutVermelhoSolidoPintaDeVermelho() = runBlocking {
        val grid = LutGrid.fromTransform { _, _, _ -> LutColor(1f, 0f, 0f) }.toBitmap()
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val out = GlEngine.render(
            source,
            Recipe(lutId = "test-red", lutIntensity = 1f),
            grid
        )
        assertNotNull("Motor GL não renderizou com LUT", out)
        val pixel = out!!.getPixel(32, 32)
        assertTrue("R devia ser 255: ${Color.red(pixel)}", Color.red(pixel) >= 247)
        assertTrue("G devia ser 0: ${Color.green(pixel)}", Color.green(pixel) <= 8)
        assertTrue("B devia ser 0: ${Color.blue(pixel)}", Color.blue(pixel) <= 8)
    }

    @Test
    fun lutTrocaCanaisProvaOrientacaoDaGrade() = runBlocking {
        // (r,g,b) -> (g,b,r): só passa se a grade e o shader
        // concordarem sobre qual eixo é qual — é a prova de
        // orientação do formato HALD 64.
        val grid = LutGrid.fromTransform { r, g, b -> LutColor(g, b, r) }.toBitmap()
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val out = GlEngine.render(
            source,
            Recipe(lutId = "test-swap", lutIntensity = 1f),
            grid
        )
        assertNotNull("Motor GL não renderizou com LUT", out)
        val pixel = out!!.getPixel(32, 32)
        assertTrue("R devia ser 60: ${Color.red(pixel)}", abs(Color.red(pixel) - 60) <= 8)
        assertTrue("G devia ser 30: ${Color.green(pixel)}", abs(Color.green(pixel) - 30) <= 8)
        assertTrue("B devia ser 120: ${Color.blue(pixel)}", abs(Color.blue(pixel) - 120) <= 8)
    }

    @Test
    fun lutIntensidadeMeioMisturaComOriginal() = runBlocking {
        val grid = LutGrid.fromTransform { _, _, _ -> LutColor(1f, 0f, 0f) }.toBitmap()
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val out = GlEngine.render(
            source,
            Recipe(lutId = "test-red", lutIntensity = 0.5f),
            grid
        )
        assertNotNull("Motor GL não renderizou com LUT", out)
        val pixel = out!!.getPixel(32, 32)
        assertTrue("R devia ser ~187: ${Color.red(pixel)}", abs(Color.red(pixel) - 187) <= 10)
        assertTrue("G devia ser ~30: ${Color.green(pixel)}", abs(Color.green(pixel) - 30) <= 10)
        assertTrue("B devia ser ~15: ${Color.blue(pixel)}", abs(Color.blue(pixel) - 15) <= 10)
    }

    @Test
    fun lutSemGradeRenderizaOriginal() = runBlocking {
        // Receita com LUT mas sem grade (importado apagado): o motor
        // renderiza sem LUT, sem quebrar.
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val out = GlEngine.render(
            source,
            Recipe(lutId = "test-missing", lutIntensity = 1f),
            null
        )
        assertNotNull("Motor GL não renderizou", out)
        val pixel = out!!.getPixel(32, 32)
        assertTrue("R mudou demais: ${Color.red(pixel)}", abs(Color.red(pixel) - 120) <= 8)
        assertTrue("G mudou demais: ${Color.green(pixel)}", abs(Color.green(pixel) - 60) <= 8)
        assertTrue("B mudou demais: ${Color.blue(pixel)}", abs(Color.blue(pixel) - 30) <= 8)
    }

    @Test
    fun lutTrocaDeGradeNoMesmoPipeline() = runBlocking {
        // Segunda grade no mesmo pipeline: prova que a textura do
        // LUT é recarregada ao trocar (o LookupFilter da biblioteca
        // não faz isso — motivo do filtro próprio).
        val redGrid = LutGrid.fromTransform { _, _, _ -> LutColor(1f, 0f, 0f) }.toBitmap()
        val blueGrid = LutGrid.fromTransform { _, _, _ -> LutColor(0f, 0f, 1f) }.toBitmap()
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val first = GlEngine.render(source, Recipe(lutId = "a", lutIntensity = 1f), redGrid)
        assertNotNull("Primeiro render com LUT falhou", first)
        assertTrue("Primeiro devia ser vermelho", Color.red(first!!.getPixel(32, 32)) >= 247)
        val second = GlEngine.render(source, Recipe(lutId = "b", lutIntensity = 1f), blueGrid)
        assertNotNull("Segundo render com LUT falhou", second)
        val pixel = second!!.getPixel(32, 32)
        assertTrue("Segundo devia ser azul: ($pixel)", Color.blue(pixel) >= 247)
        assertTrue("Segundo devia ser azul: ($pixel)", Color.red(pixel) <= 8)
    }

    @Test
    fun levelsPretoCortaSombras() = runBlocking {
        val source = solidBitmap(64, 64, Color.rgb(100, 100, 100))
        val out = GlEngine.render(source, Recipe(levelsBlack = 50f))
        assertNotNull("Motor GL não renderizou com níveis", out)
        val pixel = out!!.getPixel(32, 32)
        // Cinza 100/255 ≈ 0.39 cai abaixo do preto 0.5 → preto.
        assertTrue("Níveis não cortaram: ${Color.red(pixel)}", Color.red(pixel) <= 8)
    }

    @Test
    fun hazePositivoDesembacaENegativoAdicionaNevoa() = runBlocking {
        val source = solidBitmap(64, 64, Color.rgb(200, 200, 200))
        val dehazed = GlEngine.render(source, Recipe(haze = 100f))
        assertNotNull("Motor GL não renderizou com névoa +", dehazed)
        val dark = Color.red(dehazed!!.getPixel(32, 32))
        // Distância +0.25: (0.78-0.25)/0.75 ≈ 0.71 → ~181.
        assertTrue("Desembaçar não escureceu: $dark", dark in 165..195)
        val hazed = GlEngine.render(source, Recipe(haze = -100f))
        assertNotNull("Motor GL não renderizou com névoa -", hazed)
        val light = Color.red(hazed!!.getPixel(32, 32))
        // Distância -0.25: (0.78+0.25)/1.25 ≈ 0.83 → ~211.
        assertTrue("Névoa não clareou: $light", light in 200..225)
    }

    @Test
    fun blurSuavizaBorda() = runBlocking {
        val source = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
            for (y in 0 until 64) {
                for (x in 0 until 64) {
                    setPixel(x, y, if (x < 32) Color.BLACK else Color.WHITE)
                }
            }
        }
        val sharp = GlEngine.render(source, Recipe())
        assertNotNull("Motor GL não renderizou sem desfoque", sharp)
        assertEquals(255, Color.red(sharp!!.getPixel(33, 32)))
        val blurred = GlEngine.render(source, Recipe(blur = 100f))
        assertNotNull("Motor GL não renderizou com desfoque", blurred)
        val edge = Color.red(blurred!!.getPixel(33, 32))
        // Kernel de 9 amostras ±4px: o pixel 1px à direita da borda
        // mistura ~26% de preto → ~189. Sem desfoque seria 255.
        assertTrue("Desfoque não suavizou a borda: $edge", edge in 140..235)
    }

    @Test
    fun sepiaPintaDeSepia() = runBlocking {
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val out = GlEngine.render(source, Recipe(artisticEffect = "sepia", artisticIntensity = 1f))
        assertNotNull("Motor GL não renderizou com sépia", out)
        val pixel = out!!.getPixel(32, 32)
        // Matriz sépia da GPUImage: R≈89, G≈74, B≈59.
        assertTrue("R do sépia: ${Color.red(pixel)}", Color.red(pixel) in 80..100)
        assertTrue("G do sépia: ${Color.green(pixel)}", Color.green(pixel) in 65..85)
        assertTrue("B do sépia: ${Color.blue(pixel)}", Color.blue(pixel) in 50..70)
    }

    @Test
    fun blendMultiplyEscurece() = runBlocking {
        val source = solidBitmap(64, 64, Color.rgb(200, 100, 50))
        val second = solidBitmap(64, 64, Color.rgb(128, 128, 128))
        val out = GlEngine.render(
            source,
            Recipe(blendMode = "multiply", blendOpacity = 1f),
            null,
            second
        )
        assertNotNull("Motor GL não renderizou com mistura", out)
        val pixel = out!!.getPixel(32, 32)
        // Multiplicar: 200×128/255≈100, 100×128/255≈50, 50×128/255≈25.
        assertTrue("R da mistura: ${Color.red(pixel)}", Color.red(pixel) in 90..112)
        assertTrue("G da mistura: ${Color.green(pixel)}", Color.green(pixel) in 42..60)
        assertTrue("B da mistura: ${Color.blue(pixel)}", Color.blue(pixel) in 18..33)
    }

    @Test
    fun blendSemFotoRenderizaOriginal() = runBlocking {
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val out = GlEngine.render(source, Recipe(blendMode = "multiply", blendOpacity = 1f), null, null)
        assertNotNull("Motor GL não renderizou", out)
        val pixel = out!!.getPixel(32, 32)
        assertTrue("R mudou demais: ${Color.red(pixel)}", abs(Color.red(pixel) - 120) <= 8)
        assertTrue("G mudou demais: ${Color.green(pixel)}", abs(Color.green(pixel) - 60) <= 8)
        assertTrue("B mudou demais: ${Color.blue(pixel)}", abs(Color.blue(pixel) - 30) <= 8)
    }

    @Test
    fun rgbVermelhoZeraCanal() = runBlocking {
        val source = solidBitmap(64, 64, Color.rgb(120, 60, 30))
        val out = GlEngine.render(source, Recipe(rgbR = -100f))
        assertNotNull("Motor GL não renderizou com RGB", out)
        val pixel = out!!.getPixel(32, 32)
        assertTrue("R devia ser 0: ${Color.red(pixel)}", Color.red(pixel) <= 8)
        assertTrue("G mudou demais: ${Color.green(pixel)}", abs(Color.green(pixel) - 60) <= 8)
        assertTrue("B mudou demais: ${Color.blue(pixel)}", abs(Color.blue(pixel) - 30) <= 8)
    }

    @Test
    fun gammaCemClareiaMeiosTons() = runBlocking {
        val source = solidBitmap(64, 64, Color.rgb(100, 100, 100))
        val out = GlEngine.render(source, Recipe(gamma = 100f))
        assertNotNull("Motor GL não renderizou com gama", out)
        val pixel = out!!.getPixel(32, 32)
        // Gama 0.5: (100/255)^0.5 ≈ 0.63 → ~160.
        assertTrue("Gama não clareou: ${Color.red(pixel)}", Color.red(pixel) in 145..175)
    }

    private fun solidBitmap(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
}
