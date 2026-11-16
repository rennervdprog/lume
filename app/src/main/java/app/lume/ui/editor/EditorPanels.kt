package app.lume.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lume.core.HslChannel
import app.lume.core.HslChannels
import app.lume.core.Geometry
import app.lume.core.Preset
import app.lume.core.ProPolicy
import app.lume.core.Recipe
import app.lume.core.RecipeColorMatrix
import app.lume.core.ToneCurve
import app.lume.core.ToneCurveLut
import app.lume.ui.components.PresetSwatch
import app.lume.ui.theme.LumeSpacing
import kotlin.math.roundToInt

/** Formata valores de slider com sinal: "+12", "-5", "0". */
internal fun formatSigned(value: Float): String {
    val rounded = value.roundToInt()
    return when {
        rounded > 0 -> "+$rounded"
        rounded < 0 -> "$rounded"
        else -> "0"
    }
}

/** Formata exposição com uma casa decimal: "+0.3", "-0.4", "0.0". */
internal fun formatExposure(value: Float): String {
    val text = String.format("%.1f", value)
    return if (value > 0f) "+$text" else text
}

enum class EditorTool {
    LIGHT, COLOR, EFFECTS, CROP, PRESETS, HISTORY
}

/** Trilho de ferramentas: ícone + rótulo, uma ferramenta por vez. */
@Composable
internal fun EditorToolRail(
    selected: EditorTool,
    onSelect: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        Triple(EditorTool.LIGHT, "Luz", Icons.Filled.WbSunny),
        Triple(EditorTool.COLOR, "Cor", Icons.Filled.Palette),
        Triple(EditorTool.EFFECTS, "Efeitos", Icons.Filled.AutoFixHigh),
        Triple(EditorTool.CROP, "Cortar", Icons.Filled.Crop),
        Triple(EditorTool.PRESETS, "Presets", Icons.Filled.Style),
        Triple(EditorTool.HISTORY, "Histórico", Icons.Filled.History)
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.xs, vertical = LumeSpacing.xxs),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tools.forEach { (tool, label, icon) ->
            val isSelected = tool == selected
            val tint = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(tool) }
                    .padding(horizontal = LumeSpacing.xs, vertical = LumeSpacing.xs)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = tint
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * Linha X/✓ no fim do painel: ✕ descarta os ajustes da ferramenta
 * (volta ao estado da abertura da ferramenta), ✓ confirma e recolhe
 * o painel, deixando a foto grande.
 */
@Composable
internal fun ConfirmRow(
    onDiscard: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDiscard,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Descartar ajustes da ferramenta"
            )
        }
        FilledIconButton(
            onClick = onConfirm,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Confirmar e fechar a ferramenta"
            )
        }
    }
}

/**
 * Linha de slider: ícone, rótulo, ponto de "alterado", pílula de valor.
 * Toque duplo no rótulo zera o controle (quando [onReset] existe).
 */
