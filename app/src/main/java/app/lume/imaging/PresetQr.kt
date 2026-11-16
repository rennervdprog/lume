package app.lume.imaging

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Gera o QR code de um preset: o conteúdo é o MESMO JSON da
 * exportação/importação de presets (ver RecipeJson).
 *
 * Quem ler o QR com qualquer leitor recebe o JSON e pode importar
 * como arquivo na Biblioteca. Leitura de QR pela câmera é trabalho
 * futuro — esta versão só gera e compartilha o código.
 * Falha (conteúdo longo demais, por exemplo) retorna null.
 */
object PresetQr {

    fun createBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2
            )
            val matrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }
}
