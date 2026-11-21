package app.lume.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.core.BuiltInLuts
import app.lume.core.Feature
import app.lume.core.HslChannel
import app.lume.core.HslChannels
import app.lume.core.Preset
import app.lume.core.ProPolicy
import app.lume.core.Recipe
import app.lume.core.RecipeJson
import app.lume.core.TextFont
import app.lume.core.TextOverlay
import app.lume.core.fitScale
import app.lume.imaging.PresetQr
import app.lume.imaging.ShareHelper
import app.lume.ui.components.ShowMessageAsSnackbar
import app.lume.ui.components.SupportingText
import app.lume.ui.components.rememberPhotoPickerLauncher
import app.lume.ui.theme.LumeSpacing
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Savers por nome para os enums da seleção do Editor: o
 * rememberSaveable só guarda tipos que o Bundle entende, e enum
 * não é um deles. Sem isso, o `remember` puro morre ao navegar
 * para o Pro e voltar — a tela renasce no padrão (Ajustar, na
 * Exposição) em vez da ferramenta que estava aberta.
 */
private val editorToolSaver = Saver<EditorTool, String>(
    save = { it.name },
    restore = { runCatching { EditorTool.valueOf(it) }.getOrDefault(EditorTool.ADJUST) }
)

private val cropTabSaver = Saver<CropTab, String>(
    save = { it.name },
    restore = { runCatching { CropTab.valueOf(it) }.getOrDefault(CropTab.CROP) }
)

/** Modos do gesto na foto (um por gesto; o modo trava no 1º movimento). */
private enum class PhotoGestureMode { VALUE, TOOL, SPLIT, PAN, PINCH }

/**
 * A ferramenta atual como alvo do gesto na foto (arrastar horizontal
 * muda o valor): rótulo para o valor gigante, valor inicial, faixa,
 * formato e como aplicar o rascunho. null = a ferramenta não tem
 * valor de slider (Curva, ferramenta Pro travada, canal HSL Pro).
 */
private data class AdjustGestureSpec(
    val label: String,
    val value: Float,
    val range: ClosedFloatingPointRange<Float>,
    val format: (Float) -> String,
    val applyDraft: (Float) -> Unit
)

private fun adjustGestureSpec(
    recipe: Recipe,
    subId: String,
    hslChannel: Int,
    hslAttr: String,
    isPro: Boolean,
    onDraft: (Recipe) -> Unit
): AdjustGestureSpec? {
    val adjustLocked = !ProPolicy.isUnlocked(Feature.ADJUST_PRO, isPro)
    val blurLocked = !ProPolicy.isUnlocked(Feature.BLUR_SKIN, isPro)
    fun single(
        label: String,
        value: Float,
        range: ClosedFloatingPointRange<Float>,
        format: (Float) -> String,
        copy: (Float) -> Recipe
    ): AdjustGestureSpec = AdjustGestureSpec(
        label = label,
        value = value,
        range = range,
        format = format,
        applyDraft = { onDraft(copy(it)) }
    )
    return when (subId) {
        "exposure" -> single(
            "Exposição", recipe.exposure, -2f..2f, ::formatExposure
        ) { recipe.copy(exposure = it) }
        "contrast" -> single(
            "Contraste", recipe.contrast, -100f..100f, ::formatSigned
        ) { recipe.copy(contrast = it) }
        "highlights" -> single(
            "Realces", recipe.highlights, -100f..100f, ::formatSigned
        ) { recipe.copy(highlights = it) }
        "shadows" -> single(
            "Sombras", recipe.shadows, -100f..100f, ::formatSigned
        ) { recipe.copy(shadows = it) }
        "whites" -> single(
            "Brancos", recipe.whites, -100f..100f, ::formatSigned
        ) { recipe.copy(whites = it) }
        "blacks" -> single(
            "Pretos", recipe.blacks, -100f..100f, ::formatSigned
        ) { recipe.copy(blacks = it) }
        "temperature" -> single(
            "Temperatura", recipe.temperature, -100f..100f, ::formatSigned
        ) { recipe.copy(temperature = it) }
        "tint" -> single(
            "Matiz", recipe.tint, -100f..100f, ::formatSigned
        ) { recipe.copy(tint = it) }
        "vibrance" -> single(
            "Vibração", recipe.vibrance, -100f..100f, ::formatSigned
        ) { recipe.copy(vibrance = it) }
        "saturation" -> single(
            "Saturação", recipe.saturation, -100f..100f, ::formatSigned
        ) { recipe.copy(saturation = it) }
        "clarity" -> single(
            "Claridade", recipe.clarity, -100f..100f, ::formatSigned
        ) { recipe.copy(clarity = it) }
        "vignette" -> single(
            "Vinheta", recipe.vignette.coerceAtLeast(0f), 0f..100f, ::formatSigned
        ) { recipe.copy(vignette = it) }
        "fade" -> single(
            "Desbotado", recipe.fade, -100f..100f, ::formatSigned
        ) { recipe.copy(fade = it) }
        "haze" -> if (adjustLocked) {
            null
        } else {
            single(
                "Névoa", recipe.haze, -100f..100f, ::formatSigned
            ) { recipe.copy(haze = it) }
        }
        "gamma" -> if (adjustLocked) {
            null
        } else {
            single(
                "Gama", recipe.gamma, -100f..100f, ::formatSigned
            ) { recipe.copy(gamma = it) }
        }
        "hueShift" -> if (adjustLocked) {
            null
        } else {
            single(
                "Giro de cor", recipe.hueShift, -100f..100f, ::formatSigned
            ) { recipe.copy(hueShift = it) }
        }
        "blur" -> if (blurLocked) {
            null
        } else {
            single(
                "Desfoque", recipe.blur, 0f..100f, { "${it.roundToInt()}%" }
            ) { recipe.copy(blur = it) }
        }
        "smooth" -> if (blurLocked) {
            null
        } else {
            single(
                "Pele", recipe.smoothSkin, 0f..100f, { "${it.roundToInt()}%" }
            ) { recipe.copy(smoothSkin = it) }
        }
        "levels" -> if (adjustLocked) {
            null
        } else {
            single(
                "Ponto preto", recipe.levelsBlack, 0f..100f, { "${it.roundToInt()}%" }
            ) { recipe.copy(levelsBlack = it) }
        }
        "balance" -> if (adjustLocked) {
            null
        } else {
            single(
                "Sombras", recipe.cbShadows, -100f..100f, ::formatSigned
            ) { recipe.copy(cbShadows = it) }
        }
        "rgb" -> single(
            "Vermelho", recipe.rgbR, -100f..100f, ::formatSigned
        ) { recipe.copy(rgbR = it) }
        "hsl" -> {
            if (!ProPolicy.hslChannelUnlocked(hslChannel, isPro)) {
                null
            } else {
                val channels = HslChannels.normalize(recipe.hsl)
                val channel = channels[hslChannel]
                fun updateChannel(updated: HslChannel): Recipe {
                    val list = channels.toMutableList()
                    list[hslChannel] = updated
                    return recipe.copy(hsl = list)
                }
                val (attrLabel, attrValue, attrUpdate) = when (hslAttr) {
                    "saturation" -> Triple(
                        "Saturação", channel.saturation
                    ) { v: Float -> updateChannel(channel.copy(saturation = v)) }
                    "luminance" -> Triple(
                        "Luminância", channel.luminance
                    ) { v: Float -> updateChannel(channel.copy(luminance = v)) }
                    else -> Triple(
                        "Matiz", channel.hue
                    ) { v: Float -> updateChannel(channel.copy(hue = v)) }
                }
                single(
                    "${HslChannels.NAMES[hslChannel]} · $attrLabel",
                    attrValue, -100f..100f, ::formatSigned, attrUpdate
                )
            }
        }
        // Curva não tem valor de slider — o gesto vertical só passa por ela.
        else -> null
    }
}

/** Delta de um gesto de pinça entre dois eventos consecutivos. */
private data class PinchDelta(
    val centroidDelta: Offset,
    val scaleFactor: Float,
    val angleDeltaDegrees: Float
)

