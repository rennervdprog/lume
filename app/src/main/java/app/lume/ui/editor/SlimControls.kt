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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.lume.ui.theme.LumeSpacing
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Resistência progressiva fora do intervalo: dentro do intervalo
 * devolve o valor; fora, o excesso encolhe para 35% (o polegar, a
 * moldura ou o que for acompanha cada vez menos o dedo passado o
 * limite). Reuso dos outros painéis do editor (ex.: CropPanel).
 */
internal fun rubberBand(value: Float, min: Float, max: Float): Float {
    return when {
        value < min -> min + (value - min) * 0.35f
        value > max -> max + (value - max) * 0.35f
        else -> value
    }
}

/**
 * Projeção do valor por inércia: onde o gesto estaria [millis]
 * depois da soltura, na velocidade atual. [current] é a fração
 * (0..1) e [velocityPxPerSec] é px/s no eixo do gesto.
 */
internal fun projectedValue(
    current: Float,
    velocityPxPerSec: Float,
    widthPx: Float,
    millis: Float = 150f
): Float {
    if (widthPx <= 0f) return current
    return current + velocityPxPerSec * (millis / 1000f) / widthPx
}

/**
 * Toque para confirmar, com opção de segurar: com
 * [requireHold] = false, um toque simples confirma; com true, é
 * preciso segurar [durationMs] — soltar antes cancela. Usado pelo
 * botão X do EditorScreen, pelo "Limpar tudo" do HistoryPanel e
 * pelo girar do CropPanel.
 */
internal fun Modifier.holdToConfirm(
    durationMs: Long = 500L,
    requireHold: Boolean,
    onConfirm: () -> Unit
): Modifier {
    return pointerInput(requireHold, durationMs, onConfirm) {
        detectTapGestures(
            onTap = {
                if (!requireHold) onConfirm()
            },
            onPress = {
                if (requireHold) {
                    val released = withTimeoutOrNull(durationMs) {
                        awaitRelease()
                    }
                    // null = timeout (segurou o tempo todo): confirma.
                    // Soltou antes = awaitRelease retornou: cancela.
                    if (released == null) onConfirm()
                }
            }
        )
    }
}

