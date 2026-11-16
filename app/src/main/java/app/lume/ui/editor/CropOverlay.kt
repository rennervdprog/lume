package app.lume.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lume.core.CropEdge
import app.lume.core.CropRect
import app.lume.core.Geometry
import app.lume.core.adjusted
import app.lume.core.matchingRatio

/**
 * Moldura de corte sobre a foto: escurece fora do corte, desenha a
 * grade de terços e os cantos, e deixa arrastar as 8 alças + o meio.
 *
 * [bitmapWidth]/[bitmapHeight] são as dimensões do bitmap EXIBIDO
 * (foto girada, sem corte — o modo de prévia do Cortar) para a
 * moldura alinhar com a foto na tela. A matemática do arrasto vive
 * em [CropRect.adjusted] (pura, testada); aqui só mapeia pixels para
 * coordenadas normalizadas.
 */
@Composable
internal fun CropOverlay(
    bitmapWidth: Int,
    bitmapHeight: Int,
    geometry: Geometry,
    onCropChange: (CropRect) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val crop = geometry.crop ?: CropRect()
    // Valores frescos dentro do gesto: o pointerInput não pode
    // reiniciar no meio do arrasto, então lê o estado atualizado.
    val currentCrop by rememberUpdatedState(crop)
    val currentOnCropChange by rememberUpdatedState(onCropChange)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        if (bitmapWidth <= 0 || bitmapHeight <= 0) return@BoxWithConstraints
        val imageAspect = bitmapWidth.toFloat() / bitmapHeight
        // Retângulo da foto exibida (ContentScale.Fit).
        val fitW: Dp
        val fitH: Dp
        if (maxWidth / maxHeight > imageAspect) {
            fitH = maxHeight
            fitW = maxHeight * imageAspect
        } else {
            fitW = maxWidth
            fitH = maxWidth / imageAspect
        }
        val fitLeft = (maxWidth - fitW) / 2
        val fitTop = (maxHeight - fitH) / 2
        val frameLeft = fitLeft + fitW * crop.left
        val frameTop = fitTop + fitH * crop.top
        val frameW = fitW * crop.width
        val frameH = fitH * crop.height
        val frameRight = frameLeft + frameW
        val frameBottom = frameTop + frameH
        val frameCenterX = frameLeft + frameW / 2
        val frameCenterY = frameTop + frameH / 2
        // Trava de aspecto: a proporção pronta atual, ou livre.
        val aspectLock = matchingRatio(geometry.crop, bitmapWidth, bitmapHeight).aspect

        fun dragModifier(edge: CropEdge): Modifier =
            Modifier.pointerInput(edge, fitW, fitH, aspectLock, imageAspect) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val dx = with(density) { dragAmount.x.toDp() } / fitW
                    val dy = with(density) { dragAmount.y.toDp() } / fitH
                    currentOnCropChange(
                        currentCrop.adjusted(edge, dx, dy, aspectLock, imageAspect)
                    )
                }
            }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val leftPx = frameLeft.toPx()
            val topPx = frameTop.toPx()
            val widthPx = frameW.toPx()
            val heightPx = frameH.toPx()
            val rightPx = leftPx + widthPx
            val bottomPx = topPx + heightPx
            // Escurece fora do corte.
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, topPx))
            drawRect(
                dim,
                topLeft = Offset(0f, bottomPx),
                size = Size(size.width, size.height - bottomPx)
            )
            drawRect(
                dim,
                topLeft = Offset(0f, topPx),
                size = Size(leftPx, heightPx)
            )
            drawRect(
                dim,
                topLeft = Offset(rightPx, topPx),
                size = Size(size.width - rightPx, heightPx)
            )
            // Borda da moldura.
            drawRect(
                color = Color.White,
                topLeft = Offset(leftPx, topPx),
                size = Size(widthPx, heightPx),
                style = Stroke(width = 1.5.dp.toPx())
            )
            // Grade de terços.
            val third = Color.White.copy(alpha = 0.5f)
            val thirdStroke = 1.dp.toPx()
            for (i in 1..2) {
                val x = leftPx + widthPx * i / 3f
                drawLine(third, Offset(x, topPx), Offset(x, bottomPx), thirdStroke)
                val y = topPx + heightPx * i / 3f
                drawLine(third, Offset(leftPx, y), Offset(rightPx, y), thirdStroke)
            }
            // Cantos em L.
            val arm = 22.dp.toPx()
            val cornerStroke = 3.dp.toPx()
            val white = Color.White
            // Superior esquerdo.
            drawLine(white, Offset(leftPx, topPx + arm), Offset(leftPx, topPx), cornerStroke)
            drawLine(white, Offset(leftPx, topPx), Offset(leftPx + arm, topPx), cornerStroke)
            // Superior direito.
            drawLine(white, Offset(rightPx - arm, topPx), Offset(rightPx, topPx), cornerStroke)
            drawLine(white, Offset(rightPx, topPx), Offset(rightPx, topPx + arm), cornerStroke)
            // Inferior esquerdo.
            drawLine(white, Offset(leftPx, bottomPx - arm), Offset(leftPx, bottomPx), cornerStroke)
            drawLine(white, Offset(leftPx, bottomPx), Offset(leftPx + arm, bottomPx), cornerStroke)
            // Inferior direito.
            drawLine(white, Offset(rightPx - arm, bottomPx), Offset(rightPx, bottomPx), cornerStroke)
            drawLine(white, Offset(rightPx, bottomPx), Offset(rightPx, bottomPx - arm), cornerStroke)
        }

        // Meio da moldura: arrastar move o corte inteiro.
        Box(
            modifier = Modifier
                .offset(x = frameLeft, y = frameTop)
                .size(frameW, frameH)
                .then(dragModifier(CropEdge.MOVE))
        )

        // Alças: cantos + meios de lado. Área de toque maior que o desenho.
        val handle = 36.dp
        val half = handle / 2
        val handles = listOf(
            CropEdge.TOP_LEFT to (frameLeft to frameTop),
            CropEdge.TOP to (frameCenterX to frameTop),
            CropEdge.TOP_RIGHT to (frameRight to frameTop),
            CropEdge.LEFT to (frameLeft to frameCenterY),
            CropEdge.RIGHT to (frameRight to frameCenterY),
            CropEdge.BOTTOM_LEFT to (frameLeft to frameBottom),
            CropEdge.BOTTOM to (frameCenterX to frameBottom),
            CropEdge.BOTTOM_RIGHT to (frameRight to frameBottom)
        )
        handles.forEach { (edge, center) ->
            Box(
                modifier = Modifier
                    .offset(x = center.first - half, y = center.second - half)
                    .size(handle)
                    .then(dragModifier(edge))
            )
        }
    }
}
