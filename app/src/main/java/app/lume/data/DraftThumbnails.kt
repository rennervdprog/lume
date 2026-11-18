package app.lume.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Miniaturas dos rascunhos, guardadas em arquivo no armazenamento
 * privado do app.
 *
 * O rascunho guarda só a URI da foto, e a permissão que o seletor
 * de fotos do sistema dá é temporária: horas depois (ou após
 * reiniciar o aparelho) a URI já não abre mais, e o cartão do
 * rascunho ficava com o ícone genérico no lugar da foto. A
 * miniatura em arquivo não depende de permissão nenhuma: o cartão
 * mostra a foto sempre, mesmo com a URI vencida.
 *
 * A miniatura é só para o cartão — abrir o rascunho para editar
 * continua precisando da foto original (veja EditorViewModel).
 * Rascunhos salvos antes desta versão não têm miniatura: o cartão
 * deles mostra a foto de novo depois que o rascunho for salvo
 * outra vez.
 */
object DraftThumbnails {

    private const val DIR_NAME = "draft_thumbs"
    private const val MAX_DIMENSION = 480

    private fun fileFor(context: Context, photoUri: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(photoUri.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) } + ".jpg"
        val dir = File(context.filesDir, DIR_NAME)
        return File(dir, name)
    }

    /** Grava a miniatura (reduzida, JPEG). Falha = silêncio, nunca crash. */
    fun save(context: Context, photoUri: String, bitmap: Bitmap) {
        try {
            val file = fileFor(context, photoUri)
            file.parentFile?.mkdirs()
            val scaled = scaleDown(bitmap)
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            if (scaled !== bitmap) {
                scaled.recycle()
            }
        } catch (e: Exception) {
            // Miniatura é conveniência: falhar aqui nunca pode quebrar
            // a edição nem o salvamento do rascunho.
        }
    }

    /** Lê a miniatura, ou nulo se ela não existe (rascunho antigo). */
    fun load(context: Context, photoUri: String): Bitmap? {
        return try {
            val file = fileFor(context, photoUri)
            if (!file.exists()) {
                null
            } else {
                BitmapFactory.decodeFile(file.absolutePath)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Apaga a miniatura junto com o rascunho. Falha = silêncio. */
    fun delete(context: Context, photoUri: String) {
        try {
            fileFor(context, photoUri).delete()
        } catch (e: Exception) {
            // Arquivo órfão é inofensivo: ocupa pouco e nunca é lido
            // sem o rascunho correspondente.
        }
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val factor = MAX_DIMENSION.toFloat() / longest.toFloat()
        val width = (bitmap.width * factor).toInt().coerceAtLeast(1)
        val height = (bitmap.height * factor).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