/**
 * Slider fino desenhado à mão: trilho de 4.dp, polegar branco de
 * 22.dp (28.dp enquanto arrasta), sem caixa de valor. Um gesto só
 * cobre toque e arrasto: o dedo desce e o valor segue o dedo até
 * soltar, quando [onValueChangeFinished] confirma (entra no
 * desfazer).
 *
 * Afinar: descer o dedo na vertical durante o arrasto reduz a
 * sensibilidade horizontal até 1:10. Ímã no zero: na soltura, se a
 * projeção do movimento cair perto do zero, o valor encaixa em
 * zero com vibração curta. Passado o limite, o valor enviado fica
 * clampado, o polegar fica no limite e um círculo tracejado
 * acompanha a posição crua do dedo. Duplo toque chama
 * [onDoubleTap] sem mudar o valor. A bolha de valor é desenhada
 * por SlimSliderRow, que detém o formatValue — [showBubble] só
 * espelha a decisão aqui para não haver bolha dupla quando a
 * coluna de valor está ativa.
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
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDoubleTap: (() -> Unit)? = null,
    fillColor: Color = MaterialTheme.colorScheme.primary,
    showBubble: Boolean = true
) {
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val haptic = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var ghostX by remember { mutableStateOf<Float?>(null) }
    BoxWithConstraints(modifier = modifier.height(36.dp)) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val thumbSizeDp = if (isDragging) 28.dp else 22.dp
        val thumbPx = with(density) { thumbSizeDp.toPx() }
        val span = valueRange.endInclusive - valueRange.start
        fun valueFromX(x: Float): Float {
            val fraction = (x / widthPx).coerceIn(0f, 1f)
            return valueRange.start +
                fraction * span
        }
        fun valueFromFraction(fraction: Float): Float {
            return valueRange.start + fraction * span
        }
        val fraction = if (span > 0f) {
            ((value - valueRange.start) / span).coerceIn(0f, 1f)
        } else {
            0f
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (enabled) 1f else 0.4f)
                .testTag("slim_slider")
                .pointerInput(valueRange, widthPx, enabled) {
                    if (!enabled) return@pointerInput
                    var lastDownTime = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = down.uptimeMillis
                        val isDoubleTap = latestOnDoubleTap != null &&
                            downTime - lastDownTime < 300L
                        lastDownTime = downTime
                        if (isDoubleTap) {
                            latestOnDoubleTap?.invoke()
                            lastDownTime = 0L
                            // Espera soltar sem mexer no valor.
                            var up = false
                            while (!up) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                change.consume()
                                if (!change.pressed) up = true
                            }
                        } else {
                            isDragging = true
                            ghostX = null
                            var effectiveX = down.position.x
                            val startY = down.position.y
                            val recent = mutableListOf<Pair<Long, Float>>()
                            recent.add(downTime to down.position.x)
                            latestOnValueChange(valueFromX(down.position.x))
                            var lastFraction =
                                (down.position.x / widthPx).coerceIn(0f, 1f)
                            var finished = false
                            while (!finished) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) {
                                    finished = true
                                } else if (change.position != change.previousPosition) {
                                    val dx = change.position.x - change.previousPosition.x
                                    val dyDown = (change.position.y - startY)
                                        .coerceAtLeast(0f)
                                    val sensitivity =
                                        1f / (1f + (dyDown / 80f).coerceIn(0f, 9f))
                                    effectiveX += dx * sensitivity
                                    val rawFraction = effectiveX / widthPx
                                    lastFraction = rawFraction.coerceIn(0f, 1f)
                                    latestOnValueChange(
                                        valueFromFraction(lastFraction)
                                    )
                                    ghostX = if (rawFraction < 0f || rawFraction > 1f) {
                                        change.position.x
                                    } else {
                                        null
                                    }
                                    val time = change.uptimeMillis
                                    recent.add(time to change.position.x)
                                    while (recent.size > 1 &&
                                        recent.first().first < time - 100L
                                    ) {
                                        recent.removeAt(0)
                                    }
                                }
                                change.consume()
                            }
                            isDragging = false
                            ghostX = null
                            // Ímã no zero: projeta o movimento e
                            // encaixa se a projeção cair perto do zero.
                            val velocityPxPerSec = if (recent.size >= 2) {
                                val (firstTime, firstX) = recent.first()
                                val (lastTime, lastX) = recent.last()
                                val dtMs = (lastTime - firstTime).toFloat()
                                if (dtMs > 0f) {
                                    (lastX - firstX) / dtMs * 1000f
                                } else {
                                    0f
                                }
                            } else {
                                0f
                            }
                            val projected = projectedValue(
                                lastFraction, velocityPxPerSec, widthPx
                            )
                            val zeroFraction = if (span > 0f &&
                                valueRange.start <= 0f &&
                                0f <= valueRange.endInclusive
                            ) {
                                (0f - valueRange.start) / span
                            } else {
                                null
                            }
                            if (zeroFraction != null &&
                                abs(projected - zeroFraction) <= 0.05f
                            ) {
                                latestOnValueChange(0f)
                                haptic.performHapticFeedback(
                                    HapticFeedbackType.TextHandleMove
                                )
                            }
                            latestOnFinished()
                        }
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
                        .background(fillColor)
                )
            }
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset((widthPx * fraction - thumbPx / 2f).roundToInt(), 0)
                    }
                    .size(thumbSizeDp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(
                        1.dp,
                        Color.Black.copy(alpha = 0.15f),
                        CircleShape
                    )
            )
            val ghost = ghostX
            if (ghost != null) {
                Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    drawCircle(
                        color = fillColor,
                        radius = 14.dp.toPx(),
                        center = Offset(
                            ghost.coerceIn(0f, size.width),
                            size.height / 2f
                        ),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(6f, 4f), 0f
                            )
                        )
                    )
                }
            }
        }
    }
}

/**
 * Linha do slider fino (v4): botão de zerar à esquerda, slider com
 * a bolha do valor acima do polegar, botão de comparar à direita
 * (segurar mostra a foto original). [caption] é o nome do controle
 * quando ele não está na tira de sub-ferramentas (ex.: HSL,
 * Intensidade, Endireitar). O gesto escondido de antes — toque
 * duplo no rótulo para zerar — não existe mais.
 */
