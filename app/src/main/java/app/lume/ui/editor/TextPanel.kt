package app.lume.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lume.core.TextFont
import app.lume.core.TextOverlay
import app.lume.ui.theme.LumeSpacing
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Painel Texto (compacto, 0.15.6): campo com foco automático e ✕
 * para limpar, cartões de fonte com o começo do texto do usuário
 * (o nome aparece só na selecionada; a fonte do centro aplica ao
 * rolar a fileira), tamanho (3..20% da largura da foto) e cor em
 * bolinhas de 48 dp (segurar mostra o hex, pinta ao vivo e copia
 * ao soltar dentro; soltar fora cancela). O texto aparece sobre a
 * foto e sai na exportação; arrastar pela alça na foto move o
 * texto e a pinça muda o tamanho.
 */
@Composable
internal fun TextPanel(
    overlay: TextOverlay,
    paletteColors: List<Int>,
    onTextChange: (String) -> Unit,
    onFontChange: (TextFont) -> Unit,
    onSizeChange: (Float) -> Unit,
    onColorChange: (Int) -> Unit,
    onColorHoldCopy: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val fontListState = rememberLazyListState()
    val selectedFontIndex = TextFont.entries.indexOf(overlay.font).coerceAtLeast(0)
    var programmaticScroll by remember { mutableStateOf(false) }
    LaunchedEffect(overlay.font) {
        val visible = fontListState.layoutInfo.visibleItemsInfo
        if (visible.none { it.index == selectedFontIndex }) {
            programmaticScroll = true
            try {
                fontListState.animateScrollToItem(selectedFontIndex)
            } finally {
                programmaticScroll = false
            }
        }
    }
    val latestOverlay by rememberUpdatedState(overlay)
    val latestOnFontChange by rememberUpdatedState(onFontChange)
    LaunchedEffect(fontListState) {
        snapshotFlow {
            if (programmaticScroll || !fontListState.isScrollInProgress) {
                null
            } else {
                val info = fontListState.layoutInfo
                val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
                info.visibleItemsInfo.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }?.index
            }
        }
            .distinctUntilChanged()
            .collectLatest { centerIndex ->
                if (centerIndex == null) return@collectLatest
                val current = latestOverlay
                val centerFont = TextFont.entries.getOrNull(centerIndex) ?: return@collectLatest
                if (centerFont != current.font && current.text.isNotBlank()) {
                    latestOnFontChange(centerFont)
                }
            }
    }

    var heldColorArgb by remember { mutableStateOf<Int?>(null) }
    val latestOnColorChange by rememberUpdatedState(onColorChange)
    val latestOnColorHoldCopy by rememberUpdatedState(onColorHoldCopy)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
    ) {
        OutlinedTextField(
            value = overlay.text,
            onValueChange = onTextChange,
            label = { Text("Texto na foto") },
            placeholder = { Text("Escreva aqui") },
            singleLine = true,
            trailingIcon = {
                if (overlay.text.isNotEmpty()) {
                    IconButton(
                        onClick = { onTextChange("") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Limpar texto"
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
        LazyRow(
            state = fontListState,
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
        ) {
            items(TextFont.entries) { font ->
                val selected = font == overlay.font
                val previewText = overlay.text.take(12).ifBlank { "Aa" }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onFontChange(font) }
                        .padding(horizontal = LumeSpacing.sm, vertical = LumeSpacing.xs)
                ) {
                    Text(
                        text = previewText,
                        fontFamily = font.fontFamily(),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (selected) {
                        Text(
                            text = font.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        SlimSliderRow(
            caption = "Tamanho",
            value = overlay.sizeFraction * 100f,
            valueRange = TextOverlay.MIN_SIZE_FRACTION * 100f..
                TextOverlay.MAX_SIZE_FRACTION * 100f,
            onValueChange = { onSizeChange((it / 100f).coerceIn(
                TextOverlay.MIN_SIZE_FRACTION,
                TextOverlay.MAX_SIZE_FRACTION
            )) },
            onValueChangeFinished = {},
            formatValue = { "${it.roundToInt()}%" },
            onReset = { onSizeChange(TextOverlay.DEFAULT_SIZE_FRACTION) }
        )
        Text(
            text = "Cor — da foto",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        val heldHex = heldColorArgb
        if (heldHex != null) {
            Text(
                text = hexString(heldHex),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
        ) {
            val colors = paletteColors + listOf(
                TextOverlay.DEFAULT_COLOR_ARGB,
                0xFF000000.toInt()
            )
            items(colors) { argb ->
                val selected = argb == overlay.colorArgb
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) Color.White else Color.Transparent
                        )
                        .padding(if (selected) 2.dp else 0.dp)
                        .clip(CircleShape)
                        .background(Color(argb))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape
                        )
                        .pointerInput(argb) {
                            val boxSize = size
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val previousColor = latestOverlay.colorArgb
                                heldColorArgb = argb
                                latestOnColorChange(argb)
                                var releasedInside = false
                                var done = false
                                while (!done) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    val position = change.position
                                    if (!change.pressed) {
                                        releasedInside = position.x in 0f..boxSize.width.toFloat() &&
                                            position.y in 0f..boxSize.height.toFloat()
                                        done = true
                                    } else if (position.x < 0f || position.y < 0f ||
                                        position.x > boxSize.width ||
                                        position.y > boxSize.height
                                    ) {
                                        releasedInside = false
                                        done = true
                                    }
                                    change.consume()
                                }
                                heldColorArgb = null
                                if (releasedInside) {
                                    latestOnColorHoldCopy(argb)
                                } else {
                                    latestOnColorChange(previousColor)
                                }
                            }
                        }
                )
            }
        }
        Text(
            text = "Arraste o texto pela alça para mover. Pinça muda o tamanho.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
