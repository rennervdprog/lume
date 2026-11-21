package app.lume.imaging

import android.graphics.Bitmap

/**
 * Histograma de luminância de um bitmap JÁ editado.
 *
 * O chamador passa o bitmap renderizado pelo motor (a mesma imagem que
 * aparece na tela) — o histograma mostra o resultado da edição, não a
 * foto original, sem reaplicar a receita. Calculado sobre uma cópia
 * pequena (lado maior 160px). Falha de processamento retorna lista
 * vazia: o chamador esconde o gráfico, nunca estoura.
 */
object Histogram {

    const val BUCKETS: Int = 48

    private const val SAMPLE_MAX_DIMENSION: Int = 160

    fun compute(edited: Bitmap, buckets: Int = BUCKETS): List<Float> {
        return try {
            val scaled = scaleDown(edited)
            val scaledIsCopy = scaled !== edited
            val counts = IntArray(buckets)
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b
                val bucket = ((luminance / 255f) * buckets).toInt()
                    .coerceIn(0, buckets - 1)
                counts[bucket]++
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

    /**
     * Histograma RGB: 3 listas (R, G, B), um valor por balde, mesma
     * amostragem do [compute]. Falha retorna 3 listas vazias.
     */
    fun computeRgb(edited: Bitmap, buckets: Int = BUCKETS): List<List<Float>> {
        return try {
            val scaled = scaleDown(edited)
            val scaledIsCopy = scaled !== edited
            val counts = Array(3) { IntArray(buckets) }
            val pixels = IntArray(scaled.width * scaled.height)
            scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
            for (pixel in pixels) {
                val channels = intArrayOf(
                    (pixel shr 16) and 0xFF,
                    (pixel shr 8) and 0xFF,
                    pixel and 0xFF
                )
                for (c in 0..2) {
                    val bucket = ((channels[c] / 255f) * buckets).toInt()
                        .coerceIn(0, buckets - 1)
                    counts[c][bucket]++
                }
            }
            if (scaledIsCopy) {
                scaled.recycle()
            }
            counts.map { channelCounts ->
                val max = channelCounts.maxOrNull() ?: 0
                if (max <= 0) {
                    emptyList()
                } else {
                    channelCounts.map { count ->
                        if (count == 0) 0f else (count.toFloat() / max).coerceAtLeast(0.03f)
                    }
                }
            }
        } catch (e: Exception) {
            listOf(emptyList(), emptyList(), emptyList())
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
}