@Composable
internal fun SlimSliderRow(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    formatValue: (Float) -> String,
    caption: String? = null,
    onReset: (() -> Unit)? = null,
    onCompareChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDoubleTap: (() -> Unit)? = null,
    showValueColumn: Boolean = false,
    fillColor: Color = MaterialTheme.colorScheme.primary
) {
    // A bolha acima do polegar some quando a coluna de valor está
    // ativa; o flag desce para o SlimSlider junto.
    val showBubble = !showValueColumn
    val latestEnabled by rememberUpdatedState(enabled)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
        ) {
            if (onReset != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(if (enabled) 1f else 0.4f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(enabled = enabled, onClick = onReset)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Zerar",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .height(if (showBubble) 54.dp else 36.dp)
            ) {
                val density = LocalDensity.current
                val widthPx = with(density) { maxWidth.toPx() }
                val span = valueRange.endInclusive - valueRange.start
                val fraction = if (span > 0f) {
                    ((value - valueRange.start) / span).coerceIn(0f, 1f)
                } else {
                    0f
                }
                var bubbleWidthPx by remember { mutableIntStateOf(0) }
                if (showBubble) {
                    Box(
                        modifier = Modifier
                            .offset {
                                val maxX = (widthPx - bubbleWidthPx).coerceAtLeast(0f)
                                IntOffset(
                                    (widthPx * fraction - bubbleWidthPx / 2f)
                                        .coerceIn(0f, maxX).roundToInt(),
                                    0
                                )
                            }
                            .onGloballyPositioned { bubbleWidthPx = it.size.width }
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = formatValue(value),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.Black,
                            maxLines = 1
                        )
                    }
                }
                SlimSlider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    onValueChangeFinished = onValueChangeFinished,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    enabled = enabled,
                    onDoubleTap = onDoubleTap,
                    fillColor = fillColor,
                    showBubble = showBubble
                )
            }
            if (showValueColumn) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(48.dp)
                        .alpha(if (enabled) 1f else 0.4f)
                )
            }
            if (onCompareChange != null) {
                val latestOnCompareChange by rememberUpdatedState(onCompareChange)
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .alpha(if (enabled) 1f else 0.4f)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    if (latestEnabled) {
                                        latestOnCompareChange(true)
                                        try {
                                            awaitRelease()
                                        } finally {
                                            latestOnCompareChange(false)
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Compare,
                        contentDescription = "Segure para comparar",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

/** Uma sub-ferramenta do trilho de ícones: id, rótulo e ícone. */
internal data class IconSubOption(
    val id: String,
    val label: String,
    val icon: ImageVector,
    /**
     * Recurso Pro no plano grátis: o item fica visível com o selo ★
     * e o toque abre o paywall ([onLockedSelect]) em vez de selecionar.
     */
    val sealed: Boolean = false,
    /**
     * Tem Pro dentro, mas o item em si é usável no plano grátis:
     * mostra o selo ★ e o toque seleciona normalmente — a trava
     * mora dentro da seção (ex.: HSL: 4 canais grátis, 4 Pro).
     * Nunca combine com [sealed]: selo que trava já é [sealed].
     */
    val proInside: Boolean = false
)

/**
 * Selo Pro (preview do selo, 19/11): círculo azul de 18dp com uma
 * estrela branca, anel branco de 2dp para ler sobre qualquer cor.
 * Vai no canto superior direito do item trancado. No trilho de
 * ícones o selo usa 14dp: com 18dp ele cobria o quadrante superior
 * direito do ícone de 24dp e o HSL lia como cortado (o ponto de
 * cima e o arco do anel sumiam atrás do selo) — o brilho dentro
 * escala junto (61% do tamanho).
 */
@Composable
internal fun ProSealBadge(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    contentDescription: String = "Pro"
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .border(2.dp, Color.White, CircleShape)
    ) {
        Icon(
            imageVector = SolarizeIcons.ProSparkle,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(size * 0.61f)
        )
    }
}

/**
 * Trilho de sub-ferramentas com ícones (v4): ícone + rótulo, a
 * selecionada ganha a cor primária e um ponto azul embaixo. A tira
 * rola até a selecionada sozinha (atalhos da Home que caem no fim
 * do trilho, como HSL e Curva). A tag da tira é "sub_strip" e a
 * de cada item é "sub_<id>". Item com selo ([IconSubOption.sealed])
 * mostra o selo ★ e o toque abre o paywall ([onLockedSelect]) em
 * vez de selecionar — o recurso fica visível, nunca escondido.
 * Item com Pro dentro ([IconSubOption.proInside]) também mostra o
 * selo ★, mas o toque seleciona normalmente.
 */
@Composable
internal fun IconSubRail(
    options: List<IconSubOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    onLockedSelect: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val selectedIndex = options.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth()
    ) {
        val density = LocalDensity.current
        val viewportWidthPx = with(density) { maxWidth.toPx() }
        val itemWidthPx = with(density) { 64.dp.toPx() }
        LaunchedEffect(selectedId, viewportWidthPx) {
            // Espera o dedo soltar antes de rolar: a rolagem
            // automática não briga com o gesto do usuário. A
            // selecionada para no centro, com mola — não no início.
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            val centerOffset = -((viewportWidthPx - itemWidthPx) / 2f).roundToInt()
            listState.animateScrollToItem(selectedIndex, scrollOffset = centerOffset)
        }
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sub_strip"),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(options, key = { it.id }) { option ->
                val selected = option.id == selectedId
                val tint = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .testTag("sub_${option.id}")
                        .width(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            if (option.sealed && onLockedSelect != null) {
                                onLockedSelect(option.id)
                            } else {
                                onSelect(option.id)
                            }
                        }
                        // Topo com 10dp (não 6dp): o selo de 14dp sai 9dp
                        // para cima do ícone e o clip da coluna cortaria
                        // 3dp dele com o padding antigo.
                        .padding(top = 10.dp, bottom = 6.dp)
                ) {
                    Box(contentAlignment = Alignment.TopEnd) {
                        Icon(
                            imageVector = option.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp)
                        )
                        if (option.sealed || option.proInside) {
                            // Selo de 14dp no cantinho (offset 9/-9): com
                            // 18dp e offset 6/-6 ele cobria o quadrante
                            // superior direito do ícone — no HSL o ponto
                            // e o arco do anel sumiam e o ícone lia como
                            // cortado. Aqui ele só toca o canto.
                            ProSealBadge(
                                size = 14.dp,
                                modifier = Modifier.offset(x = 9.dp, y = (-9).dp)
                            )
                        }
                    }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        ),
                        color = tint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                }
                            )
                    )
                }
            }
        }
    }
}

