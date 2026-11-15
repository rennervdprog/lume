package app.lume.imaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat

/** Compartilhamento via share sheet do sistema. Sem SDK de rede social. */
object ShareHelper {

    fun shareImage(context: Context, imageUri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartilhar foto")
        ContextCompat.startActivity(context, chooser, null)
    }

    fun shareText(context: Context, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, title)
        ContextCompat.startActivity(context, chooser, null)
    }
}
