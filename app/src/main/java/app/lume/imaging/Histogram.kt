package app.lume.imaging

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import app.lume.core.Recipe
import app.lume.core.RecipeColorMatrix

/**
 * Histograma de luminância da foto COM a receita aplicada.
 *
 * Calculado sobre uma cópia pequena (lado maior 160px) com a MESMA
 * matriz de [RecipeColorMatrix] do preview — o histograma mostra o
 * resultado da edição, não a foto original. Falha de processamento
 * retorna lista vazia: o chamador esconde o gráfico, nunca estoura.
 */
object Histogram {

    const val BUCKETS: Int = 48

    private const val SAMPLE_MAX_DIMENSION: Int = 160

    fun compute(source: Bitmap, recipe: Recipe, buckets: Int = BUCKETS): List<Float> {
        return try {
            val scaled = scaleDown(source)
            val scaledIsCopy = scaled !== source
            val edited = applyMatrix(scaled, recipe)
            val counts = IntArray(buckets)
            val pixels = IntArray(edited.width * edited.height)
            edited.getPixels(pixels, 0, edited.width, 0, 0, edited.width, edited.height)
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val bucket = ((luminance / 255f) * buckets).toInt()
                    .coerceIn(0, buckets - 1)
                counts[bucket]++
            }
            if (edited !== scaled) {
                edited.recycle()
            }
            if (scaledIsCopy) {
                scaled.recycle()
            }
            val max = counts.maxOrNull() ?: 0
            if (max <= 0) {
                return emptyList()
            }
            counts.map { count ->
                if (count == 0) 0f else (count.toFloat() / max).coerceAtLeast(0.03f)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun scaleDown(source: Bitmap): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        if (maxSide <= SAMPLE_MAX_DIMENSION) return source
        val scale = SAMPLE_MAX_DIMENSION.toFloat() / maxSide
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, width, height, true)
    }

    private fun applyMatrix(source: Bitmap, recipe: Recipe): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(RecipeColorMatrix.values(recipe)))
        }
        Canvas(out).drawBitmap(source, 0f, 0f, paint)
        return out
    }
}
