package app.lume.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
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
    CROP, FILTER, ADJUST, HISTORY
}

/** Trilho de ferramentas: ícone + rótulo, uma ferramenta por vez. */
@Composable
internal fun EditorToolRail(
    selected: EditorTool,
    onSelect: (EditorTool) -> Unit,
    modifier: Modifier = Modifier
) {
    val tools = listOf(
        Triple(EditorTool.CROP, "Cortar", Icons.Filled.Crop),
        Triple(EditorTool.FILTER, "Filtro", Icons.Filled.Style),
        Triple(EditorTool.ADJUST, "Ajustar", Icons.Filled.Tune),
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
                Box(
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSelected) {
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

/**
 * Painel Ajustar (v4): todos os controles de luz, cor e efeitos num
 * só lugar, com trilho de ícones rolável. Simples mostra 5
 * controles; Pro abre os 15, com HSL e Curva no fim do trilho. O
 * histograma fica sempre visível no topo do painel.
 */
@Composable
internal fun AdjustPanel(
    recipe: Recipe,
    histogram: List<Float>,
    isPro: Boolean,
    simpleMode: Boolean,
    onSimpleModeChange: (Boolean) -> Unit,
    selectedSub: String,
    onSubSelect: (String) -> Unit,
    hslChannel: Int,
    onHslChannelSelect: (Int) -> Unit,
    hslAttr: String,
    onHslAttrSelect: (String) -> Unit,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onRequestPaywall: () -> Unit,
    onCompareChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    // Simples: 5 controles sobre a MESMA receita. Nada se perde ao
    // trocar para o modo Pro — só a densidade muda.
    val options = if (simpleMode) {
        listOf(
            IconSubOption("exposure", "Exposição", Icons.Filled.WbSunny),
            IconSubOption("contrast", "Contraste", Icons.Filled.Contrast),
            IconSubOption("temperature", "Temperatura", Icons.Filled.Thermostat),
            IconSubOption("saturation", "Saturação", Icons.Filled.Opacity),
            IconSubOption("clarity", "Claridade", Icons.Filled.CenterFocusStrong)
        )
    } else {
        listOf(
            IconSubOption("exposure", "Exposição", Icons.Filled.WbSunny),
            IconSubOption("contrast", "Contraste", Icons.Filled.Contrast),
            IconSubOption("highlights", "Realces", Icons.Filled.BrightnessHigh),
            IconSubOption("shadows", "Sombras", Icons.Filled.BrightnessLow),
            IconSubOption("whites", "Brancos", Icons.Filled.Brightness7),
            IconSubOption("blacks", "Pretos", Icons.Filled.Brightness3),
            IconSubOption("temperature", "Temperatura", Icons.Filled.Thermostat),
            IconSubOption("tint", "Matiz", Icons.Filled.InvertColors),
            IconSubOption("vibrance", "Vibração", Icons.Filled.Palette),
            IconSubOption("saturation", "Saturação", Icons.Filled.Opacity),
            IconSubOption("clarity", "Claridade", Icons.Filled.CenterFocusStrong),
            IconSubOption("vignette", "Vinheta", Icons.Filled.FilterFrames),
            IconSubOption("fade", "Desbotado", Icons.Filled.Gradient),
            IconSubOption("hsl", "HSL", Icons.Filled.ColorLens),
            IconSubOption("curve", "Curva", Icons.Filled.ShowChart)
        )
    }
    val effectiveSub = if (options.any { it.id == selectedSub }) {
        selectedSub
    } else {
        options.first().id
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        PanelHeader(
            title = "Ajustar",
            trailing = {
                MiniSegment(simple = simpleMode, onChange = onSimpleModeChange)
            }
        )
        HistogramStrip(values = histogram)
        when (effectiveSub) {
            "hsl" -> HslSection(
                recipe = recipe,
                isPro = isPro,
                selectedChannel = hslChannel,
                onChannelSelect = onHslChannelSelect,
                selectedAttr = hslAttr,
                onAttrSelect = onHslAttrSelect,
                onDraft = onDraft,
                onCommit = onCommit,
                onRequestPaywall = onRequestPaywall,
                onCompareChange = onCompareChange
            )
            "curve" -> CurveSection(
                recipe = recipe,
                onDraft = onDraft,
                onCommit = onCommit
            )
            "exposure" -> SlimSliderRow(
                value = recipe.exposure,
                valueRange = -2f..2f,
                onValueChange = { onDraft(recipe.copy(exposure = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatExposure,
                onReset = { onDraft(recipe.copy(exposure = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "contrast" -> SlimSliderRow(
                value = recipe.contrast,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(contrast = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(contrast = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "highlights" -> SlimSliderRow(
                value = recipe.highlights,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(highlights = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(highlights = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "shadows" -> SlimSliderRow(
                value = recipe.shadows,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(shadows = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(shadows = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "whites" -> SlimSliderRow(
                value = recipe.whites,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(whites = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(whites = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "blacks" -> SlimSliderRow(
                value = recipe.blacks,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(blacks = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(blacks = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "temperature" -> SlimSliderRow(
                value = recipe.temperature,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(temperature = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(temperature = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "tint" -> SlimSliderRow(
                value = recipe.tint,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(tint = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(tint = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "vibrance" -> SlimSliderRow(
                value = recipe.vibrance,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(vibrance = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(vibrance = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "saturation" -> SlimSliderRow(
                value = recipe.saturation,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(saturation = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(saturation = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "clarity" -> SlimSliderRow(
                value = recipe.clarity,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(clarity = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(clarity = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            // Vinheta 0..100: o filtro da GPUImage só escurece os
            // cantos — negativo seria um slider que não faz nada.
            "vignette" -> SlimSliderRow(
                value = recipe.vignette.coerceAtLeast(0f),
                valueRange = 0f..100f,
                onValueChange = { onDraft(recipe.copy(vignette = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(vignette = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "fade" -> SlimSliderRow(
                value = recipe.fade,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(fade = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(fade = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
        }
        IconSubRail(
            options = options,
            selectedId = effectiveSub,
            onSelect = onSubSelect
        )
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
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
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
            modifier = Modifier.horizontalScroll(rememberScrollState()),
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
 * HSL por cor (v3): fileira de pontos (um por canal) + chips
 * Matiz/Saturação/Luminância + UM slider fino do atributo
 * selecionado. Os 4 primeiros canais são grátis; os outros 4
 * mostram cadeado e abrem o paywall (regra em [ProPolicy]). O
 * seletor "toque na foto" (Pro) chega com o conta-gotas.
 */
@Composable
internal fun HslSection(
    recipe: Recipe,
    isPro: Boolean,
    selectedChannel: Int,
    onChannelSelect: (Int) -> Unit,
    selectedAttr: String,
    onAttrSelect: (String) -> Unit,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onRequestPaywall: () -> Unit,
    onCompareChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val channels = HslChannels.normalize(recipe.hsl)
    val channel = channels[selectedChannel]
    fun updateChannel(index: Int, updated: HslChannel): Recipe {
        val list = channels.toMutableList()
        list[index] = updated
        return recipe.copy(hsl = list)
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            channels.indices.forEach { index ->
                val locked = !ProPolicy.hslChannelUnlocked(index, isPro)
                val isSelected = index == selectedChannel
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
                                onChannelSelect(index)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                LumeSpacing.xs,
                Alignment.CenterHorizontally
            )
        ) {
            listOf(
                "hue" to "Matiz",
                "saturation" to "Saturação",
                "luminance" to "Luminância"
            ).forEach { (id, label) ->
                FilterChip(
                    selected = selectedAttr == id,
                    onClick = { onAttrSelect(id) },
                    label = { Text(label) }
                )
            }
        }
        val attrLabel: String
        val attrValue: Float
        val attrUpdate: (Float) -> Recipe
        when (selectedAttr) {
            "saturation" -> {
                attrLabel = "Saturação"
                attrValue = channel.saturation
                attrUpdate = { updateChannel(selectedChannel, channel.copy(saturation = it)) }
            }
            "luminance" -> {
                attrLabel = "Luminância"
                attrValue = channel.luminance
                attrUpdate = { updateChannel(selectedChannel, channel.copy(luminance = it)) }
            }
            else -> {
                attrLabel = "Matiz"
                attrValue = channel.hue
                attrUpdate = { updateChannel(selectedChannel, channel.copy(hue = it)) }
            }
        }
        SlimSliderRow(
            caption = "${HslChannels.NAMES[selectedChannel]} · $attrLabel",
            value = attrValue,
            valueRange = -100f..100f,
            onValueChange = { onDraft(attrUpdate(it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = { onDraft(attrUpdate(0f)); onCommit() },
            onCompareChange = onCompareChange
        )
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
        val hasGeometry = !geometry.isIdentity()
        PanelHeader(
            title = "Histórico",
            trailing = {
                if (steps.isNotEmpty() || hasGeometry) {
                    TextButton(onClick = onReset) {
                        Text("Limpar tudo")
                    }
                }
            }
        )
        if (steps.isEmpty() && !hasGeometry) {
            Text(
                text = "Nenhuma edição ainda. Mexa num controle ou aplique " +
                    "um preset — cada passo aparece aqui para remover um só.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // A pilha rola por dentro, com teto: o painel não pode
            // crescer e roubar a foto quando há muitos passos.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
            ) {
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
            }
            OutlinedButton(onClick = onCopyEdit) {
                Text("Copiar edição")
            }
        }
    }
}

@Composable
internal fun FilterPanel(
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
    onCompareChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val appliedPreset = presets.firstOrNull { it.name == appliedPresetName }
    var selectedCollection by remember(presets) { mutableStateOf<String?>(null) }
    val collections = remember(presets) {
        presets.map { it.collection }.distinct()
    }
    val visiblePresets = if (selectedCollection == null) {
        presets
    } else {
        presets.filter { it.collection == selectedCollection }
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        PanelHeader(
            title = "Filtro",
            modifier = Modifier.padding(horizontal = LumeSpacing.md)
        )
        // Categorias: texto puro com traço azul na selecionada, sem
        // pílulas — a fileira nunca muda de altura nem pula.
        LazyRow(
            contentPadding = PaddingValues(horizontal = LumeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.md)
        ) {
            item {
                TextTab(
                    label = "Todos",
                    selected = selectedCollection == null,
                    onClick = { selectedCollection = null }
                )
            }
            items(collections) { collection ->
                TextTab(
                    label = collection,
                    selected = selectedCollection == collection,
                    onClick = { selectedCollection = collection }
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = LumeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
        ) {
            item {
                PresetThumbSquare(
                    label = "Original",
                    selected = appliedPresetName == null,
                    onClick = onClearPreset
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            items(items = visiblePresets, key = { it.id }) { preset ->
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
                PresetThumbSquare(
                    label = preset.name,
                    selected = selected,
                    onClick = { onApplyPreset(preset) }
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            colorFilter = filter,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        PresetSwatch(
                            preset = preset,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        // Ações na própria linha, sempre no mesmo lugar: o QR fica
        // desativado (não some) sem preset aplicado.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LumeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
        ) {
            OutlinedButton(
                onClick = onShuffleClick,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Shuffle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Embaralhar",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            OutlinedButton(
                onClick = { appliedPreset?.let(onSharePresetClick) },
                enabled = appliedPreset != null,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "QR",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
            OutlinedButton(
                onClick = onSavePresetClick,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Save,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Salvar",
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }
        }
        if (appliedPreset != null) {
            SlimSliderRow(
                caption = "Intensidade",
                value = presetIntensity * 100f,
                valueRange = 0f..100f,
                onValueChange = { onIntensityChange(it / 100f) },
                onValueChangeFinished = onIntensityFinished,
                formatValue = { "${it.roundToInt()}%" },
                onReset = {
                    onIntensityChange(1f)
                    onIntensityFinished()
                },
                onCompareChange = onCompareChange,
                modifier = Modifier.padding(horizontal = LumeSpacing.md)
            )
        }
    }
}

@Composable
private fun PresetThumbSquare(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    thumbnail: @Composable () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .then(
                    if (selected) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(12.dp)
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            thumbnail()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = LumeSpacing.xxs)
        )
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
