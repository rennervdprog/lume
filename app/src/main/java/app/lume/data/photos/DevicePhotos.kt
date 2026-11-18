package app.lume.data.photos

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.LruCache
import app.lume.imaging.BitmapLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Uma foto da galeria do aparelho, lida pela MediaStore. */
data class DevicePhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAddedSeconds: Long
)

/**
 * Fotos do aparelho, pela MediaStore. Só funciona com a permissão
 * de fotos concedida (total ou parcial): sem ela, a consulta volta
 * vazia — o chamador mostra o estado de permissão, nunca uma grade
 * vazia fingindo que não há fotos.
 *
 * Sem permissão nenhuma, o caminho continua sendo o seletor de
 * fotos do sistema (Photo Picker), que não precisa de permissão.
 */
object DevicePhotos {

    /** Quantidade máxima de fotos listadas na grade (as mais recentes). */
    private const val LIMIT = 200

    /** Miniaturas em memória: evita redecodificar ao rolar a grade. */
    private val thumbCache = LruCache<Long, Bitmap>(40)

    suspend fun query(context: Context): List<DevicePhoto> {
        return withContext(Dispatchers.IO) {
            try {
                queryInternal(context)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    private fun queryInternal(context: Context): List<DevicePhoto> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )
        val photos = ArrayList<DevicePhoto>(LIMIT)
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext() && photos.size < LIMIT) {
                val id = cursor.getLong(idColumn)
                photos.add(
                    DevicePhoto(
                        id = id,
                        uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        ),
                        displayName = cursor.getString(nameColumn).orEmpty(),
                        dateAddedSeconds = cursor.getLong(dateColumn)
                    )
                )
            }
        }
        return photos
    }

    /** Miniatura da foto (cache em memória + decodificação reduzida). */
    suspend fun thumbnail(context: Context, photo: DevicePhoto): Bitmap? {
        synchronized(thumbCache) {
            thumbCache.get(photo.id)?.let { return it }
        }
        val bitmap = withContext(Dispatchers.IO) {
            BitmapLoader.decodeThumbnail(context, photo.uri)
        } ?: return null
        synchronized(thumbCache) {
            thumbCache.put(photo.id, bitmap)
        }
        return bitmap
    }
}
