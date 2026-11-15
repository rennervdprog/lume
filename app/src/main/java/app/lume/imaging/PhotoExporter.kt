package app.lume.imaging

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import app.lume.core.Recipe
import app.lume.core.RecipeColorMatrix
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Exportação em resolução total, sem marca d'água e sem limite — para
 * todo mundo, sem Pro (regra dura do estudo §4.4).
 *
 * Aplica a MESMA matriz de [RecipeColorMatrix] usada pelo preview: o
 * que o usuário vê é o que sai no arquivo. A renderização acontece fora
 * da thread de UI.
 *
 * TODO (Fase 1+): preservar EXIF (com opção de remover localização),
 * HEIC/PNG/TIFF (Pro), tiles para texturas acima do limite da GPU
 * quando o motor virar shader, e fila de lote com WorkManager.
 */
object PhotoExporter {

    const val JPEG_QUALITY: Int = 92

    /**
     * Renderiza [recipe] sobre a foto [photoUri] em resolução total e
     * salva o JPEG na galeria (Pasta "Lume"). Retorna a Uri do arquivo.
     */
    suspend fun exportJpeg(
        context: Context,
        photoUri: Uri,
        recipe: Recipe
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val source = BitmapLoader.decodeFull(context, photoUri)
                ?: return@withContext Result.failure(
                    IllegalStateException("Não foi possível abrir a foto original.")
                )
            val rendered = render(source, recipe)
            if (rendered !== source) {
                source.recycle()
            }
            val savedUri = saveToGallery(context, rendered)
            rendered.recycle()
            if (savedUri == null) {
                Result.failure(IllegalStateException("Não foi possível salvar o arquivo."))
            } else {
                Result.success(savedUri)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Renderiza a receita sobre [source] e devolve um bitmap novo. */
    fun render(source: Bitmap, recipe: Recipe): Bitmap {
        val values = RecipeColorMatrix.values(recipe)
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(values))
        }
        val canvas = Canvas(output)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun saveToGallery(context: Context, bitmap: Bitmap): Uri? {
        val fileName = buildFileName()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + "/Lume"
                )
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return try {
            var written = false
            resolver.openOutputStream(uri)?.use { stream ->
                written = bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
            }
            if (!written) {
                resolver.delete(uri, null, null)
                return null
            }
            uri
        } catch (e: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (ignored: Exception) {
                // Melhor esforço: a entrada parcial fica para o sistema limpar.
            }
            null
        }
    }

    private fun buildFileName(): String {
        // Nunca java.time (minSdk 24). SimpleDateFormat + catch (Throwable),
        // conforme a regra do projeto (java.time apagou tela em API 24/25).
        val stamp = try {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        } catch (t: Throwable) {
            System.currentTimeMillis().toString()
        }
        return "Lume_$stamp.jpg"
    }
}
