package app.lume.ui.library

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.core.Preset
import app.lume.core.RecipeColorMatrix
import app.lume.data.Draft
import app.lume.imaging.BitmapLoader
import app.lume.ui.components.PresetSwatch
import app.lume.ui.components.SectionTitle
import app.lume.ui.components.ShowMessageAsSnackbar
import app.lume.ui.components.SupportingText
import app.lume.ui.components.VerticalGap
import app.lume.ui.components.rememberPhotoPickerLauncher
import app.lume.ui.theme.LumeSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenEditor: (Uri) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var selectedCollection by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    val collections = remember(state.presets) {
        state.presets.map { it.collection }.distinct()
    }
    val visiblePresets = remember(state.presets, selectedCollection) {
        if (selectedCollection == null) {
            state.presets
        } else {
            state.presets.filter { it.collection == selectedCollection }
        }
    }

    val openPicker = rememberPhotoPickerLauncher { uri ->
        onOpenEditor(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.importPresets(context, uri)
        }
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
                title = { Text("Lume") },
                actions = {
                    IconButton(onClick = onOpenAccount) {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Conta"
                        )
                    }
                    IconButton(onClick = onOpenPaywall) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Lume Pro"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("library_list")
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = LumeSpacing.md,
                vertical = LumeSpacing.sm
            ),
            verticalArrangement = Arrangement.spacedBy(LumeSpacing.lg)
        ) {
            item {
                val firstDraft = state.drafts.firstOrNull()
                if (firstDraft != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        DraftThumbnail(
                            photoUri = firstDraft.photoUri,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f))
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(LumeSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
                        ) {
                            Text(
                                text = "Editar uma foto",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White
                            )
                            Text(
                                text = "Sem conta, sem anúncio, sem marca d'água. " +
                                    "Sua foto original nunca é alterada.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
                            ) {
                                Button(onClick = openPicker) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null
                                    )
                                    Text("Abrir foto")
                                }
                                OutlinedButton(
                                    onClick = { importLauncher.launch("application/json") }
                                ) {
                                    Text("Importar presets")
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(LumeSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
                        ) {
                            Text(
                                text = "Editar uma foto",
                                style = MaterialTheme.typography.titleLarge
                            )
                            SupportingText(
                                text = "Sem conta, sem anúncio, sem marca d'água. " +
                                    "Sua foto original nunca é alterada."
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)) {
                                Button(onClick = openPicker) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null
                                    )
                                    Text("Abrir foto")
                                }
                                OutlinedButton(
                                    onClick = { importLauncher.launch("application/json") }
                                ) {
                                    Text("Importar presets")
                                }
                            }
                        }
                    }
                }
            }

            if (state.drafts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SectionTitle("Continuar editando")
                            TextButton(
                                onClick = {
                                    if (selectionMode) {
                                        selectionMode = false
                                        selectedIds = emptySet()
                                    } else {
                                        selectionMode = true
                                    }
                                }
                            ) {
                                Text(if (selectionMode) "Cancelar" else "Selecionar")
                            }
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
                        ) {
                            items(items = state.drafts, key = { it.id }) { draft ->
                                DraftCard(
                                    draft = draft,
                                    selected = draft.id in selectedIds,
                                    selectionMode = selectionMode,
                                    onOpen = {
                                        if (selectionMode) {
                                            selectedIds = if (draft.id in selectedIds) {
                                                selectedIds - draft.id
                                            } else {
                                                selectedIds + draft.id
                                            }
                                        } else {
                                            onOpenEditor(Uri.parse(draft.photoUri))
                                        }
                                    },
                                    onDelete = { viewModel.deleteDraft(draft.id) }
                                )
                            }
                        }
                    }
                }
            }

            if (selectionMode && selectedIds.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
                        ) {
                            TextButton(
                                onClick = {
                                    selectionMode = false
                                    selectedIds = emptySet()
                                }
                            ) {
                                Text("Cancelar")
                            }
                            Button(
                                onClick = {
                                    viewModel.applyCopiedEdit(selectedIds)
                                    selectionMode = false
                                    selectedIds = emptySet()
                                },
                                enabled = state.hasCopiedEdit
                            ) {
                                Text("Aplicar edição copiada (${selectedIds.size})")
                            }
                        }
                        if (!state.hasCopiedEdit) {
                            SupportingText(
                                text = "Copie uma edição no Editor primeiro " +
                                    "(botão Copiar edição)."
                            )
                        }
                    }
                }
            }

            item {
                SectionTitle("Presets")
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCollection == null,
                            onClick = { selectedCollection = null },
                            label = { Text("Todos") }
                        )
                    }
                    items(collections) { collection ->
                        FilterChip(
                            selected = selectedCollection == collection,
                            onClick = { selectedCollection = collection },
                            label = { Text(collection) }
                        )
                    }
                }
            }
            item {
                // Os cartões mostram a foto do primeiro rascunho com o
                // visual do preset (como no Polish). Sem rascunho, a
                // amostra de cores volta como reserva.
                val sample = rememberSampleBitmap(
                    state.drafts.firstOrNull()?.photoUri
                )
                FlowRow(
                    maxItemsInEachRow = 2,
                    horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
                ) {
                    visiblePresets.forEach { preset ->
                        PresetGridCard(
                            preset = preset,
                            sample = sample,
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        "Abra uma foto para aplicar o preset \"${preset.name}\"."
                                    )
                                }
                            },
                            onDelete = if (preset.isBuiltIn) {
                                null
                            } else {
                                { viewModel.deletePreset(preset.id) }
                            }
                        )
                    }
                }
            }

            if (!state.isPro) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(LumeSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)
                        ) {
                            Text(
                                text = "Lume Pro",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Presets ilimitados, máscaras, lote e mais. " +
                                    "Veja o que entra em cada plano.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Button(onClick = onOpenPaywall) {
                                Text("Ver planos")
                            }
                        }
                    }
                }
            }

            item {
                VerticalGap()
            }
        }
    }
}

