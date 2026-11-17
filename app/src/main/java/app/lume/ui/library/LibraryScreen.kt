package app.lume.ui.library

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.core.Preset
import app.lume.core.RecipeColorMatrix
import app.lume.data.Draft
import app.lume.imaging.BitmapLoader
import app.lume.ui.components.PresetSwatch
import app.lume.ui.components.SectionTitle
import app.lume.ui.components.ShowMessageAsSnackbar
import app.lume.ui.components.SupportingText
import app.lume.ui.components.rememberPhotoPickerLauncher
import app.lume.ui.editor.EditorTool
import app.lume.ui.editor.PendingEditorTarget
import app.lume.ui.theme.LumeSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Ordem fixa dos itens da lista da Home. O herói "Presets" e os
 * cartões de coleção rolam até [PRESETS_SECTION_INDEX]; se a ordem
 * mudar, o índice tem que mudar junto.
 */
private const val PRESETS_SECTION_INDEX = 4

/** Uma função da grade: rótulo, ícone e a ferramenta que ela abre. */
private data class HomeFunction(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val target: PendingEditorTarget.Target
)

private val HOME_FUNCTIONS = listOf(
    HomeFunction(
        "light", "Luz", Icons.Filled.WbSunny,
        PendingEditorTarget.Target(EditorTool.LIGHT, lightSub = "exposure")
    ),
    HomeFunction(
        "color", "Cor", Icons.Filled.Palette,
        PendingEditorTarget.Target(EditorTool.COLOR, colorSub = "temperature")
    ),
    HomeFunction(
        "effects", "Efeitos", Icons.Filled.AutoAwesome,
        PendingEditorTarget.Target(EditorTool.EFFECTS, effectsSub = "clarity")
    ),
    HomeFunction(
        "crop", "Cortar", Icons.Filled.Crop,
        PendingEditorTarget.Target(EditorTool.CROP)
    ),
    HomeFunction(
        "hsl", "HSL", Icons.Filled.ColorLens,
        PendingEditorTarget.Target(EditorTool.COLOR, colorSub = "hsl")
    ),
    HomeFunction(
        "curve", "Curva", Icons.Filled.ShowChart,
        PendingEditorTarget.Target(EditorTool.LIGHT, lightSub = "curve")
    ),
    HomeFunction(
        "history", "Histórico", Icons.Filled.History,
        PendingEditorTarget.Target(EditorTool.HISTORY)
    ),
    HomeFunction(
        "qr", "QR", Icons.Filled.QrCode2,
        PendingEditorTarget.Target(EditorTool.PRESETS)
    )
)

/** O preset que o banner da Home demonstra (antes/depois com wipe). */
private const val BANNER_PRESET_ID = "builtin-pele-dourada"

/** Heróis: altura do cartão e quanto dele entra no banner. */
private val HERO_HEIGHT = 80.dp
private val HERO_BANNER_OVERLAP = 26.dp

