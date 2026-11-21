package app.lume.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.core.BuiltInLuts
import app.lume.core.Preset
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
import kotlinx.coroutines.launch
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

    // A moldura do Cortar só existe com o painel aberto na aba CORTAR:
    // o render suspende o corte e a moldura mostra o corte sobre a
    // foto cheia. Fora daqui, o render aplica a geometria completa.
    val cropOverlayVisible = selectedTool == EditorTool.CROP &&
        cropTab == CropTab.CROP && panelVisible && state.preview != null
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

    // Com a ferramenta Texto aberta, o gesto na foto é do texto
    // (arrastar move) — o segurar-para-comparar fica desligado.
    val textToolActive = selectedTool == EditorTool.TEXT

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
                        .pointerInput(splitMode, cropOverlayVisible, textToolActive) {
                            if (cropOverlayVisible) {
                                // A moldura é dona dos gestos na foto.
                                return@pointerInput
                            }
                            if (!splitMode) {
                                // Com a ferramenta Texto aberta, o toque
                                // não mostra a original: o gesto é do texto.
                                if (!textToolActive) {
                                    detectTapGestures(
                                        onPress = {
                                            showOriginal = true
                                            try {
                                                awaitRelease()
                                            } finally {
                                                showOriginal = false
                                            }
                                        }
                                    )
                                }
                            } else {
                                detectDragGestures { change, _ ->
                                    splitFraction = (change.position.x / size.width.toFloat())
                                        .coerceIn(0.08f, 0.92f)
                                }
                            }
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
                                        if (textToolActive) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                textCenterX = (
                                                    textCenterX +
                                                        dragAmount.x / displayedSize.width
                                                    ).coerceIn(0f, 1f)
                                                textCenterY = (
                                                    textCenterY +
                                                        dragAmount.y / displayedSize.height
                                                    ).coerceIn(0f, 1f)
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
                                                ((textCenterX - 0.5f) * displayedSize.width)
                                                    .roundToInt(),
                                                ((textCenterY - 0.5f) * displayedSize.height)
                                                    .roundToInt()
                                            )
                                        }
                                )
                            }
                        }
                    }
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
                    // Controles flutuantes sobre a foto (v3): sem barra
                    // de título, a foto manda. Com o painel aberto,
                    // X/✓ nos cantos; com o painel recolhido, voltar +
                    // desfazer/refazer + Exportar.
                    if (panelVisible) {
                        FloatingPhotoButton(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Descartar ajustes da ferramenta",
                            onClick = onDiscardPanel,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(LumeSpacing.sm)
                        )
                        FloatingPhotoButton(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Confirmar e fechar a ferramenta",
                            onClick = onConfirmPanel,
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
                                onClick = { viewModel.undo() },
                                enabled = state.canUndo
                            )
                            FloatingPhotoButton(
                                imageVector = Icons.Filled.Redo,
                                contentDescription = "Refazer",
                                onClick = { viewModel.redo() },
                                enabled = state.canRedo
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
                    if (selectedTool == EditorTool.CROP && panelVisible) {
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
                    if (!cropOverlayVisible) {
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

            if (panelVisible) {
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
                            histogram = state.histogram,
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
                            paletteColors = state.paletteColors,
                            selectedTextColor = textColorArgb,
                            onPaletteColorSelect = { argb ->
                                // Toque na amostra: copia o hex e deixa
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
                        EditorTool.TEXT -> TextPanel(
                            overlay = textOverlay,
                            paletteColors = state.paletteColors,
                            onTextChange = { textOverlayText = it },
                            onFontChange = { textFont = it },
                            onSizeChange = { textSizeFraction = it },
                            onColorChange = { textColorArgb = it }
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
                            onDeleteLut = { viewModel.deleteUserLut(it) },
                            isPro = state.isPro,
                            selectedArtisticId = state.recipe.artisticEffect,
                            artisticIntensity = state.recipe.artisticIntensity,
                            onApplyArtistic = { viewModel.applyArtistic(it) },
                            onArtisticIntensityChange = { viewModel.updateArtisticIntensity(it) },
                            onArtisticIntensityFinished = { viewModel.commitArtisticIntensity() },
                            onRequestPaywall = { viewModel.requestPaywall() },
                            initialTab = pendingFilterTab ?: FilterTab.All
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
                            onCompareChange = { showOriginal = it }
                        )
                        EditorTool.HISTORY -> HistoryPanel(
                            recipe = state.recipe,
                            geometry = state.geometry,
                            photoWidth = photoWidth,
                            photoHeight = photoHeight,
                            appliedPresetName = state.appliedPresetName,
                            presetIntensity = state.presetIntensity,
                            onClearField = {
                                viewModel.updateRecipeDraft(it)
                                viewModel.commitRecipe()
                            },
                            onClearPreset = { viewModel.clearPreset() },
                            onClearGeometry = { viewModel.resetGeometry() },
                            onCopyEdit = { viewModel.copyCurrentEdit() },
                            onReset = { viewModel.resetAdjustments() },
                            lutDisplayName = lutDisplayName
                        )
                    }
                }
            }
            HorizontalDivider()
            EditorToolRail(
                selected = selectedTool,
                onSelect = {
                    selectedTool = it
                    panelVisible = true
                }
            )
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
 * de toque mínima do Material 3 por dentro.
 */
@Composable
private fun FloatingPhotoButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.size(40.dp)
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.4f)
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