@Composable
private fun PresetGridCard(
    preset: Preset,
    sample: ImageBitmap?,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // Miniatura rápida pela matriz (aproximação v1): o visual
    // completo do preset só aparece no Editor e na exportação.
    val filter = remember(preset.recipe) {
        ColorFilter.colorMatrix(
            ColorMatrix(RecipeColorMatrix.values(preset.recipe))
        )
    }
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = modifier.fillMaxWidth(0.48f)
    ) {
        Column {
            if (sample != null) {
                Image(
                    bitmap = sample,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = filter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                )
            } else {
                PresetSwatch(
                    preset = preset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(84.dp)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = LumeSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = LumeSpacing.xs)
                ) {
                    Text(
                        text = preset.name,
                        style = MaterialTheme.typography.titleSmall,
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
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Apagar preset"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: Draft,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = modifier.width(160.dp)
    ) {
        Column {
            Box {
                DraftThumbnail(
                    photoUri = draft.photoUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp)
                )
                if (!draft.recipe.isIdentity()) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.65f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(LumeSpacing.xs)
                    ) {
                        Text(
                            text = "Editada",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(
                                horizontal = LumeSpacing.xs,
                                vertical = LumeSpacing.xxs
                            )
                        )
                    }
                }
                if (selected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(LumeSpacing.xs)
                            .size(24.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selecionado",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = LumeSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDraftDate(draft.updatedAtMillis),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f)
                )
                if (!selectionMode) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Apagar rascunho"
                        )
                    }
                }
            }
        }
    }
}

/**
 * A foto de amostra dos cartões de preset: o primeiro rascunho,
 * decodificado uma vez. Nulo sem rascunho ou se a foto sumiu.
 */
@Composable
private fun rememberSampleBitmap(photoUri: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photoUri) {
        if (photoUri == null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            try {
                BitmapLoader.decodePreview(context, Uri.parse(photoUri))
            } catch (e: Exception) {
                null
            }
        }
        bitmap = loaded
    }

    return bitmap?.asImageBitmap()
}

@Composable
private fun DraftThumbnail(photoUri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(photoUri) { mutableStateOf(false) }

    LaunchedEffect(photoUri) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                BitmapLoader.decodePreview(context, Uri.parse(photoUri))
            } catch (e: Exception) {
                null
            }
        }
        if (loaded == null) {
            failed = true
        } else {
            bitmap = loaded
        }
    }

    val loadedBitmap = bitmap
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (loadedBitmap != null) {
            Image(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = "Miniatura do rascunho",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = if (failed) "Foto indisponível" else null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDraftDate(millis: Long): String {
    // Nunca java.time (minSdk 24). SimpleDateFormat + catch (Throwable).
    return try {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(millis))
    } catch (t: Throwable) {
        "Rascunho"
    }
}
