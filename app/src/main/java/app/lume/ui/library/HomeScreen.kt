package app.lume.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.R
import app.lume.ui.account.AccountScreen
import app.lume.ui.account.AccountViewModel
import app.lume.ui.components.ShowMessageAsSnackbar
import app.lume.ui.components.rememberPhotoPickerLauncher
import app.lume.ui.editor.EditorTool
import app.lume.ui.editor.PendingEditorTarget

/** As três abas da navegação inferior da Home. */
enum class HomeTab {
    PHOTOS,
    PRESETS,
    ACCOUNT
}

/**
 * Dock flutuante da Home (0.15.3): substitui a NavigationBar
 * genérica. Fundo #1E2A3D (um tom acima do sol do ícone), borda
 * branca 9%, raio 26, filete gradiente laranja→teal no topo; a
 * aba selecionada ganha pílula laranja translúcida + ícone e
 * rótulo laranja. Os ícones são os vetores XML com o sol dividido
 * (tint = Unspecified, cores fixas). testTags nav_fotos /
 * nav_presets / nav_conta preservadas.
 */
@Composable
private fun SolarizeDock(
    selectedTab: HomeTab,
    onSelect: (HomeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val dockBg = Color(0xFF1E2A3D)
    val selectedBg = Color(0xFFFF9A3D).copy(alpha = 0.16f)
    val selectedColor = Color(0xFFFF9A3D)
    val unselectedColor = Color(0xFF8A97A8)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = dockBg,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
            shadowElevation = 10.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                DockItem(
                    selected = selectedTab == HomeTab.PHOTOS,
                    onClick = { onSelect(HomeTab.PHOTOS) },
                    iconRes = if (selectedTab == HomeTab.PHOTOS) {
                        R.drawable.ic_dock_fotos_on
                    } else {
                        R.drawable.ic_dock_fotos_off
                    },
                    label = "Fotos",
                    contentDescription = null,
                    selectedColor = selectedColor,
                    unselectedColor = unselectedColor,
                    selectedBg = selectedBg,
                    testTag = "nav_fotos"
                )
                DockItem(
                    selected = selectedTab == HomeTab.PRESETS,
                    onClick = { onSelect(HomeTab.PRESETS) },
                    iconRes = if (selectedTab == HomeTab.PRESETS) {
                        R.drawable.ic_dock_presets_on
                    } else {
                        R.drawable.ic_dock_presets_off
                    },
                    label = "Presets",
                    contentDescription = null,
                    selectedColor = selectedColor,
                    unselectedColor = unselectedColor,
                    selectedBg = selectedBg,
                    testTag = "nav_presets"
                )
                DockItem(
                    selected = selectedTab == HomeTab.ACCOUNT,
                    onClick = { onSelect(HomeTab.ACCOUNT) },
                    iconRes = if (selectedTab == HomeTab.ACCOUNT) {
                        R.drawable.ic_dock_conta_on
                    } else {
                        R.drawable.ic_dock_conta_off
                    },
                    label = "Conta",
                    contentDescription = "Conta",
                    selectedColor = selectedColor,
                    unselectedColor = unselectedColor,
                    selectedBg = selectedBg,
                    testTag = "nav_conta"
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 26.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        0.0f to Color(0xFFFF9A3D),
                        0.45f to Color(0xFFFF5E3A),
                        1.0f to Color(0xFF2DD4BF)
                    ),
                    shape = RoundedCornerShape(2.dp)
                )
        )
    }
}

@Composable
private fun RowScope.DockItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String,
    contentDescription: String?,
    selectedColor: Color,
    unselectedColor: Color,
    selectedBg: Color,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) selectedBg else Color.Transparent)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = Modifier.size(26.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold
            ),
            color = if (selected) selectedColor else unselectedColor
        )
    }
}

/**
 * A Home do Lume: navegação inferior com Fotos, Presets e Conta.
 * O seletor de fotos, a importação de presets, o snackbar e o
 * pedido de permissão vivem aqui, acima das abas, porque as três
 * telas usam os mesmos gestos (escolher foto, aplicar preset).
 *
 * A aba Fotos é a tela inicial: grade de fotos do aparelho (com
 * permissão), busca e "Continuar editando". A aba Presets mostra
 * a demonstração e a grade de presets. A aba Conta é a tela de
 * conta de sempre, com o voltar caindo na aba Fotos.
 */
@Composable
fun HomeScreen(
    libraryViewModel: LibraryViewModel,
    accountViewModel: AccountViewModel,
    onOpenEditor: (Uri) -> Unit,
    onOpenPaywall: () -> Unit,
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(HomeTab.PHOTOS) }
    var pendingPickTarget by remember { mutableStateOf<PendingEditorTarget.Target?>(null) }

    val permissionState = rememberPhotoPermissionState()
    val permissionActivity = remember(context) { findActivity(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionState.refresh(permissionActivity)
    }
    val requestPhotoPermission: () -> Unit = {
        permissionState.request { permission ->
            permissionLauncher.launch(permission)
        }
    }

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
            libraryViewModel.importPresets(context, uri)
        }
    }

    ShowMessageAsSnackbar(
        message = state.message,
        snackbarHostState = snackbarHostState,
        onConsume = { libraryViewModel.consumeMessage() }
    )

    LaunchedEffect(state.paywallRequested) {
        if (state.paywallRequested) {
            libraryViewModel.consumePaywallRequest()
            onOpenPaywall()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            SolarizeDock(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                HomeTab.PHOTOS -> {
                    PhotosTab(
                        state = state,
                        viewModel = libraryViewModel,
                        permissionState = permissionState,
                        requestPhotoPermission = requestPhotoPermission,
                        onOpenEditor = onOpenEditor,
                        onPickPhoto = { pickWith(null) },
                        onPickWithTarget = pickWith,
                        onOpenPaywall = onOpenPaywall,
                        onOpenCamera = onOpenCamera
                    )
                }
                HomeTab.PRESETS -> {
                    PresetsTab(
                        state = state,
                        viewModel = libraryViewModel,
                        onPickWithPreset = { preset ->
                            pickWith(
                                PendingEditorTarget.Target(
                                    EditorTool.ADJUST,
                                    presetId = preset.id
                                )
                            )
                        },
                        onPickPhoto = { pickWith(null) },
                        onImportPresets = { importLauncher.launch("application/json") },
                        onOpenPaywall = onOpenPaywall
                    )
                }
                HomeTab.ACCOUNT -> {
                    AccountScreen(
                        viewModel = accountViewModel,
                        onBack = { selectedTab = HomeTab.PHOTOS }
                    )
                }
            }
        }
    }
}
