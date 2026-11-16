package app.lume.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.lume.ui.theme.LumeSpacing
import kotlin.math.roundToInt

/**
 * Slider fino desenhado à mão: trilho de 4.dp, polegar branco de
 * 22.dp, sem caixa de valor. Um gesto só cobre toque e arrasto:
 * o dedo desce e o valor segue o dedo até soltar, quando
 * [onValueChangeFinished] confirma (entra no desfazer).
 *
 * O Slider do Material 3 não permite afinar o trilho nem o polegar
 * sem reescrever o trilho inteiro — por isso o controle é próprio.
 * A semântica de acessibilidade do Slider padrão não existe aqui:
 * o controle responde a toque e arrasto, que é o gesto do editor.
 */
@Composable
internal fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    BoxWithConstraints(modifier = modifier.height(36.dp)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbPx = with(density) { 22.dp.toPx() }
        fun valueFromX(x: Float): Float {
            val fraction = (x / widthPx).coerceIn(0f, 1f)
            return valueRange.start +
                fraction * (valueRange.endInclusive - valueRange.start)
        }
        val span = valueRange.endInclusive - valueRange.start
        val fraction = if (span > 0f) {
            ((value - valueRange.start) / span).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("slim_slider")
                .pointerInput(valueRange, widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        latestOnValueChange(valueFromX(down.position.x))
                        var finished = false
                        while (!finished) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                finished = true
                            } else if (change.position != change.previousPosition) {
                                latestOnValueChange(valueFromX(change.position.x))
                            }
                            change.consume()
                        }
                        latestOnFinished()
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((widthPx * fraction - thumbPx / 2f).roundToInt(), 0)
                    }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        1.dp,
                        Color.Black.copy(alpha = 0.15f),
                        CircleShape
                    )
            )
        }
    }
}

/**
 * Linha do slider fino: rótulo, slider e valor com sinal. Toque
 * duplo no rótulo zera o controle (quando [onReset] existe).
 */
@Composable
internal fun SlimSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    formatValue: (Float) -> String,
    onReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = 118.dp)
                .pointerInput(onReset) {
                    detectTapGestures(onDoubleTap = { onReset?.invoke() })
                }
        )
        SlimSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatValue(value),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 44.dp)
        )
    }
}

/**
 * Fileira de sub-ferramentas rolável (só texto): a selecionada ganha
 * a cor primária e um fundo suave. [trailing] é um conteúdo fixo à
 * direita (ex.: o segmento Simples/Pro), fora da rolagem.
 */
@Composable
internal fun SubToolStrip(
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f).testTag("sub_strip"),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(options, key = { it.first }) { (id, label) ->
                val selected = id == selectedId
                Box(
                    modifier = Modifier
                        .testTag("sub_$id")
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { onSelect(id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.padding(start = LumeSpacing.xs)) {
                trailing()
            }
        }
    }
}

/** Tirinha de histograma (valores 0..1, já normalizados), sem caixa. */
@Composable
internal fun HistogramStrip(values: List<Float>, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
    ) {
        if (values.isEmpty()) return@Canvas
        val barWidth = size.width / values.size
        val padding = 2.dp.toPx()
        values.forEachIndexed { index, value ->
            val barHeight = (size.height - padding * 2) * value.coerceIn(0f, 1f)
            drawRect(
                color = barColor.copy(alpha = 0.75f),
                topLeft = Offset(
                    index * barWidth + 0.5f,
                    size.height - padding - barHeight
                ),
                size = Size((barWidth - 1f).coerceAtLeast(1f), barHeight)
            )
        }
    }
}
