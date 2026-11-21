package app.lume.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.lume.core.CropEdge
import app.lume.core.CropRect
import app.lume.core.Geometry
import app.lume.core.adjusted
import app.lume.core.matchingRatio
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Projeção da soltura: velocidade × este tempo, para o encaixe. */
private const val SNAP_PROJECTION_SECONDS = 0.15f

/** Teto da velocidade de projeção, em larguras/alturas da foto por segundo. */
private const val MAX_SNAP_VELOCITY = 5f

/** Resistência do limite elástico: fração do excesso que vira visual. */
private const val OVERSHOOT_RESISTANCE = 0.35f

/** Janela de amostras do dedo para estimar a velocidade na soltura. */
private const val VELOCITY_WINDOW_MS = 100L

/**
 * Moldura de corte sobre a foto: escurece fora do corte, desenha a
 * grade de terços e os cantos, e deixa arrastar as 8 alças + o meio.
 *
 * A moldura anima com mola nas mudanças de proporção; as alças têm
 * área de toque de 48 dp (o desenho continua 36 dp); ao soltar, a
 * moldura encaixa na borda ou no centro conforme a projeção do
 * movimento; empurrar além da borda cede com resistência e volta
 * com mola; enquanto arrasta, o tamanho em pixels aparece acima do
 * dedo.
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
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val crop = geometry.crop ?: CropRect()
    // Valores frescos dentro do gesto: o pointerInput não pode
    // reiniciar no meio do arrasto, então lê o estado atualizado.
    val currentCrop by rememberUpdatedState(crop)
    val currentOnCropChange by rememberUpdatedState(onCropChange)
    // Excesso elástico além da borda, em Dp — só visual: o crop
    // enviado por onCropChange segue sempre clampado.
    val overshootAnim = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    // Posição do dedo em Dp, nas coordenadas do overlay; null = sem arrasto.
    val fingerPosState = remember { mutableStateOf<Offset?>(null) }
    var labelSize by remember { mutableStateOf(IntSize.Zero) }

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
        // Moldura com mola: os 4 lados animam em direção ao crop
        // recebido — a troca de proporção (e o endireitar com dois
        // dedos) desliza em vez de pular. Durante o arrasto o alvo
        // se move junto com o dedo, então a mola só acompanha.
        val animLeft by animateFloatAsState(
            targetValue = frameLeft.value, animationSpec = spring(), label = "frameLeft"
        )
        val animTop by animateFloatAsState(
            targetValue = frameTop.value, animationSpec = spring(), label = "frameTop"
        )
        val animRight by animateFloatAsState(
            targetValue = frameRight.value, animationSpec = spring(), label = "frameRight"
        )
        val animBottom by animateFloatAsState(
            targetValue = frameBottom.value, animationSpec = spring(), label = "frameBottom"
        )
        val animFrame = FrameDp(animLeft.dp, animTop.dp, animRight.dp, animBottom.dp)
        val currentFrame by rememberUpdatedState(animFrame)
        // Moldura exibida: animada com mola + excesso elástico
        // (aplicado DEPOIS da mola).
        val overshoot = overshootAnim.value
        val dispLeft = animFrame.left + overshoot.x.dp
        val dispTop = animFrame.top + overshoot.y.dp
        val dispRight = animFrame.right + overshoot.x.dp
        val dispBottom = animFrame.bottom + overshoot.y.dp
        val dispW = dispRight - dispLeft
        val dispH = dispBottom - dispTop
        val frameCenterX = dispLeft + dispW / 2
        val frameCenterY = dispTop + dispH / 2
        // Trava de aspecto: a proporção pronta atual, ou livre.
        val aspectLock = matchingRatio(geometry.crop, bitmapWidth, bitmapHeight).aspect

        // Área de toque das alças: 48 dp, centrada no mesmo ponto
        // do desenho de 36 dp.
        val handleTouch = 48.dp
        val halfTouch = handleTouch / 2

        // Canto superior esquerdo da caixa arrastável de cada lado,
        // em Dp, nas coordenadas do overlay.
        fun handleBoxOffset(edge: CropEdge, frame: FrameDp): Offset {
            val cx = frame.left + (frame.right - frame.left) / 2
            val cy = frame.top + (frame.bottom - frame.top) / 2
            return when (edge) {
                CropEdge.TOP_LEFT -> Offset(
                    (frame.left - halfTouch).value, (frame.top - halfTouch).value
                )
                CropEdge.TOP -> Offset(
                    (cx - halfTouch).value, (frame.top - halfTouch).value
                )
                CropEdge.TOP_RIGHT -> Offset(
                    (frame.right - halfTouch).value, (frame.top - halfTouch).value
                )
                CropEdge.LEFT -> Offset(
                    (frame.left - halfTouch).value, (cy - halfTouch).value
                )
                CropEdge.RIGHT -> Offset(
                    (frame.right - halfTouch).value, (cy - halfTouch).value
                )
                CropEdge.BOTTOM_LEFT -> Offset(
                    (frame.left - halfTouch).value, (frame.bottom - halfTouch).value
                )
                CropEdge.BOTTOM -> Offset(
                    (cx - halfTouch).value, (frame.bottom - halfTouch).value
                )
                CropEdge.BOTTOM_RIGHT -> Offset(
                    (frame.right - halfTouch).value, (frame.bottom - halfTouch).value
                )
                CropEdge.MOVE -> Offset(frame.left.value, frame.top.value)
            }
        }

        fun dragModifier(edge: CropEdge): Modifier =
            Modifier.pointerInput(edge, fitW, fitH, aspectLock, imageAspect) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startRect = currentCrop.value
                    var totalDx = 0f
                    var totalDy = 0f
                    // Últimas posições do dedo (tempo, posição
                    // normalizada na foto) para projetar a soltura.
                    val recent = ArrayDeque<Pair<Long, Offset>>()
                    fun record(change: PointerInputChange) {
                        val box = handleBoxOffset(edge, currentFrame.value)
                        val finger = Offset(
                            box.x + with(density) { change.position.x.toDp().value },
                            box.y + with(density) { change.position.y.toDp().value }
                        )
                        fingerPosState.value = finger
                        val norm = Offset(
                            (finger.x - fitLeft.value) / fitW.value,
                            (finger.y - fitTop.value) / fitH.value
                        )
                        recent.addLast(change.uptimeMillis to norm)
                        while (recent.size > 1 &&
                            recent.first().first < change.uptimeMillis - VELOCITY_WINDOW_MS
                        ) {
                            recent.removeFirst()
                        }
                        if (recent.size > 8) recent.removeFirst()
                    }
                    record(down)
                    var finished = false
                    while (!finished) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: continue
                        if (!change.pressed) {
                            finished = true
                        } else {
                            change.consume()
                            val dx = with(density) {
                                (change.position.x - change.previousPosition.x).toDp()
                            } / fitW
                            val dy = with(density) {
                                (change.position.y - change.previousPosition.y).toDp()
                            } / fitH
                            totalDx += dx
                            totalDy += dy
                            val clamped = currentCrop.value.adjusted(
                                edge, dx, dy, aspectLock, imageAspect
                            )
                            currentOnCropChange(clamped)
                            record(change)
                            // Limite elástico: o esperado sem clamp
                            // menos o clampado, com resistência — só
                            // o visual ultrapassa a borda.
                            val expected = startRect.moved(edge, totalDx, totalDy)
                            overshootAnim.snapTo(
                                overshootFor(edge, expected, clamped, fitW, fitH)
                            )
                        }
                    }
                    // Soltura: projeta o retângulo pela velocidade do
                    // dedo e encaixa na borda ou no centro, com vibração.
                    val velocity = recentVelocity(recent)
                    val rect = currentCrop.value
                    val projected = CropRect(
                        left = rect.left + velocity.x * SNAP_PROJECTION_SECONDS,
                        top = rect.top + velocity.y * SNAP_PROJECTION_SECONDS,
                        right = rect.right + velocity.x * SNAP_PROJECTION_SECONDS,
                        bottom = rect.bottom + velocity.y * SNAP_PROJECTION_SECONDS
                    )
                    val snapped = snapRectToEdges(projected)
                    if (snapped != projected.clamped()) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnCropChange(snapped)
                    }
                    fingerPosState.value = null
                    scope.launch { overshootAnim.animateTo(Offset.Zero, spring()) }
                }
            }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val leftPx = dispLeft.toPx()
            val topPx = dispTop.toPx()
            val widthPx = dispW.toPx()
            val heightPx = dispH.toPx()
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
                .offset(x = dispLeft, y = dispTop)
                .size(dispW, dispH)
                .then(dragModifier(CropEdge.MOVE))
        )

        // Alças: cantos + meios de lado. Área de toque maior que o desenho.
        val handles = listOf(
            CropEdge.TOP_LEFT to (dispLeft to dispTop),
            CropEdge.TOP to (frameCenterX to dispTop),
            CropEdge.TOP_RIGHT to (dispRight to dispTop),
            CropEdge.LEFT to (dispLeft to frameCenterY),
            CropEdge.RIGHT to (dispRight to frameCenterY),
            CropEdge.BOTTOM_LEFT to (dispLeft to dispBottom),
            CropEdge.BOTTOM to (frameCenterX to dispBottom),
            CropEdge.BOTTOM_RIGHT to (dispRight to dispBottom)
        )
        handles.forEach { (edge, center) ->
            Box(
                modifier = Modifier
                    .offset(x = center.first - halfTouch, y = center.second - halfTouch)
                    .size(handleTouch)
                    .then(dragModifier(edge))
            )
        }

        // Tamanho do corte acima do dedo enquanto arrasta.
        val finger = fingerPosState.value
        if (finger != null) {
            val pxW = (crop.width * bitmapWidth).roundToInt()
            val pxH = (crop.height * bitmapHeight).roundToInt()
            Text(
                text = "$pxW × $pxH",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .onSizeChanged { labelSize = it }
                    .offset {
                        val xPx = finger.x.dp.toPx() - labelSize.width / 2f
                        val yPx = finger.y.dp.toPx() - labelSize.height - 8.dp.toPx()
                        IntOffset(
                            xPx.roundToInt().coerceIn(
                                0,
                                (maxWidth.toPx() - labelSize.width)
                                    .roundToInt().coerceAtLeast(0)
                            ),
                            yPx.roundToInt().coerceAtLeast(0)
                        )
                    }
                    .background(
                        MaterialTheme.colorScheme.inverseSurface,
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Moldura em Dp, nas coordenadas do overlay. */
private data class FrameDp(
    val left: Dp,
    val top: Dp,
    val right: Dp,
    val bottom: Dp
)

