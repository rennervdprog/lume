package app.lume.imaging

import android.graphics.Bitmap
import android.graphics.Matrix
import app.lume.core.CropRect
import app.lume.core.Geometry
import app.lume.core.inscribedRectForRotation
import kotlin.math.roundToInt

/**
 * Aplica a [Geometry] sobre um bitmap: espelhos + giro + endireitar
 * numa matriz só, depois o corte. É o estágio de geometria do motor —
 * roda DEPOIS do estágio de cor (GL ou matriz), com a mesma receita
 * no preview e na exportação.
 *
 * Geometria identidade devolve o próprio [source] sem copiar. Qualquer
 * outro caso devolve um bitmap novo; o [source] nunca é reciclado aqui
 * (quem chamou é dono dele). Bitmaps intermediários são reciclados.
 */
object GeometryTransform {

    fun apply(source: Bitmap, geometry: Geometry): Bitmap {
        val g = geometry.clamped()
        if (g.isIdentity()) return source
        var working = source
        var owned = false
        val totalRotation = g.rotationDegrees + g.straightenDegrees
        if (totalRotation != 0f || g.flipHorizontal || g.flipVertical) {
            val matrix = Matrix()
            val cx = source.width / 2f
            val cy = source.height / 2f
            if (g.flipHorizontal || g.flipVertical) {
                matrix.postScale(
                    if (g.flipHorizontal) -1f else 1f,
                    if (g.flipVertical) -1f else 1f,
                    cx,
                    cy
                )
            }
            if (totalRotation != 0f) {
                matrix.postRotate(totalRotation, cx, cy)
            }
            working = Bitmap.createBitmap(
                source, 0, 0, source.width, source.height, matrix, true
            )
            owned = true
        }
        // Endireitar: corta o retângulo inscrito para não deixar cantos
        // vazios. O giro de 90° não entra aqui (não cria cantos vazios).
        if (g.straightenDegrees != 0f) {
            val inscribed = inscribedRectForRotation(
                working.width, working.height, g.straightenDegrees
            )
            val cropped = cropBitmap(working, inscribed)
            if (cropped !== working) {
                if (owned) working.recycle()
                working = cropped
                owned = true
            }
        }
        val crop = g.crop
        if (crop != null && !crop.isFull()) {
            val cropped = cropBitmap(working, crop)
            if (cropped !== working) {
                if (owned) working.recycle()
                working = cropped
                owned = true
            }
        }
        return working
    }

    private fun cropBitmap(source: Bitmap, rect: CropRect): Bitmap {
        val left = (rect.left * source.width).roundToInt()
            .coerceIn(0, source.width - 1)
        val top = (rect.top * source.height).roundToInt()
            .coerceIn(0, source.height - 1)
        val right = (rect.right * source.width).roundToInt()
            .coerceIn(left + 1, source.width)
        val bottom = (rect.bottom * source.height).roundToInt()
            .coerceIn(top + 1, source.height)
        if (left == 0 && top == 0 && right == source.width && bottom == source.height) {
            return source
        }
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }
}
