package app.lume.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lume.core.Preset
import app.lume.core.Recipe
import app.lume.core.RecipeColorMatrix
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
            .padding(horizontal = LumeSpacing.xs),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        tools.forEach { (tool, label, icon) ->
            val isSelected = tool == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { onSelect(tool) }
                    .padding(horizontal = LumeSpacing.xs, vertical = LumeSpacing.xs)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center
                )
            }
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
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
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
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onReset) {
                Text("Redefinir ajustes")
            }
        }
    }
}

@Composable
internal fun ColorPanel(
    recipe: Recipe,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onReset) {
                Text("Redefinir ajustes")
            }
        }
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
    appliedPresetName: String?,
    presetIntensity: Float,
    onClearField: (Recipe) -> Unit,
    onClearPreset: () -> Unit,
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
        if (steps.isEmpty()) {
            Text(
                text = "Nenhuma edição ainda. Mexa num controle ou aplique " +
                    "um preset — cada passo aparece aqui para remover um só.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
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
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onReset) {
                    Text("Redefinir tudo")
                }
            }
        }
    }
}

/**
 * Lugar reservado e honesto para ferramentas da Fase 1: explica o que
 * vem, sem controle falso. Os sliders são desabilitados de propósito.
 */
@Composable
internal fun SoonPanel(
    title: String,
    description: String,
    mockLabels: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = "FASE 1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = LumeSpacing.xs, vertical = LumeSpacing.xxs)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        mockLabels.forEach { label ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = 0f,
                    onValueChange = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
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