@Composable
internal fun LumeSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    formatValue: (Float) -> String,
    icon: ImageVector? = null,
    defaultValue: Float = 0f,
    onReset: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val changed = value != defaultValue
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = { onReset?.invoke() })
                    }
            )
            if (changed) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (changed) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(
                        horizontal = LumeSpacing.xs,
                        vertical = LumeSpacing.xxs
                    )
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun LightPanel(
    recipe: Recipe,
    simpleMode: Boolean,
    onSimpleModeChange: (Boolean) -> Unit,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Luz",
                style = MaterialTheme.typography.titleSmall
            )
            MiniSegment(simple = simpleMode, onChange = onSimpleModeChange)
        }
        if (simpleMode) {
            // Simples: 4 controles sobre a MESMA receita. Nada se perde
            // ao trocar para o modo Pro — só a densidade muda.
            LumeSliderRow(
                label = "Luz",
                value = recipe.exposure,
                valueRange = -2f..2f,
                onValueChange = { onDraft(recipe.copy(exposure = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatExposure,
                icon = Icons.Filled.WbSunny,
                onReset = { onDraft(recipe.copy(exposure = 0f)); onCommit() }
            )
            LumeSliderRow(
                label = "Cor",
                value = recipe.saturation,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(saturation = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                icon = Icons.Filled.Palette,
                onReset = { onDraft(recipe.copy(saturation = 0f)); onCommit() }
            )
            LumeSliderRow(
                label = "Calor",
                value = recipe.temperature,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(temperature = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                icon = Icons.Filled.Thermostat,
                onReset = { onDraft(recipe.copy(temperature = 0f)); onCommit() }
            )
            LumeSliderRow(
                label = "Clima",
                value = recipe.contrast,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(contrast = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                icon = Icons.Filled.Contrast,
                onReset = { onDraft(recipe.copy(contrast = 0f)); onCommit() }
            )
        } else {
            LumeSliderRow(
                label = "Exposição",
                value = recipe.exposure,
                valueRange = -2f..2f,
                onValueChange = { onDraft(recipe.copy(exposure = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatExposure,
                icon = Icons.Filled.WbSunny,
                onReset = { onDraft(recipe.copy(exposure = 0f)); onCommit() }
            )
            LumeSliderRow(
                label = "Contraste",
                value = recipe.contrast,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(contrast = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                icon = Icons.Filled.Contrast,
                onReset = { onDraft(recipe.copy(contrast = 0f)); onCommit() }
            )
            LumeSliderRow(
                label = "Realces",
                value = recipe.highlights,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(highlights = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                icon = Icons.Filled.BrightnessHigh,
                onReset = { onDraft(recipe.copy(highlights = 0f)); onCommit() }
            )
            LumeSliderRow(
                label = "Sombras",
                value = recipe.shadows,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(shadows = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                icon = Icons.Filled.BrightnessLow,
                onReset = { onDraft(recipe.copy(shadows = 0f)); onCommit() }
            )
            LumeSliderRow(
                label = "Brancos",
                value = recipe.whites,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(whites = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                icon = Icons.Filled.LightMode,
                onReset = { onDraft(recipe.copy(whites = 0f)); onCommit() }
            )
            LumeSliderRow(
                label = "Pretos",
                value = recipe.blacks,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(blacks = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                icon = Icons.Filled.DarkMode,
                onReset = { onDraft(recipe.copy(blacks = 0f)); onCommit() }
            )
            CurveSection(
                recipe = recipe,
                onDraft = onDraft,
                onCommit = onCommit
            )
        }
        ConfirmRow(onDiscard = onDiscard, onConfirm = onConfirm)
    }
}

/** Segmento pequeno Simples/Pro no cabeçalho do painel Luz. */
@Composable
private fun MiniSegment(
    simple: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CircleShape,
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(2.dp)) {
            MiniSegmentOption(
                label = "Simples",
                selected = simple,
                onClick = { onChange(true) }
            )
            MiniSegmentOption(
                label = "Pro",
                selected = !simple,
                onClick = { onChange(false) }
            )
        }
    }
}

@Composable
private fun MiniSegmentOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = LumeSpacing.sm, vertical = LumeSpacing.xs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/**
 * Curva de tons (modo Pro): pré-visualização da curva master + chips de
 * curvas prontas. Tocar num chip aplica a curva de uma vez (com
 * desfazer). O editor de pontos arrastáveis chega depois; as curvas
 * RGB ficam reservadas, sem controle falso.
 */
@Composable
internal fun CurveSection(
    recipe: Recipe,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val masterPoints = recipe.curve.master
    val selectedName = ToneCurve.presetNameFor(masterPoints)
    val lut = remember(masterPoints) { ToneCurveLut.build(masterPoints) }
    val curveChips = listOf(
        "Linear" to ToneCurve.PRESET_LINEAR,
        "Contraste" to ToneCurve.PRESET_CONTRAST,
        "Fade" to ToneCurve.PRESET_FADE,
        "Suave" to ToneCurve.PRESET_GENTLE
    )
    val primary = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        Text(
            text = "Curva de tons",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            val padding = 8.dp.toPx()
            val plotWidth = size.width - padding * 2
            val plotHeight = size.height - padding * 2
            drawLine(
                color = gridColor.copy(alpha = 0.4f),
                start = Offset(padding, size.height - padding),
                end = Offset(size.width - padding, padding),
                strokeWidth = 1.dp.toPx()
            )
            val path = Path()
            lut.forEachIndexed { index, value ->
                val x = padding + (index.toFloat() / (lut.size - 1)) * plotWidth
                val y = size.height - padding - value * plotHeight
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = primary,
                style = Stroke(width = 2.dp.toPx())
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
        ) {
            curveChips.forEach { (name, points) ->
                FilterChip(
                    selected = selectedName == name,
                    onClick = {
                        onDraft(recipe.copy(curve = recipe.curve.copy(master = points)))
                        onCommit()
                    },
                    label = { Text(name) }
                )
            }
        }
    }
}

/** Resumo compacto de um canal HSL: só os componentes diferentes de zero. */
internal fun formatHslSummary(channel: HslChannel): String {
    val parts = buildList {
        if (channel.hue != 0f) add("M ${formatSigned(channel.hue)}")
        if (channel.saturation != 0f) add("S ${formatSigned(channel.saturation)}")
        if (channel.luminance != 0f) add("L ${formatSigned(channel.luminance)}")
    }
    return parts.joinToString(" · ")
}

/**
 * Cores dos pontos de canal HSL. São dados (a identidade de cada cor),
 * não cores de tema — um ponto vermelho precisa ser vermelho.
 */
private val HslDotColors: List<Color> = listOf(
    Color(0xFFF44336),
    Color(0xFFFF9800),
    Color(0xFFFFEB3B),
    Color(0xFF4CAF50),
    Color(0xFF00BCD4),
    Color(0xFF2196F3),
    Color(0xFF9C27B0),
    Color(0xFFE91E63)
)

/**
 * HSL por cor: fileira de pontos (um por canal) + 3 sliders do canal
 * selecionado. Os 4 primeiros canais são grátis; os outros 4 mostram
 * cadeado e abrem o paywall (regra em [ProPolicy]). O seletor "toque
 * na foto" (Pro) chega com o conta-gotas, na próxima entrega.
 */
@Composable
internal fun HslSection(
    recipe: Recipe,
    isPro: Boolean,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onRequestPaywall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableIntStateOf(0) }
    val channels = HslChannels.normalize(recipe.hsl)
    val channel = channels[selected]
    fun updateChannel(index: Int, updated: HslChannel): Recipe {
        val list = channels.toMutableList()
        list[index] = updated
        return recipe.copy(hsl = list)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        Text(
            text = "HSL por cor",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            channels.indices.forEach { index ->
                val locked = !ProPolicy.hslChannelUnlocked(index, isPro)
                val isSelected = index == selected
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(
                            HslDotColors[index].copy(alpha = if (locked) 0.35f else 1f)
                        )
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable {
                            if (locked) {
                                onRequestPaywall()
                            } else {
                                selected = index
                            }
                        }
                ) {
                    if (locked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Canal Pro",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
        Text(
            text = HslChannels.NAMES[selected],
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LumeSliderRow(
            label = "Matiz",
            value = channel.hue,
            valueRange = -100f..100f,
            onValueChange = { onDraft(updateChannel(selected, channel.copy(hue = it))) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = {
                onDraft(updateChannel(selected, channel.copy(hue = 0f)))
                onCommit()
            }
        )
        LumeSliderRow(
            label = "Saturação",
            value = channel.saturation,
            valueRange = -100f..100f,
            onValueChange = { onDraft(updateChannel(selected, channel.copy(saturation = it))) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = {
                onDraft(updateChannel(selected, channel.copy(saturation = 0f)))
                onCommit()
            }
        )
        LumeSliderRow(
            label = "Luminância",
            value = channel.luminance,
            valueRange = -100f..100f,
            onValueChange = { onDraft(updateChannel(selected, channel.copy(luminance = it))) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = {
                onDraft(updateChannel(selected, channel.copy(luminance = 0f)))
                onCommit()
            }
        )
    }
}

@Composable
internal fun ColorPanel(
    recipe: Recipe,
    isPro: Boolean,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onRequestPaywall: () -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        HslSection(
            recipe = recipe,
            isPro = isPro,
            onDraft = onDraft,
            onCommit = onCommit,
            onRequestPaywall = onRequestPaywall
        )
        Text(
            text = "Global",
            style = MaterialTheme.typography.titleSmall
        )
        LumeSliderRow(
            label = "Temperatura",
            value = recipe.temperature,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(temperature = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            icon = Icons.Filled.Thermostat,
            onReset = { onDraft(recipe.copy(temperature = 0f)); onCommit() }
        )
        LumeSliderRow(
            label = "Matiz",
            value = recipe.tint,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(tint = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            icon = Icons.Filled.ColorLens,
            onReset = { onDraft(recipe.copy(tint = 0f)); onCommit() }
        )
        LumeSliderRow(
            label = "Vibração",
            value = recipe.vibrance,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(vibrance = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            icon = Icons.Filled.WaterDrop,
            onReset = { onDraft(recipe.copy(vibrance = 0f)); onCommit() }
        )
        LumeSliderRow(
            label = "Saturação",
            value = recipe.saturation,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(saturation = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            icon = Icons.Filled.Palette,
            onReset = { onDraft(recipe.copy(saturation = 0f)); onCommit() }
        )
        ConfirmRow(onDiscard = onDiscard, onConfirm = onConfirm)
    }
}

/**
 * Efeitos globais do motor: claridade, vinheta, grão e desbotado.
 * Todos livres (regra em [ProPolicy]: efeitos e detalhe são totais).
 */
@Composable
internal fun EffectsPanel(
    recipe: Recipe,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        LumeSliderRow(
            label = "Claridade",
            value = recipe.clarity,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(clarity = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            icon = Icons.Filled.Tune,
            onReset = { onDraft(recipe.copy(clarity = 0f)); onCommit() }
        )
        LumeSliderRow(
            label = "Vinheta",
            value = recipe.vignette,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(vignette = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            icon = Icons.Filled.CenterFocusStrong,
            onReset = { onDraft(recipe.copy(vignette = 0f)); onCommit() }
        )
        LumeSliderRow(
            label = "Grão",
            value = recipe.grain,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(grain = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            icon = Icons.Filled.Grain,
            onReset = { onDraft(recipe.copy(grain = 0f)); onCommit() }
        )
        LumeSliderRow(
            label = "Desbotado",
            value = recipe.fade,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(fade = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            icon = Icons.Filled.Opacity,
            onReset = { onDraft(recipe.copy(fade = 0f)); onCommit() }
        )
        ConfirmRow(onDiscard = onDiscard, onConfirm = onConfirm)
    }
}

/**
 * Pilha de edição: cada ajuste ativo vira uma linha removível.
 * Remover uma linha zera só aquele campo (com desfazer). O preset
 * aplicado aparece como o primeiro passo e volta à base anterior.
 */
@Composable
internal fun HistoryPanel(
    recipe: Recipe,
    geometry: Geometry,
    photoWidth: Int,
    photoHeight: Int,
    appliedPresetName: String?,
    presetIntensity: Float,
    onClearField: (Recipe) -> Unit,
    onClearPreset: () -> Unit,
    onClearGeometry: () -> Unit,
    onCopyEdit: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val steps = buildList {
        if (appliedPresetName != null) {
            add(
                Triple(
                    "Preset · $appliedPresetName",
                    "${(presetIntensity * 100f).roundToInt()}%",
                    null as Recipe?
                )
            )
        }
        if (recipe.exposure != 0f) {
            add(Triple("Exposição", formatExposure(recipe.exposure), recipe.copy(exposure = 0f)))
        }
        if (recipe.contrast != 0f) {
            add(Triple("Contraste", formatSigned(recipe.contrast), recipe.copy(contrast = 0f)))
        }
        if (recipe.highlights != 0f) {
            add(Triple("Realces", formatSigned(recipe.highlights), recipe.copy(highlights = 0f)))
        }
        if (recipe.shadows != 0f) {
            add(Triple("Sombras", formatSigned(recipe.shadows), recipe.copy(shadows = 0f)))
        }
        if (recipe.whites != 0f) {
            add(Triple("Brancos", formatSigned(recipe.whites), recipe.copy(whites = 0f)))
        }
        if (recipe.blacks != 0f) {
            add(Triple("Pretos", formatSigned(recipe.blacks), recipe.copy(blacks = 0f)))
        }
        if (recipe.temperature != 0f) {
            add(Triple("Temperatura", formatSigned(recipe.temperature), recipe.copy(temperature = 0f)))
        }
        if (recipe.tint != 0f) {
            add(Triple("Matiz", formatSigned(recipe.tint), recipe.copy(tint = 0f)))
        }
        if (recipe.vibrance != 0f) {
            add(Triple("Vibração", formatSigned(recipe.vibrance), recipe.copy(vibrance = 0f)))
        }
        if (recipe.saturation != 0f) {
            add(Triple("Saturação", formatSigned(recipe.saturation), recipe.copy(saturation = 0f)))
        }
        if (recipe.clarity != 0f) {
            add(Triple("Claridade", formatSigned(recipe.clarity), recipe.copy(clarity = 0f)))
        }
        if (recipe.vignette != 0f) {
            add(Triple("Vinheta", formatSigned(recipe.vignette), recipe.copy(vignette = 0f)))
        }
        if (recipe.grain != 0f) {
            add(Triple("Grão", formatSigned(recipe.grain), recipe.copy(grain = 0f)))
        }
        if (recipe.fade != 0f) {
            add(Triple("Desbotado", formatSigned(recipe.fade), recipe.copy(fade = 0f)))
        }
        if (!recipe.curve.isEffectiveIdentity()) {
            val curveName = ToneCurve.presetNameFor(recipe.curve.master) ?: "Personalizada"
            add(Triple("Curva de tons", curveName, recipe.copy(curve = ToneCurve())))
        }
        HslChannels.normalize(recipe.hsl).forEachIndexed { index, channel ->
            if (!channel.isIdentity()) {
                val clearedHsl = HslChannels.normalize(recipe.hsl).toMutableList()
                clearedHsl[index] = HslChannel()
                add(
                    Triple(
                        "HSL · ${HslChannels.NAMES[index]}",
                        formatHslSummary(channel),
                        recipe.copy(hsl = clearedHsl)
                    )
                )
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        Text(
            text = "Pilha de edição",
            style = MaterialTheme.typography.titleSmall
        )
        val hasGeometry = !geometry.isIdentity()
        if (steps.isEmpty() && !hasGeometry) {
            Text(
                text = "Nenhuma edição ainda. Mexa num controle ou aplique " +
                    "um preset — cada passo aparece aqui para remover um só.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (hasGeometry) {
                // O corte vive na foto já girada: o resumo usa as
                // dimensões giradas.
                val (rotatedWidth, rotatedHeight) = geometry.rotatedSize(
                    photoWidth, photoHeight
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
                ) {
                    Text(
                        text = "Geometria",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = geometry.summary(rotatedWidth, rotatedHeight),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = onClearGeometry) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remover geometria"
                        )
                    }
                }
            }
            steps.forEach { (label, value, cleared) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = {
                            if (cleared == null) {
                                onClearPreset()
                            } else {
                                onClearField(cleared)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remover $label"
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onCopyEdit) {
                    Text("Copiar edição")
                }
                TextButton(onClick = onReset) {
                    Text("Redefinir tudo")
                }
            }
        }
    }
}

@Composable
internal fun PresetsPanel(
    presets: List<Preset>,
    preview: ImageBitmap?,
    appliedPresetName: String?,
    presetIntensity: Float,
    onApplyPreset: (Preset) -> Unit,
    onClearPreset: () -> Unit,
    onIntensityChange: (Float) -> Unit,
    onIntensityFinished: () -> Unit,
    onSavePresetClick: () -> Unit,
    onSharePresetClick: (Preset) -> Unit,
    onShuffleClick: () -> Unit,
    onConfirm: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appliedPreset = presets.firstOrNull { it.name == appliedPresetName }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = LumeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
        ) {
            item {
                PresetThumbCard(
                    label = "Original",
                    sublabel = "Sem preset",
                    selected = appliedPresetName == null,
                    onClick = onClearPreset
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        )
                    }
                }
            }
            items(items = presets, key = { it.id }) { preset ->
                val selected = preset.name == appliedPresetName
                // Miniatura rápida pela matriz (aproximação v1): HSL,
                // curva e efeitos do preset só aparecem na foto grande
                // e na exportação, que passam pelo motor. A tira vai
                // para o motor quando presets v2 virarem o caso comum.
                val filter = remember(preset.recipe) {
                    ColorFilter.colorMatrix(
                        ColorMatrix(RecipeColorMatrix.values(preset.recipe))
                    )
                }
                PresetThumbCard(
                    label = preset.name,
                    sublabel = preset.collection,
                    selected = selected,
                    onClick = { onApplyPreset(preset) }
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = filter,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        )
                    } else {
                        PresetSwatch(
                            preset = preset,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp)
                        )
                    }
                }
            }
        }
        if (appliedPreset != null) {
            Column(modifier = Modifier.padding(horizontal = LumeSpacing.md)) {
                LumeSliderRow(
                    label = "Intensidade de \"${appliedPreset.name}\"",
                    value = presetIntensity * 100f,
                    valueRange = 0f..100f,
                    onValueChange = { onIntensityChange(it / 100f) },
                    onValueChangeFinished = onIntensityFinished,
                    formatValue = { "${it.roundToInt()}%" }
                )
            }
            PresetRecipeStack(
                preset = appliedPreset,
                modifier = Modifier.padding(horizontal = LumeSpacing.md)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LumeSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
            ) {
                OutlinedButton(onClick = onShuffleClick) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null
                    )
                    Text("Embaralhar")
                }
                OutlinedButton(onClick = { onSharePresetClick(appliedPreset) }) {
                    Icon(
                        imageVector = Icons.Filled.QrCode2,
                        contentDescription = null
                    )
                    Text("Compartilhar")
                }
            }
        } else {
            Row(modifier = Modifier.padding(horizontal = LumeSpacing.md)) {
                OutlinedButton(onClick = onShuffleClick) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null
                    )
                    Text("Embaralhar preset")
                }
            }
        }
        Row(modifier = Modifier.padding(horizontal = LumeSpacing.md)) {
            OutlinedButton(onClick = onSavePresetClick) {
                Text("Salvar ajustes como preset")
            }
        }
        ConfirmRow(
            onDiscard = onDiscard,
            onConfirm = onConfirm,
            modifier = Modifier.padding(horizontal = LumeSpacing.md)
        )
    }
}

@Composable
private fun PresetThumbCard(
    label: String,
    sublabel: String,
    selected: Boolean,
    onClick: () -> Unit,
    thumbnail: @Composable () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            }
        ),
        modifier = Modifier.width(104.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                thumbnail()
            }
            Column(modifier = Modifier.padding(LumeSpacing.xs)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sublabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A receita do preset como pilha: cada campo ativo vira uma linha. */
@Composable
internal fun PresetRecipeStack(preset: Preset, modifier: Modifier = Modifier) {
    val recipe = preset.recipe
    val rows = buildList {
        if (recipe.exposure != 0f) add("Exposição" to formatExposure(recipe.exposure))
        if (recipe.contrast != 0f) add("Contraste" to formatSigned(recipe.contrast))
        if (recipe.highlights != 0f) add("Realces" to formatSigned(recipe.highlights))
        if (recipe.shadows != 0f) add("Sombras" to formatSigned(recipe.shadows))
        if (recipe.whites != 0f) add("Brancos" to formatSigned(recipe.whites))
        if (recipe.blacks != 0f) add("Pretos" to formatSigned(recipe.blacks))
        if (recipe.temperature != 0f) add("Temperatura" to formatSigned(recipe.temperature))
        if (recipe.tint != 0f) add("Matiz" to formatSigned(recipe.tint))
        if (recipe.vibrance != 0f) add("Vibração" to formatSigned(recipe.vibrance))
        if (recipe.saturation != 0f) add("Saturação" to formatSigned(recipe.saturation))
        if (recipe.clarity != 0f) add("Claridade" to formatSigned(recipe.clarity))
        if (recipe.vignette != 0f) add("Vinheta" to formatSigned(recipe.vignette))
        if (recipe.grain != 0f) add("Grão" to formatSigned(recipe.grain))
        if (recipe.fade != 0f) add("Desbotado" to formatSigned(recipe.fade))
        if (!recipe.curve.isEffectiveIdentity()) {
            val curveName = ToneCurve.presetNameFor(recipe.curve.master) ?: "Personalizada"
            add("Curva de tons" to curveName)
        }
        HslChannels.normalize(recipe.hsl).forEachIndexed { index, channel ->
            if (!channel.isIdentity()) {
                add("HSL · ${HslChannels.NAMES[index]}" to formatHslSummary(channel))
            }
        }
    }
    if (rows.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xxs)
    ) {
        Text(
            text = "Receita do preset",
            style = MaterialTheme.typography.titleSmall
        )
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Histograma de luminância (valores 0..1, já normalizados). */
@Composable
internal fun HistogramBars(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(120.dp, 36.dp)) {
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.45f),
            size = size,
            cornerRadius = CornerRadius(8.dp.toPx())
        )
        if (values.isEmpty()) return@Canvas
        val barWidth = size.width / values.size
        val padding = 4.dp.toPx()
        values.forEachIndexed { index, value ->
            val barHeight = (size.height - padding * 2) * value.coerceIn(0f, 1f)
            drawRect(
                color = Color.White.copy(alpha = 0.85f),
                topLeft = Offset(
                    index * barWidth + 1f,
                    size.height - padding - barHeight
                ),
                size = Size((barWidth - 2f).coerceAtLeast(1f), barHeight)
            )
        }
    }
}