/**
 * Rastreador de pinça: entre o primeiro evento de dois dedos e os
 * seguintes, devolve o deslocamento do centroide, o fator de escala
 * e a variação de ângulo (normalizada para ±180°, sem salto).
 */
private class PinchTracker {
    private var lastCentroid: Offset? = null
    private var lastDistance = 0f
    private var lastAngle = 0f

    fun update(p0: Offset, p1: Offset): PinchDelta? {
        val centroid = (p0 + p1) * 0.5f
        val distance = (p0 - p1).getDistance()
        val angle = (atan2(p1.y - p0.y, p1.x - p0.x) * 180.0 / PI).toFloat()
        val prevCentroid = lastCentroid
        val result = if (prevCentroid != null && lastDistance > 0f) {
            val rawDelta = angle - lastAngle
            val angleDelta = ((rawDelta + 540f) % 360f) - 180f
            PinchDelta(
                centroidDelta = centroid - prevCentroid,
                scaleFactor = distance / lastDistance,
                angleDeltaDegrees = angleDelta
            )
        } else {
            null
        }
        lastCentroid = centroid
        lastDistance = distance
        lastAngle = angle
        return result
    }

    fun reset() {
        lastCentroid = null
        lastDistance = 0f
        lastAngle = 0f
    }
}

/**
 * Toque = um passo; segurar repete o passo; segurar 1s dispara a
 * ação longa (abrir o Histórico). Usado nos botões desfazer/refazer.
 */
