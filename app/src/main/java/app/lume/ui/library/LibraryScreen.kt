package app.lume.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.lume.R
import app.lume.core.Preset
import app.lume.core.RecipeColorMatrix
import app.lume.data.Draft
import app.lume.data.DraftThumbnails
import app.lume.data.photos.DevicePhoto
import app.lume.data.photos.DevicePhotos
import app.lume.data.photos.groupByDay
import app.lume.ui.editor.EditorTool
import app.lume.ui.editor.FilterTab
import app.lume.ui.editor.PendingEditorTarget
import app.lume.imaging.BitmapLoader
import app.lume.ui.components.PresetSwatch
import app.lume.ui.components.SectionTitle
import app.lume.ui.components.SupportingText
import app.lume.ui.theme.LumeSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** O preset que a demonstração da aba Presets mostra (antes/depois). */
private const val BANNER_PRESET_ID = "builtin-pele-dourada"

/**
 * Barra do topo das abas Fotos e Presets: a marca "Lume." à
 * esquerda e a pílula do Pro à direita. A conta vive na navegação
 * inferior (aba Conta), não mais aqui.
 */
@Composable
private fun HomeTopBar(
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("home_wordmark")
        ) {
            Text(
                text = "Lume",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = ".",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Surface(
            onClick = onOpenPaywall,
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .height(32.dp)
                .semantics { contentDescription = "Lume Pro" }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = LumeSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Pro",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

/**
 * Aba Fotos: busca, "Continuar editando" (ou o cartão "Suas
 * edições" quando não há rascunho), os atalhos de "Começar" e a
 * grade de fotos do aparelho agrupada por data — a tela inicial
 * do app. A grade só existe com a permissão de fotos; sem ela, a
 * aba mostra a explicação, o estado negado ou o aviso de acesso
 * parcial, e o botão + abre sempre o seletor do sistema (que não
 * precisa de permissão).
 */

/** Atalhos de "Começar" (Home v5): só o que o Lume já faz. */
private val startActions = listOf(
    StartAction("Abrir foto", null, null),
    StartAction(
        "Aplicar preset",
        R.drawable.sample_portrait,
        PendingEditorTarget.Target(EditorTool.FILTER)
    ),
    StartAction(
        "Cortar",
        R.drawable.sample_landscape,
        PendingEditorTarget.Target(EditorTool.CROP)
    ),
    StartAction(
        "Ajustar",
        R.drawable.sample_street,
        PendingEditorTarget.Target(EditorTool.ADJUST)
    ),
    StartAction(
        "LUT",
        R.drawable.sample_coffee,
        PendingEditorTarget.Target(
            EditorTool.FILTER,
            filterTab = FilterTab.Luts
        )
    )
)

private data class StartAction(
    val label: String,
    @DrawableRes val thumbRes: Int?,
    val target: PendingEditorTarget.Target?
)

@Composable
fun PhotosTab(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    permissionState: PhotoPermissionState,
    requestPhotoPermission: () -> Unit,
    onOpenEditor: (Uri) -> Unit,
    onPickPhoto: () -> Unit,
    onPickWithTarget: (PendingEditorTarget.Target?) -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var query by remember { mutableStateOf("") }
    var photos by remember { mutableStateOf<List<DevicePhoto>>(emptyList()) }

    val status = permissionState.status
    val gridVisible = status == PhotoPermissionStatus.GRANTED ||
        status == PhotoPermissionStatus.PARTIAL
    val showEducation = status == PhotoPermissionStatus.NOT_ASKED &&
        !permissionState.educationDismissed

    LaunchedEffect(status) {
        if (gridVisible) {
            photos = DevicePhotos.query(context)
        }
    }

    // Volta ao app (Configurações, janela de permissão): a seleção
    // parcial pode ter crescido sem o status mudar — reconsulta.
    // O efeito de ciclo de vida não é coroutine: a consulta (suspensa)
    // roda no escopo da tela.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val current = permissionState.status
        if (current == PhotoPermissionStatus.GRANTED ||
            current == PhotoPermissionStatus.PARTIAL
        ) {
            scope.launch {
                photos = DevicePhotos.query(context)
            }
        }
    }

    val draftUris = remember(state.drafts) {
        state.drafts.map { it.photoUri }.toSet()
    }
    val visiblePhotos = remember(photos, query) {
        if (query.isBlank()) {
            photos
        } else {
            photos.filter { it.displayName.contains(query, ignoreCase = true) }
        }
    }
    // Grade agrupada por dia, sem busca: com busca, a lista é plana
    // (o grupo de um resultado só não ajuda ninguém).
    val groups = remember(visiblePhotos, query) {
        if (query.isBlank()) {
            groupByDay(visiblePhotos) { it.dateAddedSeconds }
        } else {
            emptyList()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(onOpenPaywall = onOpenPaywall)
            when {
                showEducation -> {
                    PhotoPermissionEducation(
                        onAllow = requestPhotoPermission,
                        onNotNow = { permissionState.dismissEducation() },
                        modifier = Modifier.weight(1f)
                    )
                }
                status == PhotoPermissionStatus.DENIED -> {
                    PhotoPermissionDenied(
                        canReask = permissionState.canReask,
                        onAction = {
                            if (permissionState.canReask) {
                                requestPhotoPermission()
                            } else {
                                permissionState.openAppSettings()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                status == PhotoPermissionStatus.NOT_ASKED -> {
                    // Explicação dispensada com "Agora não": o sistema
                    // ainda mostraria a janela (nunca pedida), então o
                    // botão pede de novo em vez de ir às Configurações.
                    PhotoPermissionDenied(
                        canReask = true,
                        onAction = requestPhotoPermission,
                        modifier = Modifier.weight(1f)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(2.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("photos_grid")
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            PhotoSearchField(
                                query = query,
                                onQueryChange = { query = it },
                                modifier = Modifier.padding(
                                    start = LumeSpacing.md,
                                    end = LumeSpacing.md,
                                    top = LumeSpacing.xs,
                                    bottom = LumeSpacing.xs
                                )
                            )
                        }
                        if (status == PhotoPermissionStatus.PARTIAL) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                PartialAccessNotice(
                                    count = photos.size,
                                    onAddMore = requestPhotoPermission,
                                    modifier = Modifier.padding(
                                        start = LumeSpacing.md,
                                        end = LumeSpacing.md,
                                        bottom = LumeSpacing.xs
                                    )
                                )
                            }
                        }
                        if (state.drafts.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier.padding(top = LumeSpacing.sm)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = LumeSpacing.md),
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
                                            Text(
                                                if (selectionMode) {
                                                    "Cancelar"
                                                } else {
                                                    "Selecionar"
                                                }
                                            )
                                        }
                                    }
                                    LazyRow(
                                        contentPadding = PaddingValues(
                                            horizontal = LumeSpacing.md
                                        ),
                                        horizontalArrangement = Arrangement.spacedBy(
                                            LumeSpacing.sm
                                        )
                                    ) {
                                        items(
                                            items = state.drafts,
                                            key = { it.id }
                                        ) { draft ->
                                            DraftCard(
                                                draft = draft,
                                                selected = draft.id in selectedIds,
                                                selectionMode = selectionMode,
                                                onOpen = {
                                                    if (selectionMode) {
                                                        selectedIds =
                                                            if (draft.id in selectedIds) {
                                                                selectedIds - draft.id
                                                            } else {
                                                                selectedIds + draft.id
                                                            }
                                                    } else {
                                                        onOpenEditor(Uri.parse(draft.photoUri))
                                                    }
                                                },
                                                onDelete = {
                                                    viewModel.deleteDraft(draft.id)
                                                    DraftThumbnails.delete(
                                                        context,
                                                        draft.photoUri
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    if (selectionMode && selectedIds.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.padding(
                                                start = LumeSpacing.md,
                                                end = LumeSpacing.md,
                                                top = LumeSpacing.sm
                                            ),
                                            horizontalArrangement = Arrangement.spacedBy(
                                                LumeSpacing.sm
                                            )
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
                                                Text(
                                                    "Aplicar edição copiada " +
                                                        "(${selectedIds.size})"
                                                )
                                            }
                                        }
                                        if (!state.hasCopiedEdit) {
                                            SupportingText(
                                                text = "Copie uma edição no Editor primeiro " +
                                                    "(botão Copiar edição).",
                                                modifier = Modifier.padding(
                                                    horizontal = LumeSpacing.md
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        if (state.drafts.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier.padding(top = LumeSpacing.sm)
                                ) {
                                    SectionTitle(
                                        "Suas edições",
                                        modifier = Modifier.padding(
                                            horizontal = LumeSpacing.md
                                        )
                                    )
                                    Card(
                                        modifier = Modifier.padding(
                                            start = LumeSpacing.md,
                                            end = LumeSpacing.md,
                                            top = LumeSpacing.xs
                                        ),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme
                                                .surfaceContainer
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.surfaceContainerHighest
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(LumeSpacing.md),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = "Nenhuma edição em andamento",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Escolha uma foto e comece a editar. " +
                                                    "Suas edições aparecem aqui para você " +
                                                    "continuar depois.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.padding(top = LumeSpacing.xs)
                                            )
                                            Button(
                                                onClick = onPickPhoto,
                                                modifier = Modifier.padding(top = LumeSpacing.sm)
                                            ) {
                                                Text("Editar foto")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                modifier = Modifier.padding(top = LumeSpacing.sm)
                            ) {
                                SectionTitle(
                                    "Começar",
                                    modifier = Modifier.padding(
                                        horizontal = LumeSpacing.md
                                    )
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(
                                        horizontal = LumeSpacing.md
                                    ),
                                    horizontalArrangement = Arrangement.spacedBy(
                                        LumeSpacing.sm
                                    ),
                                    modifier = Modifier
                                        .padding(top = LumeSpacing.xs)
                                        .testTag("start_row")
                                ) {
                                    items(
                                        items = startActions,
                                        key = { it.label }
                                    ) { action ->
                                        StartTile(
                                            action = action,
                                            onPickWithTarget = onPickWithTarget
                                        )
                                    }
                                }
                            }
                        }
                        if (query.isBlank()) {
                            if (groups.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = "Nenhuma foto encontrada neste aparelho.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            horizontal = LumeSpacing.md,
                                            vertical = LumeSpacing.lg
                                        )
                                    )
                                }
                            }
                            for (group in groups) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    DateHeader(
                                        label = group.label,
                                        count = group.items.size
                                    )
                                }
                                items(
                                    items = group.items,
                                    key = { it.id }
                                ) { photo ->
                                    DevicePhotoCell(
                                        photo = photo,
                                        edited = photo.uri.toString() in draftUris,
                                        onClick = { onOpenEditor(photo.uri) }
                                    )
                                }
                            }
                        } else {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SectionTitle(
                                    "Suas fotos",
                                    modifier = Modifier.padding(
                                        start = LumeSpacing.md,
                                        top = LumeSpacing.md
                                    )
                                )
                            }
                            if (visiblePhotos.isEmpty()) {
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Text(
                                        text = "Nenhuma foto com esse nome.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(
                                            horizontal = LumeSpacing.md,
                                            vertical = LumeSpacing.lg
                                        )
                                    )
                                }
                            }
                            items(
                                items = visiblePhotos,
                                key = { it.id }
                            ) { photo ->
                                DevicePhotoCell(
                                    photo = photo,
                                    edited = photo.uri.toString() in draftUris,
                                    onClick = { onOpenEditor(photo.uri) }
                                )
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Lume — feito para quem vive de foto.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 42.dp, bottom = 52.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        // O botão + está em todos os estados da aba: ele abre o
        // seletor do sistema, que não precisa de permissão nenhuma
        // — a promessa de que negar nunca quebra o app.
        FloatingActionButton(
            onClick = onPickPhoto,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(LumeSpacing.md)
                .testTag("fab_new_edit")
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Nova edição"
            )
        }
    }
}

/**
 * Um atalho de "Começar": miniatura real + nome. O toque abre o
 * seletor de fotos e o Editor já na ferramenta do atalho — ou no
 * padrão, quando o atalho é só "Abrir foto" (alvo nulo).
 */
@Composable
private fun StartTile(
    action: StartAction,
    onPickWithTarget: (PendingEditorTarget.Target?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(96.dp)
            .clickable { onPickWithTarget(action.target) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (action.thumbRes != null) {
                Image(
                    painter = painterResource(id = action.thumbRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Text(
            text = action.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

/** Cabeçalho de um dia da grade: data + quantidade de fotos. */
@Composable
private fun DateHeader(
    label: String,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = LumeSpacing.md,
                end = LumeSpacing.md,
                top = LumeSpacing.md,
                bottom = LumeSpacing.xs
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = if (count == 1) "1 foto" else "$count fotos",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhotoSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Buscar nas suas fotos") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(24.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .testTag("photo_search")
    )
}

@Composable
private fun DevicePhotoCell(
    photo: DevicePhoto,
    edited: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var bitmap by remember(photo.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photo.id) {
        bitmap = DevicePhotos.thumbnail(context, photo)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        val loaded = bitmap
        if (loaded != null) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (edited) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(18.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Foto com edição salva",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(11.dp)
                    )
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
 * decodificado uma vez. A miniatura em arquivo vence (não depende
 * da permissão temporária do seletor); sem ela, tenta a URI.
 * Nulo sem rascunho ou se a foto sumiu.
 */
@Composable
private fun rememberSampleBitmap(photoUri: String?): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(photoUri) {
        if (photoUri == null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            try {
                DraftThumbnails.load(context, photoUri)
                    ?: BitmapLoader.decodePreview(context, Uri.parse(photoUri))
            } catch (e: Exception) {
                null
            }
        }
        bitmap = loaded
    }

    return bitmap?.asImageBitmap()
}

/**
 * Uma foto genérica do app, decodificada uma vez. Fica em
 * drawable-nodpi para o sistema não reescalar por densidade.
 */
@Composable
private fun rememberDrawableSample(@DrawableRes resId: Int): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(resId) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(resId) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                BitmapFactory.decodeResource(context.resources, resId)
            } catch (e: Exception) {
                null
            }
        }
        bitmap = loaded
    }

    return bitmap?.asImageBitmap()
}

/**
 * A amostra de um cartão: a foto do rascunho vence; sem rascunho,
 * o retrato para a coleção de retrato e a paisagem para as outras.
 */
private fun sampleForCollection(
    collection: String,
    draft: ImageBitmap?,
    portrait: ImageBitmap?,
    landscape: ImageBitmap?
): ImageBitmap? {
    return draft ?: if (collection == "Pele e Retrato") portrait else landscape
}

@Composable
private fun DraftThumbnail(photoUri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(photoUri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(photoUri) { mutableStateOf(false) }

    LaunchedEffect(photoUri) {
        val loaded = withContext(Dispatchers.IO) {
            try {
                // A miniatura em arquivo vence: a permissão do
                // seletor expira e a URI deixa de abrir.
                DraftThumbnails.load(context, photoUri)
                    ?: BitmapLoader.decodePreview(context, Uri.parse(photoUri))
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
        val diff = System.currentTimeMillis() - millis
        val minute = 60_000L
        val hour = 60 * minute
        val day = 24 * hour
        when {
            diff < minute -> "agora mesmo"
            diff < hour -> {
                val value = diff / minute
                if (value == 1L) "há 1 min" else "há $value min"
            }
            diff < day -> {
                val value = diff / hour
                if (value == 1L) "há 1 h" else "há $value h"
            }
            diff < 7 * day -> {
                val value = diff / day
                if (value == 1L) "há 1 dia" else "há $value dias"
            }
            else -> SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(millis))
        }
    } catch (t: Throwable) {
        "Rascunho"
    }
}

/**
 * Aba Presets: a demonstração do preset (antes/depois com wipe),
 * as coleções, a grade de presets com filtros e a importação.
 * Tocar num preset abre o seletor de fotos e aplica o preset à
 * foto escolhida — o mesmo gesto do botão da demonstração.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PresetsTab(
    state: LibraryUiState,
    viewModel: LibraryViewModel,
    onPickWithPreset: (Preset) -> Unit,
    onPickPhoto: () -> Unit,
    onImportPresets: () -> Unit,
    onOpenPaywall: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCollection by remember { mutableStateOf<String?>(null) }

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
    val collectionCards = remember(state.presets, collections) {
        collections.map { collection ->
            collection to state.presets.first { it.collection == collection }
        }
    }

    // A foto de amostra de tudo na aba: o primeiro rascunho,
    // decodificado uma vez. Sem rascunho, as fotos genéricas do
    // app (retrato para Pele e Retrato, paisagem para o resto) —
    // a aba nunca fica com cartões vazios.
    val draftSample = rememberSampleBitmap(state.drafts.firstOrNull()?.photoUri)
    val fallbackPortrait = rememberDrawableSample(R.drawable.sample_portrait)
    val fallbackLandscape = rememberDrawableSample(R.drawable.sample_landscape)
    val sample = draftSample ?: fallbackPortrait

    // O preset que a demonstração mostra: Pele Dourada, com quedas
    // honestas se a lista mudar (nunca trava a aba).
    val bannerPreset = remember(state.presets) {
        state.presets.find { it.id == BANNER_PRESET_ID }
            ?: state.presets.firstOrNull { it.collection == "Pele e Retrato" }
            ?: state.presets.firstOrNull()
    }
    val bannerFilter = remember(bannerPreset) {
        bannerPreset?.recipe?.let { recipe ->
            ColorFilter.colorMatrix(ColorMatrix(RecipeColorMatrix.values(recipe)))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        HomeTopBar(onOpenPaywall = onOpenPaywall)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("presets_list")
        ) {
            item {
                HomeWipeBanner(
                    preset = bannerPreset,
                    sample = sample,
                    filter = bannerFilter,
                    onUsePreset = {
                        bannerPreset?.let { preset -> onPickWithPreset(preset) }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
            }
            item {
                Column(
                    modifier = Modifier.padding(top = LumeSpacing.lg)
                ) {
                    SectionTitle(
                        "Coleções de presets",
                        modifier = Modifier.padding(start = LumeSpacing.md)
                    )
                    LazyRow(
                        modifier = Modifier
                            .testTag("collections_row")
                            .padding(top = LumeSpacing.sm),
                        contentPadding = PaddingValues(
                            horizontal = LumeSpacing.md
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(collectionCards) { (collection, firstPreset) ->
                            val count = state.presets.count {
                                it.collection == collection
                            }
                            CollectionCard(
                                collection = collection,
                                count = count,
                                firstPreset = firstPreset,
                                sample = sampleForCollection(
                                    collection,
                                    draftSample,
                                    fallbackPortrait,
                                    fallbackLandscape
                                ),
                                onClick = { selectedCollection = collection }
                            )
                        }
                        item {
                            CreatePresetCard(
                                onClick = onPickPhoto
                            )
                        }
                    }
                }
            }
            item {
                Column(
                    modifier = Modifier.padding(
                        top = LumeSpacing.lg,
                        bottom = LumeSpacing.xl
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = LumeSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionTitle(
                            "Presets",
                            modifier = Modifier.testTag("presets_title")
                        )
                        TextButton(onClick = onImportPresets) {
                            Text("Importar presets")
                        }
                    }
                    LazyRow(
                        modifier = Modifier.padding(top = LumeSpacing.xs),
                        contentPadding = PaddingValues(
                            horizontal = LumeSpacing.md
                        ),
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
                    FlowRow(
                        maxItemsInEachRow = 2,
                        horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm),
                        modifier = Modifier.padding(
                            start = LumeSpacing.md,
                            end = LumeSpacing.md,
                            top = LumeSpacing.sm
                        )
                    ) {
                        visiblePresets.forEach { preset ->
                            PresetGridCard(
                                preset = preset,
                                sample = sampleForCollection(
                                    preset.collection,
                                    draftSample,
                                    fallbackPortrait,
                                    fallbackLandscape
                                ),
                                onClick = { onPickWithPreset(preset) },
                                onDelete = if (preset.isBuiltIn) {
                                    null
                                } else {
                                    { viewModel.deletePreset(preset.id) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A demonstração de um preset, não anúncio. A foto do primeiro
 * rascunho com o preset varrendo em diagonal (depois à esquerda
 * da linha, original à direita); segurar mostra o original — o
 * mesmo gesto de comparar do Editor. Sem rascunho, gradiente da
 * marca. O botão aplica o preset à foto escolhida.
 */
@Composable
private fun HomeWipeBanner(
    preset: Preset?,
    sample: ImageBitmap?,
    filter: ColorFilter?,
    onUsePreset: () -> Unit,
    modifier: Modifier = Modifier
) {
    var held by remember { mutableStateOf(false) }
    val wipeTransition = rememberInfiniteTransition(label = "wipe")
    val wipeFraction by wipeTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wipeFraction"
    )
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = remember(density, maxWidth) {
            with(density) { maxWidth.toPx() }
        }
        val slantPx = remember(density) {
            with(density) { 24.dp.toPx() }
        }
        if (sample != null) {
            // Original por baixo, foto com preset por cima recortada
            // pela linha do wipe. Segurar esconde a camada do preset.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                held = true
                                tryAwaitRelease()
                                held = false
                            }
                        )
                    }
            ) {
                Image(
                    bitmap = sample,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Image(
                    bitmap = sample,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = filter,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(WipeShape(wipeFraction, slantPx))
                        .graphicsLayer { alpha = if (held) 0f else 1f }
                )
                // Linha do wipe, inclinada igual ao recorte.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset { IntOffset((widthPx * wipeFraction).toInt(), 0) }
                        .width(3.dp)
                        .height(260.dp)
                        .graphicsLayer {
                            rotationZ = -7f
                            alpha = if (held) 0f else 1f
                        }
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        } else {
            // Sem rascunho: gradiente da marca, sem foto inventada.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Collections,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                    modifier = Modifier.size(72.dp)
                )
            }
        }
        // Sombra para a legenda continuar legível sobre a foto.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.55f)
                        )
                    )
                )
        )
        if (preset != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 16.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(LumeSpacing.xxs)
            ) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Coleção ${preset.collection}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                onClick = onUsePreset,
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "Usar este preset",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF111111),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** Recorte diagonal do wipe: mostra a camada do preset à esquerda. */
private class WipeShape(
    private val fraction: Float,
    private val slantPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val x = size.width * fraction
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(x, 0f)
            lineTo(x - slantPx, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Cartão de coleção: 112x149, foto com o visual do primeiro preset. */
@Composable
private fun CollectionCard(
    collection: String,
    count: Int,
    firstPreset: Preset,
    sample: ImageBitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Miniatura rápida pela matriz (aproximação v1): o visual
    // completo do preset só aparece no Editor e na exportação.
    val filter = remember(firstPreset.recipe) {
        ColorFilter.colorMatrix(
            ColorMatrix(RecipeColorMatrix.values(firstPreset.recipe))
        )
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.size(width = 112.dp, height = 149.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (sample != null) {
                Image(
                    bitmap = sample,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = filter,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PresetSwatch(
                    preset = firstPreset,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.75f)
                            )
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        start = 10.dp,
                        end = 10.dp,
                        bottom = 9.dp
                    )
            ) {
                Text(
                    text = collection,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (count == 1) "1 preset" else "$count presets",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/** Cartão "Criar preset": abre o seletor (o preset nasce de uma edição). */
@Composable
private fun CreatePresetCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val dashPx = remember(density) { with(density) { 1.dp.toPx() } }
    val radiusPx = remember(density) { with(density) { 14.dp.toPx() } }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .size(width = 112.dp, height = 149.dp)
            .drawBehind {
                drawRoundRect(
                    color = primary.copy(alpha = 0.5f),
                    style = Stroke(
                        width = dashPx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                    ),
                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                )
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = "Criar preset",
                style = MaterialTheme.typography.labelLarge,
                color = primary
            )
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