@OptIn(ExperimentalLayoutApi::class)
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
    var pendingPickTarget by remember { mutableStateOf<PendingEditorTarget.Target?>(null) }

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

    // A foto de amostra de tudo na Home: o primeiro rascunho,
    // decodificada uma vez. Nula sem rascunho ou se a foto sumiu.
    val sample = rememberSampleBitmap(state.drafts.firstOrNull()?.photoUri)

    // O preset que o banner demonstra: Pele Dourada, com quedas
    // honestas se a lista mudar (nunca trava a Home).
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
    val heroMiniPresets = remember(state.presets) { state.presets.take(3) }

    val openPicker = rememberPhotoPickerLauncher { uri ->
        // O alvo só é entregue quando a foto chega: se o usuário
        // cancelar o seletor, nada fica pendente para a próxima vez.
        PendingEditorTarget.target = pendingPickTarget
        pendingPickTarget = null
        onOpenEditor(uri)
    }
    val pickWith: (PendingEditorTarget.Target?) -> Unit = { target ->
        pendingPickTarget = target
        openPicker()
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

    val listState = rememberLazyListState()

    // Colapso guiado pelo scroll (igual ao MotionScene do Polish:
    // alpha 1 -> 0): o banner some com parallax, os heróis e a grade
    // somem em fade, e a tira fixa aparece no topo.
    val density = LocalDensity.current
    val collapseThresholdPx = remember(density) {
        with(density) { 260.dp.toPx() }
    }
    val collapseState by remember {
        derivedStateOf {
            val offsetPx = if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else {
                collapseThresholdPx * 2f
            }
            offsetPx to (offsetPx / collapseThresholdPx).coerceIn(0f, 1f)
        }
    }
    val scrollOffsetPx = collapseState.first
    val collapse = collapseState.second
    val stripAlpha = ((collapse - 0.55f) / 0.45f).coerceIn(0f, 1f)
    val topBarSolid = collapse > 0.55f

    val scrollToPresets: () -> Unit = {
        scope.launch { listState.animateScrollToItem(PRESETS_SECTION_INDEX) }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("library_list")
            ) {
                // 0: banner (demonstração de preset com wipe) com os
                // heróis montados na borda inferior dele.
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .graphicsLayer {
                                alpha = (1f - collapse * 1.15f).coerceIn(0f, 1f)
                                translationY = scrollOffsetPx * 0.35f
                            }
                            .testTag("home_banner")
                    ) {
                        HomeWipeBanner(
                            preset = bannerPreset,
                            sample = sample,
                            filter = bannerFilter,
                            onUsePreset = {
                                bannerPreset?.let { preset ->
                                    pickWith(
                                        PendingEditorTarget.Target(
                                            EditorTool.LIGHT,
                                            presetId = preset.id
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .offset(y = HERO_HEIGHT - HERO_BANNER_OVERLAP)
                                .fillMaxWidth()
                                .padding(horizontal = 15.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Card(
                                onClick = { pickWith(null) },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(232f)
                                    .height(HERO_HEIGHT)
                                    .testTag("hero_edit")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = LumeSpacing.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Text(
                                        text = "Editar foto",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            letterSpacing = 0.8.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            Card(
                                onClick = scrollToPresets,
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                ),
                                modifier = Modifier
                                    .weight(90f)
                                    .height(HERO_HEIGHT)
                                    .testTag("hero_presets")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    HeroMiniStrip(
                                        presets = heroMiniPresets,
                                        sample = sample
                                    )
                                    Text(
                                        text = "Presets",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            letterSpacing = 0.7.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // 1: grade de funções (os heróis vivem no item do
                // banner; o topo limpa a parte que desce dele).
                item {
                    Column(
                        modifier = Modifier
                            .graphicsLayer {
                                alpha = 1f - collapse
                                translationY = -scrollOffsetPx * 0.12f
                            }
                            .padding(
                                start = 18.dp,
                                end = 18.dp,
                                top = HERO_HEIGHT - HERO_BANNER_OVERLAP + 14.dp
                            )
                    ) {
                        Text(
                            text = "FERRAMENTAS",
                            style = MaterialTheme.typography.labelSmall.copy(
                                letterSpacing = 2.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HOME_FUNCTIONS.chunked(4).forEach { rowFunctions ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowFunctions.forEach { function ->
                                    HomeFunctionItem(
                                        function = function,
                                        onPick = { pickWith(function.target) },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("fn_${function.id}")
                                    )
                                }
                            }
                        }
                    }
                }

                // 2: rascunhos (item sempre presente, vazio sem
                // rascunhos, para o índice da seção de presets não
                // mudar).
                item {
                    if (state.drafts.isNotEmpty()) {
                        Column(
                            modifier = Modifier.padding(top = LumeSpacing.lg)
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
                                    Text(if (selectionMode) "Cancelar" else "Selecionar")
                                }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(
                                    horizontal = LumeSpacing.md
                                ),
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
                            if (selectionMode && selectedIds.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.padding(
                                        start = LumeSpacing.md,
                                        end = LumeSpacing.md,
                                        top = LumeSpacing.sm
                                    ),
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

                // 3: coleções de presets.
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
                                    sample = sample,
                                    onClick = {
                                        selectedCollection = collection
                                        scrollToPresets()
                                    }
                                )
                            }
                            item {
                                CreatePresetCard(
                                    onClick = { pickWith(null) }
                                )
                            }
                        }
                    }
                }

                // 4: todos os presets (grade + filtros + importar).
                item {
                    Column(
                        modifier = Modifier.padding(top = LumeSpacing.lg)
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
                            TextButton(
                                onClick = { importLauncher.launch("application/json") }
                            ) {
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
                                    sample = sample,
                                    onClick = {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                "Abra uma foto para aplicar o preset " +
                                                    "\"${preset.name}\"."
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
                }

                if (!state.isPro) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = LumeSpacing.md,
                                    end = LumeSpacing.md,
                                    top = LumeSpacing.lg
                                )
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

            // Barra do topo: a marca "Lume." à esquerda, Pro + conta
            // à direita. Transparente sobre o banner, sólida quando
            // a tira fixa aparece.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        if (topBarSolid) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            Color.Transparent
                        }
                    )
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
                        color = if (topBarSolid) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            Color.White
                        }
                    )
                    Text(
                        text = ".",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                    Surface(
                        onClick = onOpenAccount,
                        shape = CircleShape,
                        color = if (topBarSolid) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            Color.Black.copy(alpha = 0.35f)
                        },
                        border = BorderStroke(
                            1.dp,
                            if (topBarSolid) {
                                MaterialTheme.colorScheme.outlineVariant
                            } else {
                                Color.White.copy(alpha = 0.75f)
                            }
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Conta",
                                tint = if (topBarSolid) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    Color.White
                                },
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            // Tira fixa de funções: só existe quando o scroll já
            // colapsou os heróis (toques não atravessam quando
            // invisível porque o conteúdo nem é composto).
            if (stripAlpha > 0.05f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 52.dp)
                        .graphicsLayer { alpha = stripAlpha }
                        .background(MaterialTheme.colorScheme.surface)
                        .testTag("pinned_strip")
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(
                                start = 15.dp,
                                end = 15.dp,
                                top = LumeSpacing.sm,
                                bottom = LumeSpacing.sm
                            ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HOME_FUNCTIONS.forEach { function ->
                            Surface(
                                onClick = { pickWith(function.target) },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .size(width = 76.dp, height = 64.dp)
                                    .testTag("strip_${function.id}")
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = function.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = function.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Um item da grade de funções: ícone de 24dp + rótulo de 11sp. */
@Composable
private fun HomeFunctionItem(
    function: HomeFunction,
    onPick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onPick)
            .padding(top = 7.dp, bottom = LumeSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = function.icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = function.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * O banner da Home: demonstração de um preset, não anúncio. A foto
 * do primeiro rascunho com o preset varrendo em diagonal (depois à
 * esquerda da linha, original à direita); segurar mostra o
 * original — o mesmo gesto de comparar do Editor. Sem rascunho,
 * gradiente da marca. O botão aplica o preset à foto escolhida.
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
                    .padding(start = 24.dp, bottom = 44.dp, end = 24.dp),
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
                    .padding(end = 16.dp, bottom = 44.dp)
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

/** 3 miniaturas reais no herói Presets (nada, sem presets). */
@Composable
private fun HeroMiniStrip(
    presets: List<Preset>,
    sample: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    if (presets.isEmpty()) return
    // Miniaturas rápidas pela matriz (aproximação v1): o visual
    // completo do preset só aparece no Editor e na exportação.
    val filters = remember(presets) {
        presets.map { preset ->
            ColorFilter.colorMatrix(
                ColorMatrix(RecipeColorMatrix.values(preset.recipe))
            )
        }
    }
    Row(
        modifier = modifier.padding(bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        presets.forEachIndexed { index, preset ->
            if (sample != null) {
                Image(
                    bitmap = sample,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = filters[index],
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                )
            } else {
                PresetSwatch(
                    preset = preset,
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(7.dp))
                )
            }
        }
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