private fun Modifier.holdRepeatAction(
    onTap: () -> Unit,
    onRepeat: () -> Unit,
    onLongHold: () -> Unit
): Modifier = pointerInput(onTap, onRepeat, onLongHold) {
    while (true) {
        awaitEachGesture {
            awaitFirstDown()
            var repeated = false
            var longHoldFired = false
            val repeatJob = launch {
                delay(400)
                while (true) {
                    repeated = true
                    onRepeat()
                    delay(120)
                }
            }
            val longJob = launch {
                delay(1000)
                longHoldFired = true
                repeatJob.cancel()
                onLongHold()
            }
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.all { !it.pressed }) break
            }
            repeatJob.cancel()
            longJob.cancel()
            if (!longHoldFired && !repeated) {
                onTap()
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    // Seleção da tela com rememberSaveable (não remember): ao
    // navegar para o Pro a tela sai da composição e o remember
    // puro morre — na volta ela renascia no padrão (Ajustar, na
    // Exposição) e a ferramenta escolhida se perdia. O
    // rememberSaveable sobrevive à ida e volta no back stack.
    // Diálogos e pedidos de entrada única ficam no remember:
    // não precisam sobreviver à navegação.
    var selectedTool by rememberSaveable(stateSaver = editorToolSaver) {
        mutableStateOf(EditorTool.ADJUST)
    }
    var splitMode by rememberSaveable { mutableStateOf(false) }
    var splitFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var qrPreset by remember { mutableStateOf<Preset?>(null) }
    var showOriginal by rememberSaveable { mutableStateOf(false) }
    var panelVisible by rememberSaveable { mutableStateOf(true) }
    // Foto cheia (duplo toque): esconde painel, trilho e o resto do
    // cromo — só foto + voltar/desfazer/refazer/Exportar.
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    // Histograma sobre a foto (chip): visível por padrão; o toque
    // alterna cinza/RGB, o segurar esconde.
    var histogramVisible by rememberSaveable { mutableStateOf(true) }
    var histogramRgb by rememberSaveable { mutableStateOf(false) }
    var cropTab by rememberSaveable(stateSaver = cropTabSaver) {
        mutableStateOf(CropTab.CROP)
    }
    var adjustSub by rememberSaveable { mutableStateOf("exposure") }
    var hslChannel by rememberSaveable { mutableIntStateOf(0) }
    var hslAttr by rememberSaveable { mutableStateOf("hue") }
    var pendingPresetId by remember { mutableStateOf<String?>(null) }
    var pendingFilterTab by remember { mutableStateOf<FilterTab?>(null) }

    // Texto sobre a foto (Fase 1): rememberSaveable como o resto da
    // seleção — o remember puro morre ao ir ao paywall e voltar.
    // A fonte usa Saver por nome (enum não é Bundle-saveable).
    var textOverlayText by rememberSaveable { mutableStateOf("") }
    var textFont by rememberSaveable(stateSaver = textFontSaver) {
        mutableStateOf(TextFont.DANCING_SCRIPT)
    }
    var textSizeFraction by rememberSaveable {
        mutableFloatStateOf(TextOverlay.DEFAULT_SIZE_FRACTION)
    }
    var textColorArgb by rememberSaveable {
        mutableIntStateOf(TextOverlay.DEFAULT_COLOR_ARGB)
    }
    var textCenterX by rememberSaveable {
        mutableFloatStateOf(TextOverlay.DEFAULT_CENTER_X)
    }
    var textCenterY by rememberSaveable {
        mutableFloatStateOf(TextOverlay.DEFAULT_CENTER_Y)
    }
    // Gestos na foto (0.15.6): zoom/pan transitórios, valor gigante
    // durante o arrastar, alvo do embaralhador (rolar até o sorteado).
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var zoomOffset by remember { mutableStateOf(Offset.Zero) }
    var gestureValue by remember { mutableStateOf<Pair<String, String>?>(null) }
    var shuffleTargetName by remember { mutableStateOf<String?>(null) }
    var shuffleNonce by remember { mutableIntStateOf(0) }
    val textOvershoot = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val haptics = LocalHapticFeedback.current
    val textOverlay = TextOverlay(
        text = textOverlayText,
        font = textFont,
        sizeFraction = textSizeFraction,
        colorArgb = textColorArgb,
        centerX = textCenterX,
        centerY = textCenterY
    )
    // Tamanho da área da foto em pixels: a foto exibida (Fit) vive
    // dentro dela com letterbox — o texto é posicionado sobre o
    // retângulo exibido, não sobre a área cheia.
    var photoAreaSize by remember { mutableStateOf(IntSize.Zero) }

    val openPicker = rememberPhotoPickerLauncher { uri ->
        // Foto nova pelo seletor do Editor: o preset do banner não
        // vale para ela (valia só para a foto que abriu o Editor).
        pendingPresetId = null
        viewModel.openPhoto(uri)
    }

    // Importar .cube: o formato não tem MIME registrado, então o
    // seletor abre sem filtro e a validação é do conteúdo.
    val importLutLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importLut(uri)
        }
    }

    // Segunda foto da Mistura: só imagem; a decodificação é do ViewModel.
    val blendPhotoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.pickBlendPhoto(uri)
        }
    }

    // Nome do LUT atual para o passo do Histórico.
    val lutDisplayName = state.recipe.lutId?.let { id ->
        BuiltInLuts.displayName(id)
            ?: state.userLuts.firstOrNull { it.id == id }?.name
    }

    ShowMessageAsSnackbar(
        message = state.message,
        snackbarHostState = snackbarHostState,
        onConsume = { viewModel.consumeMessage() }
    )

    LaunchedEffect(state.paywallRequested) {
        if (state.paywallRequested) {
            viewModel.consumePaywallRequest()
            onOpenPaywall()
        }
    }

    // Alvo pendente da Home: a grade abre o Editor já na ferramenta
    // tocada (e na subferramenta do Ajustar, quando há: HSL, Curva).
    // Consumido uma vez ao entrar; sem pedido, abre no padrão
    // (Ajustar, na Exposição).
    LaunchedEffect(Unit) {
        PendingEditorTarget.take()?.let { target ->
            selectedTool = target.tool
            target.adjustSub?.let { adjustSub = it }
            target.presetId?.let { pendingPresetId = it }
            pendingFilterTab = target.filterTab
            panelVisible = true
        }
    }

    // Preset pedido pelo banner da Home: aplica quando a foto já
    // carregou (o rascunho salvo, se houver, já foi restaurado).
    // O rascunho vence: o preset só entra em foto sem edição.
    LaunchedEffect(state.preview, state.presets, pendingPresetId) {
        val presetId = pendingPresetId ?: return@LaunchedEffect
        if (state.preview == null || state.presets.isEmpty()) return@LaunchedEffect
        pendingPresetId = null
        if (!state.recipe.isIdentity()) return@LaunchedEffect
        state.presets.find { it.id == presetId }?.let { preset ->
            viewModel.applyPreset(preset)
        }
    }

    // Abrir uma ferramenta marca a base do X/✓.
    LaunchedEffect(selectedTool) {
        viewModel.beginPanelSession()
    }

    // A dica do segurar-pra-ver-original some para sempre depois do
    // primeiro uso real (o gesto marca; o toque na dica também marca).
    LaunchedEffect(showOriginal) {
        if (showOriginal && !state.originalHintShown) {
            viewModel.markOriginalHintShown()
        }
    }

    // O gesto na foto ignora toques nestas duas pílulas (elas têm o
    // próprio gesto). Os limites são lidos via rememberUpdatedState
    // dentro do loop (a área da foto só é medida depois da 1ª
    // composição). Chip: canto inferior esquerdo, 136×40dp. Dica:
    // topo central, abaixo dos botões, 280×36dp.
    val histogramChipBounds: Rect? =
        if (state.histogram.isNotEmpty() && chromeVisible &&
            photoAreaSize != IntSize.Zero
        ) {
            val left = with(density) { 8.dp.toPx() }
            val top = photoAreaSize.height - with(density) { 48.dp.toPx() }
            Rect(
                left, top,
                left + with(density) { 136.dp.toPx() },
                photoAreaSize.height - with(density) { 8.dp.toPx() }
            )
        } else {
            null
        }
    val hintPillBounds: Rect? =
        if (!state.originalHintShown && chromeVisible && photoAreaSize != IntSize.Zero) {
            val width = with(density) { 280.dp.toPx() }
            val height = with(density) { 36.dp.toPx() }
            val left = (photoAreaSize.width - width) / 2f
            val top = with(density) { 56.dp.toPx() }
            Rect(left, top, left + width, top + height)
        } else {
            null
        }
    val latestHistogramChipBounds by rememberUpdatedState(histogramChipBounds)
    val latestHintPillBounds by rememberUpdatedState(hintPillBounds)
    val latestIsOnText by rememberUpdatedState(isOnText)

    // A moldura do Cortar só existe com o painel aberto na aba CORTAR:
    // o render suspende o corte e a moldura mostra o corte sobre a
    // foto cheia. Fora daqui, o render aplica a geometria completa.
    // Na foto cheia a moldura também some.
    val cropOverlayVisible = selectedTool == EditorTool.CROP &&
        cropTab == CropTab.CROP && chromeVisible && state.preview != null
    LaunchedEffect(cropOverlayVisible) {
        viewModel.setCropPreviewMode(cropOverlayVisible)
    }

    // Dimensões da foto já girada (o espaço onde o corte vive), para
    // a moldura, as proporções e o resumo da pilha de edição.
    val previewBitmap = state.preview
    val (photoWidth, photoHeight) = if (previewBitmap != null) {
        state.geometry.rotatedSize(previewBitmap.width, previewBitmap.height)
    } else {
        0 to 0
    }

    // Retângulo da foto exibida dentro da área (ContentScale.Fit
    // centraliza com letterbox): é nele que o texto vive — as
    // frações do overlay são frações DESTE retângulo, que é a foto.
    val displayedSize = if (photoAreaSize != IntSize.Zero && photoWidth > 0 && photoHeight > 0) {
        val scale = minOf(
            photoAreaSize.width / photoWidth.toFloat(),
            photoAreaSize.height / photoHeight.toFloat()
        )
        IntSize(
            (photoWidth * scale).roundToInt(),
            (photoHeight * scale).roundToInt()
        )
    } else {
        IntSize.Zero
    }
    val displayedOffset = IntOffset(
        (photoAreaSize.width - displayedSize.width) / 2,
        (photoAreaSize.height - displayedSize.height) / 2
    )

    // Tamanho do texto na tela (Fase 1): medido no tamanho base e
    // encolhido só se não couber na foto (mesma regra da
    // exportação). Fora de qualquer `if`: o remember nunca muda de
    // posição entre recomposições.
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val textBaseSizePx = textOverlay.sizeFraction * displayedSize.width
    val textBaseFontSize = with(density) { textBaseSizePx.toInt().toSp() }
    val textMeasuredWidthPx = remember(textOverlay.text, textOverlay.font, textBaseFontSize) {
        if (textOverlay.text.isBlank() || textBaseSizePx <= 0f) {
            0f
        } else {
            textMeasurer.measure(
                text = AnnotatedString(textOverlay.text),
                style = TextStyle(
                    fontFamily = textOverlay.font.fontFamily(),
                    fontSize = textBaseFontSize
                )
            ).size.width.toFloat()
        }
    }
    val textScale = fitScale(
        textMeasuredWidthPx,
        TextOverlay.MAX_WIDTH_FRACTION * displayedSize.width
    )
    val textFontSize = with(density) { (textBaseSizePx * textScale).toInt().toSp() }

    // Cromo visível = painel aberto E fora da foto cheia. A foto cheia
    // não fecha o painel (panelVisible continua true) — só o esconde.
    val chromeVisible = panelVisible && !fullscreen

    // Alvo do gesto na foto: a ferramenta atual do Ajustar como valor
    // (null quando não há slider — Curva, Pro travado, canal HSL Pro).
    // O loop de gestos lê via rememberUpdatedState (não reinicia o
    // gesto quando a ferramenta muda entre gestos).
    val adjustSpec = if (selectedTool == EditorTool.ADJUST) {
        adjustGestureSpec(state.recipe, adjustSub, hslChannel, hslAttr, state.isPro) {
            viewModel.updateRecipeDraft(it)
        }
    } else {
        null
    }
    val latestSpec by rememberUpdatedState(adjustSpec)
    val latestZoom by rememberUpdatedState(zoomScale)

    // Próxima ferramenta com valor de gesto, na ordem dos grupos
    // (Luz → Cor → Tom → Efeito, circular): o arrastar vertical pula
    // as que não têm slider (Curva, Pro travado).
    val nextAdjustTool: (String, Int) -> String = { currentId, direction ->
        val order = ADJUST_TOOL_ORDER
        val size = order.size
        val start = order.indexOf(currentId).coerceAtLeast(0)
        var index = start
        var found = currentId
        var steps = 0
        while (steps < size) {
            index = (index + direction + size) % size
            steps++
            val candidate = order[index]
            val spec = adjustGestureSpec(
                state.recipe, candidate, hslChannel, hslAttr, state.isPro
            ) { viewModel.updateRecipeDraft(it) }
            if (spec != null) {
                found = candidate
                break
            }
        }
        found
    }
    val latestNextTool by rememberUpdatedState(nextAdjustTool)

    // Com a ferramenta Texto aberta, o gesto na foto é do texto
    // (arrastar move) — o segurar-para-comparar fica desligado.
    val textToolActive = selectedTool == EditorTool.TEXT

    // Retângulo do texto na foto (coordenadas da área da foto, em px):
    // glifo + folga, mais a alça abaixo dele. O gesto na foto ignora
    // toques aqui (o texto é dono do gesto); o resto é do gesto de
    // valor/ferramenta. A alça só existe com a ferramenta Texto aberta.
    val textHitBounds: Rect? =
        if (!textOverlay.isEmpty && displayedSize != IntSize.Zero && textToolActive) {
            val halfW = textMeasuredWidthPx / 2f + with(density) { 16.dp.toPx() }
            val halfH = textBaseSizePx * textScale / 2f + with(density) { 16.dp.toPx() }
            val cx = displayedOffset.x + textCenterX * displayedSize.width
            val cy = displayedOffset.y + textCenterY * displayedSize.height
            val handleBottom = cy + halfH + with(density) { 48.dp.toPx() }
            Rect(cx - halfW, cy - halfH, cx + halfW, handleBottom)
        } else {
            null
        }
    val isOnText: (Offset) -> Boolean = { pos ->
        val bounds = textHitBounds
        bounds != null && bounds.contains(pos)
    }

    val onConfirmPanel: () -> Unit = {
        viewModel.confirmPanelSession()
        panelVisible = false
    }
    val onDiscardPanel: () -> Unit = {
        if (selectedTool == EditorTool.FILTER) {
            // No Filtro, o X volta à receita de antes do preset.
            viewModel.clearPreset()
        } else {
            viewModel.discardPanelSession()
        }
        panelVisible = false
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (state.photoUri == null || state.preview == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center
                ) {
                    FloatingPhotoButton(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        onClick = onBack,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(LumeSpacing.sm)
                    )
                    if (state.isLoadingPhoto) {
                        CircularProgressIndicator()
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm),
                            modifier = Modifier.padding(LumeSpacing.md)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Nenhuma foto aberta",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            SupportingText(
                                text = "Abra uma foto do aparelho para começar a editar.",
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = openPicker) {
                                Text("Abrir foto")
                            }
                        }
                    }
                }
            } else {
                val preview = state.preview
                // Editado = último render do motor; sem render ainda,
                // mostra o original (primeiro quadro).
                val edited = state.editedPreview ?: preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .onSizeChanged { photoAreaSize = it }
                        .pointerInput(
                            splitMode, cropOverlayVisible, textToolActive, fullscreen
                        ) {
                            if (cropOverlayVisible) {
                                // A moldura é dona dos gestos na foto.
                                return@pointerInput
                            }
                            val pointerScope = this
                            val slopPx = with(density) { 8.dp.toPx() }
                            val doubleTapSlopPx = with(density) { 48.dp.toPx() }
                            val toolSwitchPx = with(density) { 48.dp.toPx() }
                            var lastTapUpTime = 0L
                            var lastTapPos = Offset.Zero
                            while (true) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val downPos = down.position
                                    val downTime = down.uptimeMillis
                                    // Dedo ativo do gesto (muda se, após
                                    // uma pinça, o dedo original levantar).
                                    var activeId = down.id
                                    // Toques no texto são do texto
                                    // (arrastar/pinça) — a foto ignora.
                                    if (latestIsOnText.value(downPos)) {
                                        return@awaitEachGesture
                                    }
                                    val chipBounds = latestHistogramChipBounds.value
                                    if (chipBounds != null &&
                                        chipBounds.contains(downPos)
                                    ) {
                                        return@awaitEachGesture
                                    }
                                    val hintBounds = latestHintPillBounds.value
                                    if (hintBounds != null &&
                                        hintBounds.contains(downPos)
                                    ) {
                                        return@awaitEachGesture
                                    }
                                    val isDoubleTapCandidate =
                                        downTime - lastTapUpTime < 300L &&
                                            (downPos - lastTapPos).getDistance() <
                                            doubleTapSlopPx
                                    var mode: PhotoGestureMode? = null
                                    var originalShown = false
                                    var pressJob: Job? = null
                                    // Segurar 120ms mostra a original — o
                                    // arrastar não pisca a foto. Com Texto
                                    // aberto ou no dividido, não há o que
                                    // segurar: o gesto é do texto/divisor.
                                    // No 2º toque do duplo toque, também não.
                                    if (!isDoubleTapCandidate && !textToolActive &&
                                        !splitMode
                                    ) {
                                        pressJob = pointerScope.launch {
                                            delay(120)
                                            showOriginal = true
                                            originalShown = true
                                        }
                                    }
                                    // Estado do arrastar de valor.
                                    var dragValue = 0f
                                    var dragSpec: AdjustGestureSpec? = null
                                    var lastValueTime = 0L
                                    var lastValuePos = 0f
                                    var prevValueTime = 0L
                                    var prevValuePos = 0f
                                    // Estado do trocar de ferramenta.
                                    var toolAccumY = 0f
                                    // Estado do dividido.
                                    var lastSplitTime = 0L
                                    var lastSplitPos = 0f
                                    var prevSplitTime = 0L
                                    var prevSplitPos = 0f
                                    // Estado do pan (foto com zoom).
                                    var lastPanPos = downPos
                                    // Estado da pinça.
                                    val pinchTracker = PinchTracker()
                                    var pinchStartStraighten = 0f
                                    var pinchAngleTotal = 0f
                                    var straightenActive = false

                                    fun cancelPress() {
                                        pressJob?.cancel()
                                        pressJob = null
                                        if (originalShown) {
                                            showOriginal = false
                                            originalShown = false
                                        }
                                    }

                                    fun finishGesture() {
                                        cancelPress()
                                        gestureValue = null
                                        when (mode) {
                                            PhotoGestureMode.VALUE -> {
                                                val spec = dragSpec
                                                if (spec != null) {
                                                    // Ímã no zero pela
                                                    // PROJEÇÃO do momento
                                                    // na soltura, não pelo
                                                    // ponto onde parou.
                                                    if (lastValueTime > prevValueTime) {
                                                        val dt = (
                                                            lastValueTime - prevValueTime
                                                            ).coerceAtLeast(1L)
                                                        val velocity =
                                                            (lastValuePos - prevValuePos) /
                                                                dt * 1000f
                                                        val span = spec.range.endInclusive -
                                                            spec.range.start
                                                        val projected = dragValue +
                                                            velocity * 0.15f / size.width *
                                                            span * 1.2f
                                                        if (abs(projected) <= span * 0.05f) {
                                                            spec.applyDraft(0f)
                                                            dragValue = 0f
                                                            haptics.performHapticFeedback(
                                                                HapticFeedbackType.TextHandleMove
                                                            )
                                                        }
                                                    }
                                                    viewModel.commitRecipe()
                                                }
                                            }
                                            PhotoGestureMode.SPLIT -> {
                                                if (lastSplitTime > prevSplitTime) {
                                                    val dt = (
                                                        lastSplitTime - prevSplitTime
                                                        ).coerceAtLeast(1L)
                                                    val velocity =
                                                        (lastSplitPos - prevSplitPos) /
                                                            dt * 1000f
                                                    val projected = splitFraction +
                                                        velocity * 0.15f / size.width
                                                    if (abs(projected - 0.5f) < 0.04f) {
                                                        splitFraction = 0.5f
                                                        haptics.performHapticFeedback(
                                                            HapticFeedbackType.TextHandleMove
                                                        )
                                                    }
                                                }
                                            }
                                            else -> Unit
                                        }
                                        mode = null
                                    }

                                    var gestureDone = false
                                    while (!gestureDone) {
                                        val event = awaitPointerEvent()
                                        val pressed =
                                            event.changes.filter { it.pressed }
                                        if (pressed.isEmpty()) {
                                            if (mode == null) {
                                                cancelPress()
                                                if (isDoubleTapCandidate) {
                                                    // Duplo toque: com
                                                    // zoom, volta a
                                                    // encaixar; senão,
                                                    // alterna a foto cheia.
                                                    if (zoomScale > 1f) {
                                                        zoomScale = 1f
                                                        zoomOffset = Offset.Zero
                                                    } else {
                                                        fullscreen = !fullscreen
                                                    }
                                                } else {
                                                    lastTapUpTime = event.changes
                                                        .firstOrNull()?.uptimeMillis
                                                        ?: downTime
                                                    lastTapPos = downPos
                                                }
                                            } else {
                                                finishGesture()
                                            }
                                            gestureDone = true
                                            continue
                                        }
                                        if (pressed.size >= 2 &&
                                            mode != PhotoGestureMode.PINCH
                                        ) {
                                            // Segundo dedo: pinça (zoom;
                                            // girar endireita no Corrigir).
                                            cancelPress()
                                            gestureValue = null
                                            mode = PhotoGestureMode.PINCH
                                            pinchTracker.reset()
                                            pinchStartStraighten =
                                                state.geometry.straightenDegrees
                                            pinchAngleTotal = 0f
                                            straightenActive =
                                                selectedTool == EditorTool.CROP &&
                                                    cropTab == CropTab.STRAIGHTEN
                                            continue
                                        }
                                        when (mode) {
                                            null -> {
                                                val change = pressed.firstOrNull {
                                                    it.id == activeId
                                                } ?: continue
                                                val total =
                                                    (change.position - downPos).getDistance()
                                                if (total > slopPx) {
                                                    cancelPress()
                                                    mode = when {
                                                        zoomScale > 1f ->
                                                            PhotoGestureMode.PAN
                                                        splitMode ->
                                                            PhotoGestureMode.SPLIT
                                                        selectedTool == EditorTool.ADJUST &&
                                                            latestSpec.value != null -> {
                                                            val dx = change.position.x -
                                                                downPos.x
                                                            val dy = change.position.y -
                                                                downPos.y
                                                            if (abs(dx) >= abs(dy)) {
                                                                dragSpec = latestSpec.value
                                                                dragValue =
                                                                    dragSpec?.value ?: 0f
                                                                lastValueTime =
                                                                    change.uptimeMillis
                                                                lastValuePos =
                                                                    change.position.x
                                                                prevValueTime = lastValueTime
                                                                prevValuePos = lastValuePos
                                                                dragSpec?.let {
                                                                    gestureValue = it.label to
                                                                        it.format(dragValue)
                                                                }
                                                                PhotoGestureMode.VALUE
                                                            } else {
                                                                toolAccumY = 0f
                                                                PhotoGestureMode.TOOL
                                                            }
                                                        }
                                                        else -> null
                                                    }
                                                    // Sem modo (ferramenta
                                                    // sem slider): o gesto
                                                    // continua esperando —
                                                    // a pinça ainda pode
                                                    // entrar.
                                                }
                                            }
                                            PhotoGestureMode.VALUE -> {
                                                val change = pressed.firstOrNull {
                                                    it.id == activeId
                                                } ?: continue
                                                val spec = dragSpec ?: continue
                                                val dx = change.position.x - lastValuePos
                                                val span = spec.range.endInclusive -
                                                    spec.range.start
                                                val newValue = (
                                                    dragValue + dx / size.width *
                                                        span * 1.2f
                                                    ).coerceIn(
                                                    spec.range.start,
                                                    spec.range.endInclusive
                                                )
                                                prevValueTime = lastValueTime
                                                prevValuePos = lastValuePos
                                                lastValueTime = change.uptimeMillis
                                                lastValuePos = change.position.x
                                                if (newValue != dragValue) {
                                                    dragValue = newValue
                                                    spec.applyDraft(dragValue)
                                                    gestureValue = spec.label to
                                                        spec.format(dragValue)
                                                }
                                                change.consume()
                                            }
                                            PhotoGestureMode.TOOL -> {
                                                val change = pressed.firstOrNull {
                                                    it.id == activeId
                                                } ?: continue
                                                val dy = change.position.y - lastPanPos.y
                                                toolAccumY += dy
                                                lastPanPos = change.position
                                                if (abs(toolAccumY) >= toolSwitchPx) {
                                                    val direction =
                                                        if (toolAccumY > 0) 1 else -1
                                                    val next = latestNextTool.value(
                                                        adjustSub, direction
                                                    )
                                                    if (next != adjustSub) {
                                                        adjustSub = next
                                                        // O spec da ferramenta
                                                        // nova é calculado
                                                        // aqui (o latestSpec
                                                        // só atualiza na
                                                        // próxima recomposição).
                                                        adjustGestureSpec(
                                                            state.recipe, next,
                                                            hslChannel, hslAttr, state.isPro
                                                        ) {
                                                            viewModel.updateRecipeDraft(it)
                                                        }?.let {
                                                            gestureValue = it.label to
                                                                it.format(it.value)
                                                        }
                                                    }
                                                    toolAccumY = 0f
                                                }
                                                change.consume()
                                            }
                                            PhotoGestureMode.SPLIT -> {
                                                val change = pressed.firstOrNull {
                                                    it.id == activeId
                                                } ?: continue
                                                prevSplitTime = lastSplitTime
                                                prevSplitPos = lastSplitPos
                                                lastSplitTime = change.uptimeMillis
                                                lastSplitPos = change.position.x
                                                splitFraction =
                                                    (change.position.x / size.width)
                                                        .coerceIn(0.08f, 0.92f)
                                                change.consume()
                                            }
                                            PhotoGestureMode.PAN -> {
                                                val change = pressed.firstOrNull {
                                                    it.id == activeId
                                                } ?: continue
                                                val dx = change.position.x - lastPanPos.x
                                                val dy = change.position.y - lastPanPos.y
                                                lastPanPos = change.position
                                                val maxX = (zoomScale - 1f) *
                                                    size.width / 2f
                                                val maxY = (zoomScale - 1f) *
                                                    size.height / 2f
                                                zoomOffset = Offset(
                                                    (zoomOffset.x + dx)
                                                        .coerceIn(-maxX, maxX),
                                                    (zoomOffset.y + dy)
                                                        .coerceIn(-maxY, maxY)
                                                )
                                                change.consume()
                                            }
                                            PhotoGestureMode.PINCH -> {
                                                if (pressed.size < 2) {
                                                    // Um dedo levantou: o
                                                    // restante volta a
                                                    // classificar (pan se
                                                    // ainda há zoom).
                                                    mode = null
                                                    pinchTracker.reset()
                                                    pressed.firstOrNull()?.let {
                                                        activeId = it.id
                                                        lastPanPos = it.position
                                                    }
                                                    continue
                                                }
                                                val sorted = pressed.sortedBy {
                                                    it.id.value
                                                }
                                                val delta = pinchTracker.update(
                                                    sorted[0].position,
                                                    sorted[1].position
                                                )
                                                if (delta != null) {
                                                    zoomScale = (
                                                        zoomScale * delta.scaleFactor
                                                        ).coerceIn(1f, 4f)
                                                    if (zoomScale <= 1f) {
                                                        zoomOffset = Offset.Zero
                                                    } else {
                                                        val maxX = (zoomScale - 1f) *
                                                            size.width / 2f
                                                        val maxY = (zoomScale - 1f) *
                                                            size.height / 2f
                                                        zoomOffset = Offset(
                                                            (zoomOffset.x +
                                                                delta.centroidDelta.x)
                                                                .coerceIn(-maxX, maxX),
                                                            (zoomOffset.y +
                                                                delta.centroidDelta.y)
                                                                .coerceIn(-maxY, maxY)
                                                        )
                                                    }
                                                    if (straightenActive) {
                                                        pinchAngleTotal +=
                                                            delta.angleDeltaDegrees
                                                        val straightened =
                                                            (pinchStartStraighten +
                                                                pinchAngleTotal)
                                                                .coerceIn(-10f, 10f)
                                                        viewModel.updateStraighten(
                                                            straightened
                                                        )
                                                    }
                                                }
                                                pressed.forEach { it.consume() }
                                            }
                                        }
                                    }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Zoom por pinça (0.15.6): só a foto + o texto
                    // escalam; moldura e botões ficam fora.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = zoomScale
                                scaleY = zoomScale
                                translationX = zoomOffset.x
                                translationY = zoomOffset.y
                            },
                        contentAlignment = Alignment.Center
                    ) {
                    if (preview != null && edited != null) {
                        if (splitMode) {
                            Image(
                                bitmap = preview,
                                contentDescription = "Foto em edição, lado esquerdo original.",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .drawWithContent {
                                        clipRect(left = size.width * splitFraction) {
                                            this@drawWithContent.drawContent()
                                        }
                                    }
                            ) {
                                Image(
                                    bitmap = edited,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val dividerX = maxWidth * splitFraction
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(2.dp)
                                        .offset(x = dividerX - 1.dp)
                                        .background(Color.White)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .offset(x = dividerX - 20.dp)
                                        .align(Alignment.CenterStart)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text(
                                            text = "◂ ▸",
                                            color = Color.Black,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(LumeSpacing.sm)
                            ) {
                                Text(
                                    text = "ANTES",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(
                                        horizontal = LumeSpacing.xs,
                                        vertical = LumeSpacing.xxs
                                    )
                                )
                            }
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(LumeSpacing.sm)
                            ) {
                                Text(
                                    text = "DEPOIS",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(
                                        horizontal = LumeSpacing.xs,
                                        vertical = LumeSpacing.xxs
                                    )
                                )
                            }
                        } else {
                            Image(
                                bitmap = if (showOriginal) preview else edited,
                                contentDescription = "Foto em edição. Segure para ver a original.",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    if (!textOverlay.isEmpty && !showOriginal && displayedSize != IntSize.Zero) {
                        // Texto sobre a foto (Fase 1): por cima da
                        // imagem, abaixo dos botões flutuantes. No
                        // modo dividido, o texto é edição: só no
                        // lado "depois". Arrastar na foto move o
                        // texto (só com a ferramenta Texto aberta).
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (splitMode) {
                                        Modifier.drawWithContent {
                                            clipRect(left = size.width * splitFraction) {
                                                this@drawWithContent.drawContent()
                                            }
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .offset { displayedOffset }
                                    .size(
                                        width = with(density) { displayedSize.width.toDp() },
                                        height = with(density) { displayedSize.height.toDp() }
                                    )
                                    .pointerInput(textToolActive, displayedSize) {
                                        if (!textToolActive) return@pointerInput
                                        val textScope = this
                                        while (true) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown()
                                                // Só o glifo e a alça
                                                // movem o texto; o resto
                                                // da foto é do gesto de
                                                // valor/ferramenta.
                                                val downInPhoto = down.position + Offset(
                                                    displayedOffset.x.toFloat(),
                                                    displayedOffset.y.toFloat()
                                                )
                                                if (!latestIsOnText.value(downInPhoto)) {
                                                    return@awaitEachGesture
                                                }
                                                var unclampedX = textCenterX
                                                var unclampedY = textCenterY
                                                var lastPositions =
                                                    mapOf(down.id to down.position)
                                                val pinchTracker = PinchTracker()
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    val pressed = event.changes.filter {
                                                        it.pressed
                                                    }
                                                    if (pressed.isEmpty()) break
                                                    suspend fun moveBy(dx: Float, dy: Float) {
                                                        val zoom = latestZoom.value
                                                            .coerceAtLeast(1f)
                                                        unclampedX += dx /
                                                            displayedSize.width / zoom
                                                        unclampedY += dy /
                                                            displayedSize.height / zoom
                                                        val cx = unclampedX
                                                            .coerceIn(0f, 1f)
                                                        val cy = unclampedY
                                                            .coerceIn(0f, 1f)
                                                        textCenterX = cx
                                                        textCenterY = cy
                                                        // Limite elástico: o
                                                        // excesso vira
                                                        // deslocamento
                                                        // visual (volta com
                                                        // mola na soltura).
                                                        val ox = (unclampedX - cx) *
                                                            displayedSize.width * 0.35f
                                                        val oy = (unclampedY - cy) *
                                                            displayedSize.height * 0.35f
                                                        textOvershoot.snapTo(Offset(ox, oy))
                                                    }
                                                    if (pressed.size == 1) {
                                                        val c = pressed[0]
                                                        val last = lastPositions[c.id]
                                                        if (last != null) {
                                                            moveBy(
                                                                c.position.x - last.x,
                                                                c.position.y - last.y
                                                            )
                                                        }
                                                        lastPositions =
                                                            mapOf(c.id to c.position)
                                                        pinchTracker.reset()
                                                    } else {
                                                        val sorted = pressed.sortedBy {
                                                            it.id.value
                                                        }
                                                        val delta = pinchTracker.update(
                                                            sorted[0].position,
                                                            sorted[1].position
                                                        )
                                                        if (delta != null) {
                                                            moveBy(
                                                                delta.centroidDelta.x,
                                                                delta.centroidDelta.y
                                                            )
                                                            // Pinça no texto
                                                            // muda o tamanho.
                                                            textSizeFraction =
                                                                (textSizeFraction *
                                                                    delta.scaleFactor)
                                                                    .coerceIn(0.03f, 0.20f)
                                                        }
                                                        lastPositions = sorted.associate {
                                                            it.id to it.position
                                                        }
                                                    }
                                                    pressed.forEach { it.consume() }
                                                }
                                            }
                                            textScope.launch {
                                                textOvershoot.animateTo(
                                                    Offset.Zero, spring()
                                                )
                                            }
                                        }
                                    }
                            ) {
                                Text(
                                    text = textOverlay.text,
                                    fontFamily = textOverlay.font.fontFamily(),
                                    fontSize = textFontSize,
                                    color = Color(textOverlay.colorArgb),
                                    textAlign = TextAlign.Center,
                                    style = TextStyle(
                                        shadow = Shadow(
                                            color = Color.Black.copy(alpha = 0.7f),
                                            offset = Offset(0f, 2f),
                                            blurRadius = 12f
                                        )
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .offset {
                                            IntOffset(
                                                (
                                                    (textCenterX - 0.5f) *
                                                        displayedSize.width +
                                                        textOvershoot.value.x
                                                    ).roundToInt(),
                                                (
                                                    (textCenterY - 0.5f) *
                                                        displayedSize.height +
                                                        textOvershoot.value.y
                                                    ).roundToInt()
                                            )
                                        }
                                )
                                // Alça (0.15.6): círculo abaixo do texto
                                // para arrastar; 24dp visual, 48dp de toque.
                                if (textToolActive) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .offset {
                                                IntOffset(
                                                    (
                                                        (textCenterX - 0.5f) *
                                                            displayedSize.width
                                                        ).roundToInt(),
                                                    (
                                                        (textCenterY - 0.5f) *
                                                            displayedSize.height +
                                                            textBaseSizePx * textScale / 2f +
                                                            with(density) { 24.dp.toPx() }
                                                        ).roundToInt()
                                                    )
                                            }
                                            .size(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Color.White.copy(alpha = 0.9f)
                                                )
                                                .border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    } // Fim do Box de zoom (foto + texto).
                    if (cropOverlayVisible) {
                        CropOverlay(
                            bitmapWidth = photoWidth,
                            bitmapHeight = photoHeight,
                            geometry = state.geometry,
                            onCropChange = {
                                viewModel.updateGeometry(state.geometry.copy(crop = it))
                            }
                        )
                    }
                    // Histograma sobre a foto (0.15.6): chip pequeno no
                    // canto inferior esquerdo. Toque alterna cinza/RGB;
                    // segurar esconde (a pílula "Histograma" restaura).
                    if (histogramVisible && state.histogram.isNotEmpty() && chromeVisible) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(LumeSpacing.sm)
                                .size(width = 120.dp, height = 36.dp)
                                .pointerInput(histogramRgb) {
                                    detectTapGestures(
                                        onTap = { histogramRgb = !histogramRgb },
                                        onLongPress = { histogramVisible = false }
                                    )
                                }
                        ) {
                            HistogramStrip(
                                values = state.histogram,
                                rgbValues = state.histogramRgb,
                                rgb = histogramRgb,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp)
                            )
                        }
                    } else if (!histogramVisible && chromeVisible &&
                        state.histogram.isNotEmpty()
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(LumeSpacing.sm)
                        ) {
                            TextButton(onClick = { histogramVisible = true }) {
                                Text(
                                    text = "Histograma",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    // Dica do gesto segurar-pra-ver-original: aparece
                    // uma vez só; some para sempre depois do primeiro
                    // uso (ou do toque nela).
                    if (!state.originalHintShown && chromeVisible) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 56.dp)
                        ) {
                            TextButton(
                                onClick = { viewModel.markOriginalHintShown() }
                            ) {
                                Text(
                                    text = "Segure a foto para ver a original",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                    // Valor gigante sobre a foto enquanto arrasta
                    // (também na foto cheia: é o único retorno do gesto).
                    val gestureValueNow = gestureValue
                    if (gestureValueNow != null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(
                                    horizontal = LumeSpacing.md,
                                    vertical = LumeSpacing.sm
                                )
                            ) {
                                Text(
                                    text = gestureValueNow.first,
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = gestureValueNow.second,
                                    color = Color.White,
                                    style = MaterialTheme.typography.displayMedium
                                )
                            }
                        }
                    }
                    // Controles flutuantes sobre a foto (v3): sem barra
                    // de título, a foto manda. Com o cromo visível,
                    // X/✓ nos cantos; sem ele (painel recolhido ou
                    // foto cheia), voltar + desfazer/refazer + Exportar.
                    // O X pede segurar 0,5s quando a ferramenta mudou
                    // algo; o ✓ é maior (48dp) e azul.
                    if (chromeVisible) {
                        FloatingPhotoButton(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Descartar ajustes da ferramenta",
                            onClick = {},
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(LumeSpacing.sm)
                                .holdToConfirm(
                                    requireHold = viewModel.hasPanelChanges(),
                                    onConfirm = onDiscardPanel
                                )
                        )
                        FloatingPhotoButton(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Confirmar e fechar a ferramenta",
                            onClick = onConfirmPanel,
                            size = 48.dp,
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(LumeSpacing.sm)
                        )
                    } else {
                        FloatingPhotoButton(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            onClick = onBack,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(LumeSpacing.sm)
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(LumeSpacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FloatingPhotoButton(
                                imageVector = Icons.Filled.Undo,
                                contentDescription = "Desfazer",
                                onClick = {},
                                enabled = state.canUndo,
                                modifier = Modifier.holdRepeatAction(
                                    onTap = { if (state.canUndo) viewModel.undo() },
                                    onRepeat = { if (state.canUndo) viewModel.undo() },
                                    onLongHold = {
                                        selectedTool = EditorTool.HISTORY
                                        panelVisible = true
                                    }
                                )
                            )
                            FloatingPhotoButton(
                                imageVector = Icons.Filled.Redo,
                                contentDescription = "Refazer",
                                onClick = {},
                                enabled = state.canRedo,
                                modifier = Modifier.holdRepeatAction(
                                    onTap = { if (state.canRedo) viewModel.redo() },
                                    onRepeat = { if (state.canRedo) viewModel.redo() },
                                    onLongHold = {
                                        selectedTool = EditorTool.HISTORY
                                        panelVisible = true
                                    }
                                )
                            )
                            Button(
                                onClick = { showExportSheet = true },
                                enabled = !state.isExporting,
                                contentPadding = PaddingValues(
                                    horizontal = LumeSpacing.md,
                                    vertical = 0.dp
                                ),
                                modifier = Modifier.height(40.dp)
                            ) {
                                Text("Exportar")
                            }
                        }
                    }
                    if (selectedTool == EditorTool.CROP && chromeVisible) {
                        // Medida do corte (ou da foto cheia) no topo.
                        val crop = state.geometry.crop
                        val cropWidth = crop?.width ?: 1f
                        val cropHeight = crop?.height ?: 1f
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(LumeSpacing.sm)
                        ) {
                            Text(
                                text = "${(cropWidth * photoWidth).roundToInt()} × " +
                                    "${(cropHeight * photoHeight).roundToInt()}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(
                                    horizontal = LumeSpacing.sm,
                                    vertical = LumeSpacing.xs
                                )
                            )
                        }
                    }
                    if (!cropOverlayVisible && chromeVisible) {
                        Surface(
                            color = if (splitMode) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Black.copy(alpha = 0.65f)
                            },
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(LumeSpacing.sm)
                        ) {
                            IconButton(onClick = { splitMode = !splitMode }) {
                                Icon(
                                    imageVector = Icons.Filled.Compare,
                                    contentDescription = if (splitMode) {
                                        "Voltar a segurar para comparar"
                                    } else {
                                        "Dividir antes e depois"
                                    },
                                    tint = if (splitMode) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        Color.White
                                    }
                                )
                            }
                        }
                    }
                    if (state.isExporting) {
                        CircularProgressIndicator()
                    }
                }
                val exportedUri = state.exportedUri
                if (exportedUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LumeSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Exportada para a galeria.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = {
                                ShareHelper.shareImage(context, exportedUri)
                                viewModel.clearExportedUri()
                            }
                        ) {
                            Text("Compartilhar")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = chromeVisible,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = LumeSpacing.sm)
                ) {
                    when (selectedTool) {
                        EditorTool.ADJUST -> AdjustPanel(
                            recipe = state.recipe,
                            isPro = state.isPro,
                            selectedSub = adjustSub,
                            onSubSelect = { adjustSub = it },
                            hslChannel = hslChannel,
                            onHslChannelSelect = { hslChannel = it },
                            hslAttr = hslAttr,
                            onHslAttrSelect = { hslAttr = it },
                            onDraft = { viewModel.updateRecipeDraft(it) },
                            onCommit = { viewModel.commitRecipe() },
                            onRequestPaywall = { viewModel.requestPaywall() },
                            onCompareChange = { showOriginal = it },
                            onScopedCompare = { viewModel.setCompareOverride(it) }
                        )
                        EditorTool.TEXT -> TextPanel(
                            overlay = textOverlay,
                            paletteColors = state.paletteColors,
                            onTextChange = { textOverlayText = it },
                            onFontChange = { textFont = it },
                            onSizeChange = { textSizeFraction = it },
                            onColorChange = { textColorArgb = it },
                            onColorHoldCopy = { argb ->
                                // Segurar a bolinha: copia o hex e deixa
                                // a cor como cor da ferramenta Texto.
                                textColorArgb = argb
                                val clipboard = context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText("Cor da foto", hexString(argb))
                                )
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Cor ${hexString(argb)} copiada."
                                    )
                                }
                            }
                        )
                        EditorTool.CROP -> CropPanel(
                            geometry = state.geometry,
                            photoWidth = photoWidth,
                            photoHeight = photoHeight,
                            cropTab = cropTab,
                            onCropTabChange = { cropTab = it },
                            onGeometryChange = { viewModel.updateGeometry(it) },
                            onRatioSelect = { viewModel.setCropRatio(it) },
                            onRotate = { viewModel.rotateGeometry(it) },
                            onFlip = { viewModel.flipGeometry(it) },
                            onCompareChange = { showOriginal = it }
                        )
                        EditorTool.FILTER -> FilterPanel(
                            presets = state.presets,
                            preview = state.preview,
                            appliedPresetName = state.appliedPresetName,
                            presetIntensity = state.presetIntensity,
                            onApplyPreset = { viewModel.applyPreset(it) },
                            onClearPreset = { viewModel.clearPreset() },
                            onIntensityChange = { viewModel.updatePresetIntensity(it) },
                            onIntensityFinished = { viewModel.commitPresetIntensity() },
                            onSavePresetClick = { showSaveDialog = true },
                            onSharePresetClick = { qrPreset = it },
                            onShuffleClick = {
                                val pool = state.presets.filter {
                                    it.name != state.appliedPresetName
                                }
                                val pick = pool.randomOrNull()
                                    ?: state.presets.randomOrNull()
                                if (pick != null) {
                                    shuffleTargetName = pick.name
                                    shuffleNonce++
                                    viewModel.applyPreset(pick)
                                }
                            },
                            onCompareChange = { showOriginal = it },
                            userLuts = state.userLuts,
                            selectedLutId = state.recipe.lutId,
                            lutIntensity = state.recipe.lutIntensity,
                            onApplyLut = { viewModel.applyLut(it) },
                            onLutIntensityChange = { viewModel.updateLutIntensity(it) },
                            onLutIntensityFinished = { viewModel.commitLutIntensity() },
                            onImportLutClick = { importLutLauncher.launch("*/*") },
                            onDeleteLut = { id ->
                                viewModel.deleteUserLut(id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "LUT apagado.",
                                        actionLabel = "Desfazer"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.undoDeleteUserLut()
                                    }
                                }
                            },
                            isPro = state.isPro,
                            selectedArtisticId = state.recipe.artisticEffect,
                            artisticIntensity = state.recipe.artisticIntensity,
                            onApplyArtistic = { viewModel.applyArtistic(it) },
                            onArtisticIntensityChange = { viewModel.updateArtisticIntensity(it) },
                            onArtisticIntensityFinished = { viewModel.commitArtisticIntensity() },
                            onRequestPaywall = { viewModel.requestPaywall() },
                            initialTab = pendingFilterTab ?: FilterTab.All,
                            recentPresets = state.recentPresetIds.mapNotNull { id ->
                                state.presets.find { it.id == id }
                            },
                            onPeekPreset = { preset, peek ->
                                if (peek) {
                                    viewModel.peekPreset(preset)
                                } else {
                                    viewModel.setCompareOverride(null)
                                }
                            },
                            onPeekLut = { lutId, peek ->
                                if (peek) {
                                    viewModel.peekLut(lutId)
                                } else {
                                    viewModel.setCompareOverride(null)
                                }
                            },
                            scrollToPresetName = shuffleTargetName,
                            shuffleNonce = shuffleNonce
                        )
                        EditorTool.BLEND -> BlendPanel(
                            recipe = state.recipe,
                            isPro = state.isPro,
                            hasBlendPhoto = state.hasBlendPhoto,
                            onPickBlendPhoto = { blendPhotoLauncher.launch("image/*") },
                            onClearBlendPhoto = { viewModel.clearBlendPhoto() },
                            onApplyBlendMode = { viewModel.applyBlendMode(it) },
                            onBlendOpacityChange = { viewModel.updateBlendOpacity(it) },
                            onBlendOpacityFinished = { viewModel.commitBlendOpacity() },
                            onRequestPaywall = { viewModel.requestPaywall() },
                            onCompareChange = { showOriginal = it },
                            preview = state.preview,
                            blendPreview = state.blendPreview
                        )
                        EditorTool.HISTORY -> HistoryPanel(
                            recipe = state.recipe,
                            geometry = state.geometry,
                            photoWidth = photoWidth,
                            photoHeight = photoHeight,
                            appliedPresetName = state.appliedPresetName,
                            presetIntensity = state.presetIntensity,
                            onClearField = { cleared ->
                                val before = state.recipe
                                viewModel.updateRecipeDraft(cleared)
                                viewModel.commitRecipe()
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Passo removido.",
                                        actionLabel = "Desfazer"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.updateRecipeDraft(before)
                                        viewModel.commitRecipe()
                                    }
                                }
                            },
                            onClearPreset = {
                                val beforeRecipe = state.recipe
                                val beforeName = state.appliedPresetName
                                val beforeIntensity = state.presetIntensity
                                val preset =
                                    state.presets.find { it.name == beforeName }
                                viewModel.clearPreset()
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Preset removido.",
                                        actionLabel = "Desfazer"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        if (preset != null) {
                                            viewModel.applyPreset(preset)
                                            if (beforeIntensity != 1f) {
                                                viewModel.updatePresetIntensity(
                                                    beforeIntensity
                                                )
                                                viewModel.commitPresetIntensity()
                                            }
                                        } else {
                                            viewModel.updateRecipeDraft(beforeRecipe)
                                            viewModel.commitRecipe()
                                        }
                                    }
                                }
                            },
                            onClearGeometry = {
                                val before = state.geometry
                                viewModel.resetGeometry()
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Corte removido.",
                                        actionLabel = "Desfazer"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.updateGeometry(before)
                                    }
                                }
                            },
                            onCopyEdit = { viewModel.copyCurrentEdit() },
                            onReset = {
                                val beforeRecipe = state.recipe
                                val beforeGeometry = state.geometry
                                val beforeName = state.appliedPresetName
                                val beforeIntensity = state.presetIntensity
                                val preset =
                                    state.presets.find { it.name == beforeName }
                                viewModel.resetAdjustments()
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Tudo limpo.",
                                        actionLabel = "Desfazer"
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        viewModel.updateRecipeDraft(beforeRecipe)
                                        viewModel.commitRecipe()
                                        viewModel.updateGeometry(beforeGeometry)
                                        if (preset != null) {
                                            viewModel.applyPreset(preset)
                                            if (beforeIntensity != 1f) {
                                                viewModel.updatePresetIntensity(
                                                    beforeIntensity
                                                )
                                                viewModel.commitPresetIntensity()
                                            }
                                        }
                                    }
                                }
                            },
                            lutDisplayName = lutDisplayName
                        )
                    }
                }
            }
            if (chromeVisible) {
                HorizontalDivider()
                EditorToolRail(
                    selected = selectedTool,
                    onSelect = {
                        // Tocar na ferramenta ativa fecha o painel.
                        if (it == selectedTool && panelVisible) {
                            panelVisible = false
                        } else {
                            selectedTool = it
                            panelVisible = true
                        }
                    },
                    onDragSelect = {
                        // Arrastar no trilho só troca (nunca fecha).
                        selectedTool = it
                        panelVisible = true
                    }
                )
            }
        }
    }

    if (showSaveDialog) {
        SavePresetDialog(
            onDismiss = { showSaveDialog = false },
            onConfirm = { name, collection ->
                showSaveDialog = false
                viewModel.savePreset(name, collection)
            }
        )
    }

    if (showExportSheet) {
        ExportSheet(
            onDismiss = { showExportSheet = false },
            onSave = {
                showExportSheet = false
                viewModel.exportPhoto(textOverlay.takeIf { !it.isEmpty })
            }
        )
    }

    val qr = qrPreset
    if (qr != null) {
        PresetQrDialog(
            preset = qr,
            onDismiss = { qrPreset = null },
            onCopyCode = { json ->
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Preset Solarize", json))
                qrPreset = null
                scope.launch {
                    snackbarHostState.showSnackbar("Código do preset copiado.")
                }
            }
        )
    }
}

