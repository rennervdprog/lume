package app.lume.ui.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.core.Preset
import app.lume.core.RecipeJson
import app.lume.imaging.PresetQr
import app.lume.imaging.ShareHelper
import app.lume.ui.components.ShowMessageAsSnackbar
import app.lume.ui.components.SupportingText
import app.lume.ui.components.rememberPhotoPickerLauncher
import app.lume.ui.theme.LumeSpacing
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
    var selectedTool by remember { mutableStateOf(EditorTool.LIGHT) }
    var simpleMode by remember { mutableStateOf(true) }
    var splitMode by remember { mutableStateOf(false) }
    var splitFraction by remember { mutableFloatStateOf(0.5f) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    var qrPreset by remember { mutableStateOf<Preset?>(null) }
    var showOriginal by remember { mutableStateOf(false) }
    var panelVisible by remember { mutableStateOf(true) }
    var cropTab by remember { mutableStateOf(CropTab.CROP) }
    var lightSub by remember { mutableStateOf("exposure") }
    var colorSub by remember { mutableStateOf("temperature") }
    var effectsSub by remember { mutableStateOf("clarity") }
    var hslChannel by remember { mutableIntStateOf(0) }
    var hslAttr by remember { mutableStateOf("hue") }

    val openPicker = rememberPhotoPickerLauncher { uri ->
        viewModel.openPhoto(uri)
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

    val onConfirmPanel: () -> Unit = {
        viewModel.confirmPanelSession()
        panelVisible = false
    }
    val onDiscardPanel: () -> Unit = {
        if (selectedTool == EditorTool.PRESETS) {
            // No Presets, o X volta à receita de antes do preset.
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
                        .pointerInput(splitMode, cropOverlayVisible) {
                            if (cropOverlayVisible) {
                                // A moldura é dona dos gestos na foto.
                                return@pointerInput
                            }
                            if (!splitMode) {
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
                        EditorTool.LIGHT -> LightPanel(
                            recipe = state.recipe,
                            histogram = state.histogram,
                            simpleMode = simpleMode,
                            onSimpleModeChange = { simpleMode = it },
                            selectedSub = lightSub,
                            onSubSelect = { lightSub = it },
                            onDraft = { viewModel.updateRecipeDraft(it) },
                            onCommit = { viewModel.commitRecipe() }
                        )
                        EditorTool.COLOR -> ColorPanel(
                            recipe = state.recipe,
                            isPro = state.isPro,
                            selectedSub = colorSub,
                            onSubSelect = { colorSub = it },
                            hslChannel = hslChannel,
                            onHslChannelSelect = { hslChannel = it },
                            hslAttr = hslAttr,
                            onHslAttrSelect = { hslAttr = it },
                            onDraft = { viewModel.updateRecipeDraft(it) },
                            onCommit = { viewModel.commitRecipe() },
                            onRequestPaywall = { viewModel.requestPaywall() }
                        )
                        EditorTool.EFFECTS -> EffectsPanel(
                            recipe = state.recipe,
                            selectedSub = effectsSub,
                            onSubSelect = { effectsSub = it },
                            onDraft = { viewModel.updateRecipeDraft(it) },
                            onCommit = { viewModel.commitRecipe() }
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
                            onFlip = { viewModel.flipGeometry(it) }
                        )
                        EditorTool.PRESETS -> PresetsPanel(
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
                            }
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
                            onReset = { viewModel.resetAdjustments() }
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
                viewModel.exportPhoto()
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
                clipboard.setPrimaryClip(ClipData.newPlainText("Preset Lume", json))
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
                text = "JPEG · Qualidade 92% · Pasta Lume na galeria",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "A foto original nunca é alterada. Outros formatos " +
                    "chegam na Fase 1.",
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