/**
 * Encaixa o retângulo na borda da foto (0 ou 1) ou no centro (0.5)
 * quando a borda ou o centro está a menos de [threshold] do alvo. O
 * retângulo desliza para o encaixe — preserva o tamanho — e o
 * resultado fica preso em 0..1. Puro (sem Android), testável.
 */
internal fun snapRectToEdges(rect: CropRect, threshold: Float = 0.03f): CropRect {
    val w = rect.width
    val h = rect.height
    if (w <= 0f || h <= 0f) return rect.clamped()
    var l = rect.left
    var r = rect.right
    val cx = (l + r) / 2f
    when {
        abs(l) < threshold -> {
            l = 0f
            r = w
        }
        abs(r - 1f) < threshold -> {
            l = 1f - w
            r = 1f
        }
        abs(cx - 0.5f) < threshold -> {
            l = 0.5f - w / 2f
            r = 0.5f + w / 2f
        }
    }
    var t = rect.top
    var b = rect.bottom
    val cy = (t + b) / 2f
    when {
        abs(t) < threshold -> {
            t = 0f
            b = h
        }
        abs(b - 1f) < threshold -> {
            t = 1f - h
            b = 1f
        }
        abs(cy - 0.5f) < threshold -> {
            t = 0.5f - h / 2f
            b = 0.5f + h / 2f
        }
    }
    return CropRect(l, t, r, b).clamped()
}

