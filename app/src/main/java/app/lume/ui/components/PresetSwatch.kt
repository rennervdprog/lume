package app.lume.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.lume.core.Preset
import app.lume.core.RecipeColorMatrix

/**
 * Miniatura de um preset: faixas de cores representativas (pele, céu,
 * folha, calor, neutro) com a matriz do preset aplicada.
 *
 * É uma AMOSTRA de cor gerada em código — não finge ser uma foto.
 * O look real aparece na tira do Editor, sobre a foto que está aberta.
 * Um único bitmap é compartilhado por todos os swatches.
 */
private val swatchBitmap: Bitmap by lazy { createSwatchBitmap() }

@Composable
fun PresetSwatch(preset: Preset, modifier: Modifier = Modifier) {
    val filter = remember(preset.recipe) {
        ColorFilter.colorMatrix(ColorMatrix(RecipeColorMatrix.values(preset.recipe)))
    }
    Image(
        bitmap = swatchBitmap.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = filter,
        modifier = modifier
    )
}

private fun createSwatchBitmap(): Bitmap {
    val width = 160
    val height = 120
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    // Faixas verticais: pele, céu, folha, calor, neutro.
    val bands = arrayOf(
        Triple(217, 160, 122),
        Triple(127, 168, 217),
        Triple(123, 160, 91),
        Triple(217, 179, 106),
        Triple(138, 143, 148)
    )
    for (y in 0 until height) {
        val shade = 1f - (y.toFloat() / height) * 0.45f
        for (x in 0 until width) {
            val (r0, g0, b0) = bands[(x * bands.size) / width]
            val r = (r0 * shade).toInt()
            val g = (g0 * shade).toInt()
            val b = (b0 * shade).toInt()
            bitmap.setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
        }
    }
    return bitmap
}