/**
 * Uma aba de texto: texto puro, sem pílula. A selecionada leva o
 * traço azul de 2dp; a não selecionada reserva o mesmo espaço com
 * o traço ausente, para a fileira nunca pular. Usada nas
 * categorias de filtros e nas abas do Cortar.
 *
 * O traço é DESENHADO (drawBehind), nunca um filho com
 * fillMaxWidth: um filho assim, medido contra as restrições do
 * pai, alarga a aba até a largura da fileira e empurra as outras
 * abas para fora da tela — foi o que quebrou o teste do Cortar
 * nas runs #22 e #23, nas duas formas (Column e Box).
 */
@Composable
internal fun TextTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            ),
            color = if (selected) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            modifier = Modifier
                .drawBehind {
                    if (selected) {
                        val line = 2.dp.toPx()
                        drawRect(
                            color = primary,
                            topLeft = Offset(0f, size.height - line),
                            size = Size(size.width, line)
                        )
                    }
                }
                .padding(bottom = 6.dp)
        )
    }
}

/** Tirinha de histograma (valores 0..1, já normalizados), sem caixa. */
@Composable
internal fun HistogramStrip(
    values: List<Float>,
    modifier: Modifier = Modifier,
    rgbValues: List<List<Float>> = emptyList(),
    rgb: Boolean = false
) {
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant
    // Cores de dados (não de marca): um conjunto por canal.
    val rgbColors = listOf(
        Color(0xFFF44336),
        Color(0xFF4CAF50),
        Color(0xFF2196F3)
    )
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
    ) {
        if (rgb && rgbValues.size >= 3) {
            rgbValues.take(3).forEachIndexed { channelIndex, channel ->
                if (channel.isEmpty()) return@forEachIndexed
                val barWidth = size.width / channel.size
                val padding = 2.dp.toPx()
                channel.forEachIndexed { index, value ->
                    val barHeight = (size.height - padding * 2) * value.coerceIn(0f, 1f)
                    drawRect(
                        color = rgbColors[channelIndex].copy(alpha = 0.55f),
                        topLeft = Offset(
                            index * barWidth + 0.5f,
                            size.height - padding - barHeight
                        ),
                        size = Size((barWidth - 1f).coerceAtLeast(1f), barHeight)
                    )
                }
            }
            return@Canvas
        }
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
