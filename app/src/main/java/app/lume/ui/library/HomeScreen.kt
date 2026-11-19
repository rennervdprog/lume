package app.lume.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == HomeTab.PHOTOS,
                    onClick = { selectedTab = HomeTab.PHOTOS },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.PhotoLibrary,
                            contentDescription = null
                        )
                    },
                    label = { Text("Fotos") },
                    modifier = Modifier.testTag("nav_fotos")
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.PRESETS,
                    onClick = { selectedTab = HomeTab.PRESETS },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null
                        )
                    },
                    label = { Text("Presets") },
                    modifier = Modifier.testTag("nav_presets")
                )
                NavigationBarItem(
                    selected = selectedTab == HomeTab.ACCOUNT,
                    onClick = { selectedTab = HomeTab.ACCOUNT },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Person,
                            contentDescription = "Conta"
                        )
                    },
                    label = { Text("Conta") },
                    modifier = Modifier.testTag("nav_conta")
                )
            }
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
                        onOpenPaywall = onOpenPaywall
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