/** Mesmo deslocamento de [CropRect.adjusted], mas sem prender nas bordas. */
private fun CropRect.moved(edge: CropEdge, dx: Float, dy: Float): CropRect {
    var l = left
    var t = top
    var r = right
    var b = bottom
    when (edge) {
        CropEdge.LEFT -> l += dx
        CropEdge.RIGHT -> r += dx
        CropEdge.TOP -> t += dy
        CropEdge.BOTTOM -> b += dy
        CropEdge.TOP_LEFT -> {
            l += dx
            t += dy
        }
        CropEdge.TOP_RIGHT -> {
            r += dx
            t += dy
        }
        CropEdge.BOTTOM_LEFT -> {
            l += dx
            b += dy
        }
        CropEdge.BOTTOM_RIGHT -> {
            r += dx
            b += dy
        }
        CropEdge.MOVE -> {
            l += dx
            r += dx
            t += dy
            b += dy
        }
    }
    return CropRect(l, t, r, b)
}

/**
 * Excesso elástico em Dp: quanto o retângulo esperado (sem clamp)
 * passou do clampado, com resistência de [OVERSHOOT_RESISTANCE].
 * Só o lado arrastado contribui (no MOVE, o deslocamento inteiro).
 */
private fun overshootFor(
    edge: CropEdge,
    expected: CropRect,
    clamped: CropRect,
    fitW: Dp,
    fitH: Dp
): Offset {
    val oxNorm = when (edge) {
        CropEdge.LEFT, CropEdge.TOP_LEFT, CropEdge.BOTTOM_LEFT ->
            expected.left - clamped.left
        CropEdge.RIGHT, CropEdge.TOP_RIGHT, CropEdge.BOTTOM_RIGHT ->
            expected.right - clamped.right
        CropEdge.MOVE -> expected.left - clamped.left
        else -> 0f
    }
    val oyNorm = when (edge) {
        CropEdge.TOP, CropEdge.TOP_LEFT, CropEdge.TOP_RIGHT ->
            expected.top - clamped.top
        CropEdge.BOTTOM, CropEdge.BOTTOM_LEFT, CropEdge.BOTTOM_RIGHT ->
            expected.bottom - clamped.bottom
        CropEdge.MOVE -> expected.top - clamped.top
        else -> 0f
    }
    return Offset(
        oxNorm * fitW.value * OVERSHOOT_RESISTANCE,
        oyNorm * fitH.value * OVERSHOOT_RESISTANCE
    )
}

/**
 * Velocidade do dedo em unidades normalizadas por segundo, a partir
 * das amostras recentes (tempo, posição). Zero sem amostras suficientes.
 */
private fun recentVelocity(recent: List<Pair<Long, Offset>>): Offset {
    if (recent.size < 2) return Offset.Zero
    val (firstTime, firstPos) = recent.first()
    val (lastTime, lastPos) = recent.last()
    val dtMs = (lastTime - firstTime).toFloat()
    if (dtMs <= 0f) return Offset.Zero
    val vx = (lastPos.x - firstPos.x) / dtMs * 1000f
    val vy = (lastPos.y - firstPos.y) / dtMs * 1000f
    return Offset(
        vx.coerceIn(-MAX_SNAP_VELOCITY, MAX_SNAP_VELOCITY),
        vy.coerceIn(-MAX_SNAP_VELOCITY, MAX_SNAP_VELOCITY)
    )
}
