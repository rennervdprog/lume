package app.lume.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lume.core.Preset
import app.lume.core.Recipe
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

@Composable
internal fun LumeSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    formatValue: (Float) -> String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            label = "Exposição",
            value = recipe.exposure,
            valueRange = -2f..2f,
            onValueChange = { onDraft(recipe.copy(exposure = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatExposure
        )
        LumeSliderRow(
            label = "Contraste",
            value = recipe.contrast,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(contrast = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned
        )
        LumeSliderRow(
            label = "Realces",
            value = recipe.highlights,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(highlights = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned
        )
        LumeSliderRow(
            label = "Sombras",
            value = recipe.shadows,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(shadows = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned
        )
        LumeSliderRow(
            label = "Brancos",
            value = recipe.whites,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(whites = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned
        )
        LumeSliderRow(
            label = "Pretos",
            value = recipe.blacks,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(blacks = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned
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
            formatValue = ::formatSigned
        )
        LumeSliderRow(
            label = "Matiz",
            value = recipe.tint,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(tint = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned
        )
        LumeSliderRow(
            label = "Vibração",
            value = recipe.vibrance,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(vibrance = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned
        )
        LumeSliderRow(
            label = "Saturação",
            value = recipe.saturation,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(saturation = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned
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

@Composable
internal fun PresetsPanel(
    presets: List<Preset>,
    appliedPresetName: String?,
    presetIntensity: Float,
    onApplyPreset: (Preset) -> Unit,
    onIntensityChange: (Float) -> Unit,
    onIntensityFinished: () -> Unit,
    onSavePresetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = LumeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
        ) {
            items(items = presets, key = { it.id }) { preset ->
                val selected = preset.name == appliedPresetName
                Card(
                    onClick = { onApplyPreset(preset) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        }
                    ),
                    modifier = Modifier.width(128.dp)
                ) {
                    Column(modifier = Modifier.padding(LumeSpacing.sm)) {
                        Text(
                            text = preset.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = preset.collection,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        if (appliedPresetName != null) {
            Column(modifier = Modifier.padding(horizontal = LumeSpacing.md)) {
                LumeSliderRow(
                    label = "Intensidade de \"$appliedPresetName\"",
                    value = presetIntensity * 100f,
                    valueRange = 0f..100f,
                    onValueChange = { onIntensityChange(it / 100f) },
                    onValueChangeFinished = onIntensityFinished,
                    formatValue = { "${it.roundToInt()}%" }
                )
            }
        }
        Row(modifier = Modifier.padding(horizontal = LumeSpacing.md)) {
            OutlinedButton(onClick = onSavePresetClick) {
                Text("Salvar ajustes como preset")
            }
        }
    }
}