/**
 * Botão redondo translúcido sobre a foto (v3): 40.dp visuais, área
 * de toque mínima do Material 3 por dentro. O ✓ de confirmar é
 * maior (48dp) e azul (primary).
 */
@Composable
private fun FloatingPhotoButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    backgroundColor: Color = Color.Black.copy(alpha = 0.65f),
    contentColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    Surface(
        color = backgroundColor,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.size(size)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = contentColor.copy(alpha = if (enabled) 1f else 0.4f)
            )
        }
    }
}

@Composable
private fun SavePresetDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, collection: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var collection by remember { mutableStateOf("Meus presets") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salvar como preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do preset") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = collection,
                    onValueChange = { collection = it },
                    label = { Text("Coleção") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, collection) },
                enabled = name.isNotBlank()
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportSheet(
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LumeSpacing.md, vertical = LumeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
        ) {
            Text(
                text = "Exportar",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = "JPEG · Qualidade 92% · Pasta Solarize na galeria",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "A foto original nunca é alterada. Outros formatos " +
                    "chegam nas próximas fases.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancelar")
                }
                Button(onClick = onSave) {
                    Text("Salvar na galeria")
                }
            }
        }
    }
}

@Composable
private fun PresetQrDialog(
    preset: Preset,
    onDismiss: () -> Unit,
    onCopyCode: (String) -> Unit
) {
    val json = remember(preset) { RecipeJson.encodePreset(preset) }
    val qrBitmap = remember(json) { PresetQr.createBitmap(json) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preset.name) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm),
                modifier = Modifier.fillMaxWidth()
            ) {
                val bitmap = qrBitmap
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR code do preset",
                        modifier = Modifier.size(200.dp)
                    )
                } else {
                    Text(
                        text = "Não foi possível gerar o QR deste preset.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Quem ler o QR recebe o JSON do preset. Para " +
                        "importar, salve o código como arquivo .json e use " +
                        "Importar presets na Biblioteca.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopyCode(json) }) {
                Text("Copiar código")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}
