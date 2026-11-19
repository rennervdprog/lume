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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brightness3
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FilterFrames
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Tonality
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbCloudy
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import app.lume.core.BuiltInLuts
import app.lume.core.Feature
import app.lume.core.HslChannel
import app.lume.core.HslChannels
import app.lume.core.Geometry
import app.lume.core.Preset
import app.lume.core.ProPolicy
import app.lume.core.Recipe
import app.lume.core.RecipeColorMatrix
import app.lume.core.ToneCurve
import app.lume.core.ToneCurveLut
import app.lume.data.UserLut
import app.lume.imaging.ArtisticEffect
import app.lume.imaging.BlendMode
import app.lume.imaging.GlEngine
import app.lume.imaging.LutGrids
import app.lume.ui.components.PresetSwatch
import app.lume.ui.theme.LumeSpacing
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    CROP, FILTER, ADJUST, BLEND, HISTORY
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
        Triple(EditorTool.BLEND, "Mistura", Icons.Filled.Layers),
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
        // Ajustar Pro (0.14.0): os itens novos do motor entram no
        // fim do trilho, depois da Curva. Os Pro levam o selo ★ e o
        // toque abre o paywall — nada some atrás de modo. O RGB é
        // grátis (CURVES_BASIC): sem selo, como a tabela aprovada.
        val adjustProLocked = !ProPolicy.isUnlocked(Feature.ADJUST_PRO, isPro)
        val blurSkinLocked = !ProPolicy.isUnlocked(Feature.BLUR_SKIN, isPro)
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
            IconSubOption("curve", "Curva", Icons.Filled.ShowChart),
            IconSubOption("levels", "Níveis", Icons.Filled.Tonality, adjustProLocked),
            IconSubOption("balance", "Balanço", Icons.Filled.WbCloudy, adjustProLocked),
            IconSubOption("rgb", "RGB", Icons.Filled.Equalizer),
            IconSubOption("haze", "Névoa", Icons.Filled.Cloud, adjustProLocked),
            IconSubOption("gamma", "Gama", Icons.Filled.BrightnessMedium, adjustProLocked),
            IconSubOption("hueShift", "Giro de cor", Icons.Filled.Autorenew, adjustProLocked),
            IconSubOption("blur", "Desfoque", Icons.Filled.BlurOn, blurSkinLocked),
            IconSubOption("smooth", "Pele", Icons.Filled.Face, blurSkinLocked)
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
            "levels" -> LevelsSection(
                recipe = recipe,
                onDraft = onDraft,
                onCommit = onCommit,
                onCompareChange = onCompareChange
            )
            "balance" -> BalanceSection(
                recipe = recipe,
                onDraft = onDraft,
                onCommit = onCommit,
                onCompareChange = onCompareChange
            )
            "rgb" -> RgbSection(
                recipe = recipe,
                onDraft = onDraft,
                onCommit = onCommit,
                onCompareChange = onCompareChange
            )
            // Névoa: + desembaça, - adiciona névoa (ver o motor).
            "haze" -> SlimSliderRow(
                value = recipe.haze,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(haze = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(haze = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            // Gama: + clareia os meios-tons, - escurece.
            "gamma" -> SlimSliderRow(
                value = recipe.gamma,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(gamma = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(gamma = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            // Giro de cor global: ±100 = ±180° de matiz.
            "hueShift" -> SlimSliderRow(
                value = recipe.hueShift,
                valueRange = -100f..100f,
                onValueChange = { onDraft(recipe.copy(hueShift = it)) },
                onValueChangeFinished = onCommit,
                formatValue = ::formatSigned,
                onReset = { onDraft(recipe.copy(hueShift = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "blur" -> SlimSliderRow(
                value = recipe.blur,
                valueRange = 0f..100f,
                onValueChange = { onDraft(recipe.copy(blur = it)) },
                onValueChangeFinished = onCommit,
                formatValue = { "${it.roundToInt()}%" },
                onReset = { onDraft(recipe.copy(blur = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
            "smooth" -> SlimSliderRow(
                value = recipe.smoothSkin,
                valueRange = 0f..100f,
                onValueChange = { onDraft(recipe.copy(smoothSkin = it)) },
                onValueChangeFinished = onCommit,
                formatValue = { "${it.roundToInt()}%" },
                onReset = { onDraft(recipe.copy(smoothSkin = 0f)); onCommit() },
                onCompareChange = onCompareChange
            )
        }
        IconSubRail(
            options = options,
            selectedId = effectiveSub,
            onSelect = onSubSelect,
            onLockedSelect = { onRequestPaywall() }
        )
    }
}

/**
 * Níveis (Ajustar Pro): ponto preto, meio-tom e ponto branco — o
 * mesmo trio do Lightroom. Preto e branco são 0..100 (janela que
 * corta as pontas); meio-tom é -100..100 (clareia/escurece o meio).
 */
@Composable
private fun LevelsSection(
    recipe: Recipe,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onCompareChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        SlimSliderRow(
            caption = "Ponto preto",
            value = recipe.levelsBlack,
            valueRange = 0f..100f,
            onValueChange = { onDraft(recipe.copy(levelsBlack = it)) },
            onValueChangeFinished = onCommit,
            formatValue = { "${it.roundToInt()}%" },
            onReset = { onDraft(recipe.copy(levelsBlack = 0f)); onCommit() },
            onCompareChange = onCompareChange
        )
        SlimSliderRow(
            caption = "Meio-tom",
            value = recipe.levelsMid,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(levelsMid = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = { onDraft(recipe.copy(levelsMid = 0f)); onCommit() },
            onCompareChange = onCompareChange
        )
        SlimSliderRow(
            caption = "Ponto branco",
            value = recipe.levelsWhite,
            valueRange = 0f..100f,
            onValueChange = { onDraft(recipe.copy(levelsWhite = it)) },
            onValueChangeFinished = onCommit,
            formatValue = { "${it.roundToInt()}%" },
            onReset = { onDraft(recipe.copy(levelsWhite = 0f)); onCommit() },
            onCompareChange = onCompareChange
        )
    }
}

/**
 * Balanço de cor por faixa (Ajustar Pro): quente (+) ou frio (-)
 * só nas sombras, só nos meios-tons ou só nos realces. A
 * luminosidade de cada faixa é preservada (ver o motor).
 */
@Composable
private fun BalanceSection(
    recipe: Recipe,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onCompareChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        SlimSliderRow(
            caption = "Sombras",
            value = recipe.cbShadows,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(cbShadows = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = { onDraft(recipe.copy(cbShadows = 0f)); onCommit() },
            onCompareChange = onCompareChange
        )
        SlimSliderRow(
            caption = "Meios-tons",
            value = recipe.cbMidtones,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(cbMidtones = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = { onDraft(recipe.copy(cbMidtones = 0f)); onCommit() },
            onCompareChange = onCompareChange
        )
        SlimSliderRow(
            caption = "Realces",
            value = recipe.cbHighlights,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(cbHighlights = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = { onDraft(recipe.copy(cbHighlights = 0f)); onCommit() },
            onCompareChange = onCompareChange
        )
    }
}

/**
 * RGB (grátis, como a tabela aprovada): ganho por canal, 1 é
 * neutro. Vermelho +, verde +, azul + deixam a foto mais quente
 * ou mais fria sem tocar na temperatura global.
 */
@Composable
private fun RgbSection(
    recipe: Recipe,
    onDraft: (Recipe) -> Unit,
    onCommit: () -> Unit,
    onCompareChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        SlimSliderRow(
            caption = "Vermelho",
            value = recipe.rgbR,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(rgbR = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = { onDraft(recipe.copy(rgbR = 0f)); onCommit() },
            onCompareChange = onCompareChange
        )
        SlimSliderRow(
            caption = "Verde",
            value = recipe.rgbG,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(rgbG = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = { onDraft(recipe.copy(rgbG = 0f)); onCommit() },
            onCompareChange = onCompareChange
        )
        SlimSliderRow(
            caption = "Azul",
            value = recipe.rgbB,
            valueRange = -100f..100f,
            onValueChange = { onDraft(recipe.copy(rgbB = it)) },
            onValueChangeFinished = onCommit,
            formatValue = ::formatSigned,
            onReset = { onDraft(recipe.copy(rgbB = 0f)); onCommit() },
            onCompareChange = onCompareChange
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
 * ficam com a cor cheia e o selo ★, e o toque abre o paywall
 * (regra em [ProPolicy]). O seletor "toque na foto" (Pro) chega
 * com o conta-gotas.
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
                    contentAlignment = Alignment.TopEnd,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(HslDotColors[index])
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
                        ProSealBadge(
                            modifier = Modifier.offset(x = 4.dp, y = (-4).dp),
                            contentDescription = "Canal Pro"
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
 * Ferramenta Mistura (0.14.0): dupla exposição — uma segunda foto
 * sobre a primeira, com 8 modos e opacidade. Recurso Pro: no plano
 * grátis tudo fica visível com o selo ★ e o toque abre o paywall
 * (o gesto do Lightroom). A segunda foto vive só na sessão: o
 * rascunho guarda o modo e a opacidade, não a foto.
 */
@Composable
internal fun BlendPanel(
    recipe: Recipe,
    isPro: Boolean,
    hasBlendPhoto: Boolean,
    onPickBlendPhoto: () -> Unit,
    onClearBlendPhoto: () -> Unit,
    onApplyBlendMode: (String?) -> Unit,
    onBlendOpacityChange: (Float) -> Unit,
    onBlendOpacityFinished: () -> Unit,
    onRequestPaywall: () -> Unit,
    onCompareChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val locked = !ProPolicy.isUnlocked(Feature.BLEND_MODES, isPro)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
    ) {
        PanelHeader(title = "Mistura")
        Text(
            text = "Uma segunda foto sobre a primeira. Escolha a foto, " +
                "o modo e a opacidade.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { if (locked) onRequestPaywall() else onPickBlendPhoto() },
                modifier = Modifier.height(36.dp)
            ) {
                if (locked) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = if (hasBlendPhoto) {
                        "Trocar segunda foto"
                    } else {
                        "Escolher segunda foto"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = if (locked) 6.dp else 0.dp)
                )
            }
            if (hasBlendPhoto) {
                TextButton(onClick = onClearBlendPhoto) {
                    Text("Remover")
                }
            }
        }
        // Modos: pílulas roláveis; a selecionada é a do modo ativo.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                FilterChip(
                    selected = recipe.blendMode == null,
                    onClick = { onApplyBlendMode(null) },
                    label = { Text("Sem mistura") }
                )
            }
            items(items = BlendMode.entries, key = { it.id }) { mode ->
                FilterChip(
                    selected = recipe.blendMode == mode.id,
                    onClick = {
                        if (locked) {
                            onRequestPaywall()
                        } else {
                            onApplyBlendMode(mode.id)
                        }
                    },
                    label = { Text(mode.label) },
                    leadingIcon = if (locked) {
                        {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Pro",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        null
                    }
                )
            }
        }
        if (recipe.blendMode != null) {
            SlimSliderRow(
                caption = "Opacidade",
                value = recipe.blendOpacity * 100f,
                valueRange = 0f..100f,
                onValueChange = { onBlendOpacityChange(it / 100f) },
                onValueChangeFinished = onBlendOpacityFinished,
                formatValue = { "${it.roundToInt()}%" },
                onReset = {
                    onBlendOpacityChange(1f)
                    onBlendOpacityFinished()
                },
                onCompareChange = onCompareChange
            )
        }
        if (!hasBlendPhoto) {
            Text(
                text = "Sem a segunda foto, o modo não aparece na foto — " +
                    "escolha uma foto para ver a mistura.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    lutDisplayName: String? = null,
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
        if (recipe.lutId != null) {
            add(
                Triple(
                    "LUT · ${lutDisplayName ?: "LUT"}",
                    "${(recipe.lutIntensity * 100f).roundToInt()}%",
                    recipe.copy(lutId = null, lutIntensity = 0f)
                )
            )
        }
        if (recipe.levelsBlack != 0f) {
            add(
                Triple(
                    "Níveis · Preto",
                    "${recipe.levelsBlack.roundToInt()}%",
                    recipe.copy(levelsBlack = 0f)
                )
            )
        }
        if (recipe.levelsMid != 0f) {
            add(
                Triple(
                    "Níveis · Meio-tom",
                    formatSigned(recipe.levelsMid),
                    recipe.copy(levelsMid = 0f)
                )
            )
        }
        if (recipe.levelsWhite != 0f) {
            add(
                Triple(
                    "Níveis · Branco",
                    "${recipe.levelsWhite.roundToInt()}%",
                    recipe.copy(levelsWhite = 0f)
                )
            )
        }
        if (recipe.cbShadows != 0f) {
            add(
                Triple(
                    "Balanço · Sombras",
                    formatSigned(recipe.cbShadows),
                    recipe.copy(cbShadows = 0f)
                )
            )
        }
        if (recipe.cbMidtones != 0f) {
            add(
                Triple(
                    "Balanço · Meios-tons",
                    formatSigned(recipe.cbMidtones),
                    recipe.copy(cbMidtones = 0f)
                )
            )
        }
        if (recipe.cbHighlights != 0f) {
            add(
                Triple(
                    "Balanço · Realces",
                    formatSigned(recipe.cbHighlights),
                    recipe.copy(cbHighlights = 0f)
                )
            )
        }
        if (recipe.haze != 0f) {
            add(Triple("Névoa", formatSigned(recipe.haze), recipe.copy(haze = 0f)))
        }
        if (recipe.gamma != 0f) {
            add(Triple("Gama", formatSigned(recipe.gamma), recipe.copy(gamma = 0f)))
        }
        if (recipe.hueShift != 0f) {
            add(Triple("Giro de cor", formatSigned(recipe.hueShift), recipe.copy(hueShift = 0f)))
        }
        if (recipe.rgbR != 0f) {
            add(Triple("RGB · Vermelho", formatSigned(recipe.rgbR), recipe.copy(rgbR = 0f)))
        }
        if (recipe.rgbG != 0f) {
            add(Triple("RGB · Verde", formatSigned(recipe.rgbG), recipe.copy(rgbG = 0f)))
        }
        if (recipe.rgbB != 0f) {
            add(Triple("RGB · Azul", formatSigned(recipe.rgbB), recipe.copy(rgbB = 0f)))
        }
        if (recipe.blur != 0f) {
            add(
                Triple(
                    "Desfoque",
                    "${recipe.blur.roundToInt()}%",
                    recipe.copy(blur = 0f)
                )
            )
        }
        if (recipe.smoothSkin != 0f) {
            add(
                Triple(
                    "Suavizar pele",
                    "${recipe.smoothSkin.roundToInt()}%",
                    recipe.copy(smoothSkin = 0f)
                )
            )
        }
        if (recipe.artisticEffect != null) {
            val effect = ArtisticEffect.fromId(recipe.artisticEffect)
            add(
                Triple(
                    "Efeito · ${effect?.label ?: "Efeito"}",
                    "${(recipe.artisticIntensity * 100f).roundToInt()}%",
                    recipe.copy(artisticEffect = null, artisticIntensity = 1f)
                )
            )
        }
        if (recipe.blendMode != null) {
            val mode = BlendMode.fromId(recipe.blendMode)
            add(
                Triple(
                    "Mistura · ${mode?.label ?: "Mistura"}",
                    "${(recipe.blendOpacity * 100f).roundToInt()}%",
                    recipe.copy(blendMode = null, blendOpacity = 1f)
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

/** Abas do painel Filtro: presets (Todos + coleções), LUTs e efeitos artísticos. */
sealed interface FilterTab {
    data object All : FilterTab
    data object Luts : FilterTab
    data object MyLuts : FilterTab
    data object Effects : FilterTab
    data class Collection(val name: String) : FilterTab
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
    userLuts: List<UserLut> = emptyList(),
    selectedLutId: String? = null,
    lutIntensity: Float = 1f,
    onApplyLut: (String?) -> Unit = {},
    onLutIntensityChange: (Float) -> Unit = {},
    onLutIntensityFinished: () -> Unit = {},
    onImportLutClick: () -> Unit = {},
    onDeleteLut: (String) -> Unit = {},
    isPro: Boolean = false,
    selectedArtisticId: String? = null,
    artisticIntensity: Float = 1f,
    onApplyArtistic: (String?) -> Unit = {},
    onArtisticIntensityChange: (Float) -> Unit = {},
    onArtisticIntensityFinished: () -> Unit = {},
    onRequestPaywall: () -> Unit = {},
    initialTab: FilterTab = FilterTab.All,
    modifier: Modifier = Modifier
) {
    val appliedPreset = presets.firstOrNull { it.name == appliedPresetName }
    var selectedTab by remember(presets) { mutableStateOf(initialTab) }
    val collections = remember(presets) {
        presets.map { it.collection }.distinct()
    }
    val visiblePresets = when (val tab = selectedTab) {
        is FilterTab.Collection -> presets.filter { it.collection == tab.name }
        else -> presets
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
                    selected = selectedTab == FilterTab.All,
                    onClick = { selectedTab = FilterTab.All }
                )
            }
            item {
                TextTab(
                    label = "LUTs",
                    selected = selectedTab == FilterTab.Luts,
                    onClick = { selectedTab = FilterTab.Luts }
                )
            }
            item {
                TextTab(
                    label = "Meus LUTs",
                    selected = selectedTab == FilterTab.MyLuts,
                    onClick = { selectedTab = FilterTab.MyLuts }
                )
            }
            item {
                TextTab(
                    label = "Efeitos",
                    selected = selectedTab == FilterTab.Effects,
                    onClick = { selectedTab = FilterTab.Effects }
                )
            }
            items(collections) { collection ->
                TextTab(
                    label = collection,
                    selected = selectedTab == FilterTab.Collection(collection),
                    onClick = { selectedTab = FilterTab.Collection(collection) }
                )
            }
        }
        when (selectedTab) {
            is FilterTab.Luts -> LutFilmstrip(
                preview = preview,
                selectedLutId = selectedLutId,
                onApplyLut = onApplyLut,
                onImportLutClick = onImportLutClick
            )
            is FilterTab.MyLuts -> MyLutFilmstrip(
                preview = preview,
                userLuts = userLuts,
                selectedLutId = selectedLutId,
                onApplyLut = onApplyLut,
                onImportLutClick = onImportLutClick,
                onDeleteLut = onDeleteLut
            )
            is FilterTab.Effects -> EffectsFilmstrip(
                preview = preview,
                isPro = isPro,
                selectedArtisticId = selectedArtisticId,
                onApplyArtistic = onApplyArtistic,
                onRequestPaywall = onRequestPaywall
            )
            else -> PresetFilmstrip(
                presets = visiblePresets,
                preview = preview,
                appliedPresetName = appliedPresetName,
                onApplyPreset = onApplyPreset,
                onClearPreset = onClearPreset
            )
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
        if (selectedLutId != null) {
            SlimSliderRow(
                caption = "Intensidade do LUT",
                value = lutIntensity * 100f,
                valueRange = 0f..100f,
                onValueChange = { onLutIntensityChange(it / 100f) },
                onValueChangeFinished = onLutIntensityFinished,
                formatValue = { "${it.roundToInt()}%" },
                onReset = {
                    onLutIntensityChange(1f)
                    onLutIntensityFinished()
                },
                onCompareChange = onCompareChange,
                modifier = Modifier.padding(horizontal = LumeSpacing.md)
            )
        }
        // Intensidade do efeito artístico (o Esboço não tem
        // intensidade — liga/desliga, sem slider).
        val selectedEffect = ArtisticEffect.fromId(selectedArtisticId)
        if (selectedEffect != null && selectedEffect.hasIntensity) {
            SlimSliderRow(
                caption = "Intensidade do efeito",
                value = artisticIntensity * 100f,
                valueRange = 0f..100f,
                onValueChange = { onArtisticIntensityChange(it / 100f) },
                onValueChangeFinished = onArtisticIntensityFinished,
                formatValue = { "${it.roundToInt()}%" },
                onReset = {
                    onArtisticIntensityChange(1f)
                    onArtisticIntensityFinished()
                },
                onCompareChange = onCompareChange,
                modifier = Modifier.padding(horizontal = LumeSpacing.md)
            )
        }
    }
}

/** Tira de presets (aba Todos ou uma coleção): código original do painel. */
@Composable
private fun PresetFilmstrip(
    presets: List<Preset>,
    preview: ImageBitmap?,
    appliedPresetName: String?,
    onApplyPreset: (Preset) -> Unit,
    onClearPreset: () -> Unit
) {
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
}

/** Tira da aba LUTs: "Sem LUT" + os 6 LUTs próprios do Lume. */
@Composable
private fun LutFilmstrip(
    preview: ImageBitmap?,
    selectedLutId: String?,
    onApplyLut: (String?) -> Unit,
    onImportLutClick: () -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = LumeSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
    ) {
        item {
            LutImportCard(onClick = onImportLutClick)
        }
        item {
            LutThumbSquare(
                label = "Sem LUT",
                selected = selectedLutId == null,
                onClick = { onApplyLut(null) }
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        bitmap = lutFallbackBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
        items(items = BuiltInLuts.all, key = { it.id }) { lut ->
            val thumb = rememberLutThumb(preview, lut.id)
            LutThumbSquare(
                label = lut.name,
                selected = lut.id == selectedLutId,
                onClick = { onApplyLut(lut.id) }
            ) {
                if (thumb != null) {
                    Image(
                        bitmap = thumb,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/** Tira da aba Meus LUTs: importar + os .cube do usuário, com ✕. */
@Composable
private fun MyLutFilmstrip(
    preview: ImageBitmap?,
    userLuts: List<UserLut>,
    selectedLutId: String?,
    onApplyLut: (String?) -> Unit,
    onImportLutClick: () -> Unit,
    onDeleteLut: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = LumeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
        ) {
            item {
                LutImportCard(onClick = onImportLutClick)
            }
            items(items = userLuts, key = { it.id }) { lut ->
                val thumb = rememberLutThumb(preview, lut.id)
                LutThumbSquare(
                    label = lut.name,
                    selected = lut.id == selectedLutId,
                    onClick = { onApplyLut(lut.id) },
                    onDeleteClick = { onDeleteLut(lut.id) }
                ) {
                    if (thumb != null) {
                        Image(
                            bitmap = thumb,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        if (userLuts.isEmpty()) {
            Text(
                text = "Nenhum LUT importado ainda. Toque em Importar " +
                    "e escolha um arquivo .cube de qualquer fotógrafo ou pacote.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = LumeSpacing.md)
            )
        }
    }
}

/** Tira da aba Efeitos: "Sem efeito" + os 10 efeitos artísticos (Pro). */
@Composable
private fun EffectsFilmstrip(
    preview: ImageBitmap?,
    isPro: Boolean,
    selectedArtisticId: String?,
    onApplyArtistic: (String?) -> Unit,
    onRequestPaywall: () -> Unit
) {
    val locked = !ProPolicy.isUnlocked(Feature.ARTISTIC_EFFECTS, isPro)
    LazyRow(
        contentPadding = PaddingValues(horizontal = LumeSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
    ) {
        item {
            LutThumbSquare(
                label = "Sem efeito",
                selected = selectedArtisticId == null,
                onClick = { onApplyArtistic(null) }
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
        items(items = ArtisticEffect.entries, key = { it.id }) { effect ->
            val thumb = rememberArtisticThumb(preview, effect.id)
            LutThumbSquare(
                label = effect.label,
                selected = effect.id == selectedArtisticId,
                sealed = locked,
                onClick = {
                    if (locked) {
                        onRequestPaywall()
                    } else {
                        onApplyArtistic(effect.id)
                    }
                }
            ) {
                val shown = thumb ?: preview
                if (shown != null) {
                    Image(
                        bitmap = shown,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Miniatura de um efeito artístico sobre a foto aberta: o efeito
 * de verdade, renderizado pelo motor numa cópia reduzida. O render
 * é assíncrono (o motor vive numa thread só e o grupo é
 * reconstruído a cada efeito) — enquanto não chega, o cartão
 * mostra a foto sem efeito. Se o motor falhar, fica a foto sem
 * efeito (nunca um cartão vazio).
 */
@Composable
private fun rememberArtisticThumb(preview: ImageBitmap?, effectId: String): ImageBitmap? {
    var thumb by remember(preview, effectId) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(preview, effectId) {
        thumb = null
        val source = preview?.asAndroidBitmap() ?: return@LaunchedEffect
        val rendered = withContext(Dispatchers.Default) {
            GlEngine.render(source, Recipe(artisticEffect = effectId, artisticIntensity = 1f))
        }
        val base = rendered ?: source
        val scaled = downscaleThumb(base, 144)
        if (rendered != null && scaled !== rendered) {
            rendered.recycle()
        }
        thumb = scaled.asImageBitmap()
    }
    return thumb
}

/**
 * Miniatura de um LUT sobre a foto aberta (ou sobre as faixas de cor,
 * sem foto): a grade é aplicada em CPU numa cópia reduzida — a
 * miniatura é o LUT de verdade, não uma aproximação de matriz. O
 * cálculo roda uma vez por (foto, LUT) e fica no remember.
 */
@Composable
private fun rememberLutThumb(preview: ImageBitmap?, lutId: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(preview, lutId) {
        val grid = LutGrids.gridFor(context, lutId) ?: return@remember null
        val source = preview?.asAndroidBitmap()?.let { downscaleThumb(it) }
            ?: lutFallbackBitmap
        grid.applyTo(source).asImageBitmap()
    }
}

private fun downscaleThumb(source: Bitmap, targetWidth: Int = 144): Bitmap {
    if (source.width <= targetWidth) return source
    val height = (source.height * targetWidth / source.width).coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, targetWidth, height, true)
}

/**
 * Faixas de cor de reserva para as miniaturas sem foto aberta: pele,
 * céu, folha, calor, neutro — as mesmas cores da amostra de preset.
 * Uma única instância compartilhada (a aplicação do LUT só lê).
 */
private val lutFallbackBitmap: Bitmap by lazy {
    val width = 144
    val height = 144
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        val bands = arrayOf(
            Triple(217, 160, 122),
            Triple(127, 168, 217),
            Triple(123, 160, 91),
            Triple(217, 179, 106),
            Triple(138, 143, 148)
        )
        for (y in 0 until height) {
            val shade = 1f - (y.toFloat() / height) * 0.45f
            for (x in 0 until width) {
                val (r0, g0, b0) = bands[(x * bands.size) / width]
                val r = (r0 * shade).toInt()
                val g = (g0 * shade).toInt()
                val b = (b0 * shade).toInt()
                setPixel(x, y, (0xFF shl 24) or (r shl 16) or (g shl 8) or b)
            }
        }
    }
}

@Composable
private fun LutThumbSquare(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    sealed: Boolean = false,
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
            if (sealed) {
                ProSealBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
            if (onDeleteClick != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clip(CircleShape)
                        .clickable(onClick = onDeleteClick)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Apagar $label",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
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

@Composable
private fun LutImportCard(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }
        Text(
            text = "Importar",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = LumeSpacing.xxs)
        )
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
        if (recipe.lutId != null) {
            add("LUT" to (BuiltInLuts.displayName(recipe.lutId) ?: "LUT importado"))
        }
        if (recipe.levelsBlack != 0f) {
            add("Níveis · Preto" to "${recipe.levelsBlack.roundToInt()}%")
        }
        if (recipe.levelsMid != 0f) {
            add("Níveis · Meio-tom" to formatSigned(recipe.levelsMid))
        }
        if (recipe.levelsWhite != 0f) {
            add("Níveis · Branco" to "${recipe.levelsWhite.roundToInt()}%")
        }
        if (recipe.cbShadows != 0f) {
            add("Balanço · Sombras" to formatSigned(recipe.cbShadows))
        }
        if (recipe.cbMidtones != 0f) {
            add("Balanço · Meios-tons" to formatSigned(recipe.cbMidtones))
        }
        if (recipe.cbHighlights != 0f) {
            add("Balanço · Realces" to formatSigned(recipe.cbHighlights))
        }
        if (recipe.haze != 0f) add("Névoa" to formatSigned(recipe.haze))
        if (recipe.gamma != 0f) add("Gama" to formatSigned(recipe.gamma))
        if (recipe.hueShift != 0f) add("Giro de cor" to formatSigned(recipe.hueShift))
        if (recipe.rgbR != 0f) add("RGB · Vermelho" to formatSigned(recipe.rgbR))
        if (recipe.rgbG != 0f) add("RGB · Verde" to formatSigned(recipe.rgbG))
        if (recipe.rgbB != 0f) add("RGB · Azul" to formatSigned(recipe.rgbB))
        if (recipe.blur != 0f) add("Desfoque" to "${recipe.blur.roundToInt()}%")
        if (recipe.smoothSkin != 0f) {
            add("Suavizar pele" to "${recipe.smoothSkin.roundToInt()}%")
        }
        if (recipe.artisticEffect != null) {
            val effect = ArtisticEffect.fromId(recipe.artisticEffect)
            add(
                "Efeito · ${effect?.label ?: "Efeito"}" to
                    "${(recipe.artisticIntensity * 100f).roundToInt()}%"
            )
        }
        if (recipe.blendMode != null) {
            val mode = BlendMode.fromId(recipe.blendMode)
            add(
                "Mistura · ${mode?.label ?: "Mistura"}" to
                    "${(recipe.blendOpacity * 100f).roundToInt()}%"
            )
        }
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
