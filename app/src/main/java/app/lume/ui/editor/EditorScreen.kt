package app.lume.ui.editor

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.core.RecipeColorMatrix
import app.lume.imaging.ShareHelper
import app.lume.ui.components.ShowMessageAsSnackbar
import app.lume.ui.components.SupportingText
import app.lume.ui.components.rememberPhotoPickerLauncher
import app.lume.ui.theme.LumeSpacing

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
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showOriginal by remember { mutableStateOf(false) }

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

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Editor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = state.canUndo
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Undo,
                            contentDescription = "Desfazer"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = state.canRedo
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Redo,
                            contentDescription = "Refazer"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.exportPhoto() },
                        enabled = state.photoUri != null && !state.isExporting
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Exportar"
                        )
                    }
                }
            )
        },
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
                val colorFilter = remember(state.recipe, showOriginal) {
                    if (showOriginal || state.recipe.isIdentity()) {
                        null
                    } else {
                        ColorFilter.colorMatrix(
                            ColorMatrix(RecipeColorMatrix.values(state.recipe))
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .pointerInput(Unit) {
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (preview != null) {
                        Image(
                            bitmap = preview,
                            contentDescription = "Foto em edição. Segure para ver a original.",
                            contentScale = ContentScale.Fit,
                            colorFilter = colorFilter,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    if (state.isExporting) {
                        CircularProgressIndicator()
                    }
                }
                Text(
                    text = "Segure a foto para comparar com a original.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = LumeSpacing.xs)
                )
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

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Luz") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Cor") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Presets") }
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(288.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = LumeSpacing.sm)
            ) {
                when (selectedTab) {
                    0 -> LightPanel(
                        recipe = state.recipe,
                        onDraft = { viewModel.updateRecipeDraft(it) },
                        onCommit = { viewModel.commitRecipe() },
                        onReset = { viewModel.resetAdjustments() }
                    )
                    1 -> ColorPanel(
                        recipe = state.recipe,
                        onDraft = { viewModel.updateRecipeDraft(it) },
                        onCommit = { viewModel.commitRecipe() },
                        onReset = { viewModel.resetAdjustments() }
                    )
                    else -> PresetsPanel(
                        presets = state.presets,
                        appliedPresetName = state.appliedPresetName,
                        presetIntensity = state.presetIntensity,
                        onApplyPreset = { viewModel.applyPreset(it) },
                        onIntensityChange = { viewModel.updatePresetIntensity(it) },
                        onIntensityFinished = { viewModel.commitPresetIntensity() },
                        onSavePresetClick = { showSaveDialog = true }
                    )
                }
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
