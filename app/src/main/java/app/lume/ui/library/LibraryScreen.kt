package app.lume.ui.library

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.data.Draft
import app.lume.imaging.BitmapLoader
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

@OptIn(ExperimentalMaterial3Api::class)
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
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = LumeSpacing.md,
                vertical = LumeSpacing.sm
            ),
            verticalArrangement = Arrangement.spacedBy(LumeSpacing.lg)
        ) {
            item {
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

            if (state.drafts.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(LumeSpacing.xs)) {
                        SectionTitle("Continuar editando")
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
                        ) {
                            items(items = state.drafts, key = { it.id }) { draft ->
                                DraftCard(
                                    draft = draft,
                                    onOpen = {
                                        onOpenEditor(Uri.parse(draft.photoUri))
                                    },
                                    onDelete = { viewModel.deleteDraft(draft.id) }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SectionTitle("Presets")
            }
            items(items = state.presets, key = { it.id }) { preset ->
                Card(
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Abra uma foto para aplicar o preset \"${preset.name}\"."
                            )
                        }
                    },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(LumeSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = preset.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = preset.collection,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (!preset.isBuiltIn) {
                            IconButton(onClick = { viewModel.deletePreset(preset.id) }) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Apagar preset"
                                )
                            }
                        }
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
private fun DraftCard(
    draft: Draft,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onOpen,
        modifier = modifier.width(128.dp)
    ) {
        Column {
            DraftThumbnail(
                photoUri = draft.photoUri,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
            )
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
