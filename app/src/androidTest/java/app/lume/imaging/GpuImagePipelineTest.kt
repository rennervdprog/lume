package app.lume.imaging

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.lume.core.HslChannel
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

    private fun solidBitmap(width: Int, height: Int, color: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
}
