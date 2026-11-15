package app.lume.imaging

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * Decodificação de bitmaps com amostragem e correção de orientação EXIF.
 *
 * Regras (estudo §6.8): nunca carregar a foto total para o preview;
 * respeitar a orientação EXIF na decodificação e na escrita (foto
 * "deitada" após exportar é o bug clássico); nunca RGB_565.
 *
 * Falha de decodificação retorna null — o chamador mostra erro, nunca
 * estoura. Sem `!!` neste caminho.
 */
object BitmapLoader {

    /** Lado maior máximo do bitmap de preview (edição interativa). */
    const val PREVIEW_MAX_DIMENSION: Int = 2048

    fun decodePreview(context: Context, uri: Uri): Bitmap? {
        return decodeSampled(context, uri, PREVIEW_MAX_DIMENSION)
    }

    fun decodeFull(context: Context, uri: Uri): Bitmap? {
        return try {
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null
            applyExifRotation(context, uri, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampled(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val bounds = context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, options)
                options
            } ?: return null
            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0) return null
            var sampleSize = 1
            while (width / sampleSize > maxDimension || height / sampleSize > maxDimension) {
                sampleSize *= 2
            }
            val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return null
            applyExifRotation(context, uri, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return bitmap
        val matrix = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        return rotated
    }
}
