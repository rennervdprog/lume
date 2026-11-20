package app.lume.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.data.auth.AccountState
import app.lume.imaging.ShareHelper
import app.lume.ui.components.SectionTitle
import app.lume.ui.components.ShowMessageAsSnackbar
import app.lume.ui.components.SupportingText
import app.lume.ui.components.VerticalGap
import app.lume.ui.theme.LumeSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }

    ShowMessageAsSnackbar(
        message = state.message,
        snackbarHostState = snackbarHostState,
        onConsume = { viewModel.consumeMessage() }
    )

    LaunchedEffect(state.exportPayload) {
        val payload = state.exportPayload
        if (payload != null) {
            ShareHelper.shareText(context, "Meus presets (Solarize)", payload)
            viewModel.consumeExportPayload()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Conta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Voltar"
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LumeSpacing.md, vertical = LumeSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
        ) {
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
                    when (val account = state.accountState) {
                        is AccountState.Guest -> {
                            Text(
                                text = "Você está como visitante",
                                style = MaterialTheme.typography.titleMedium
                            )
                            SupportingText(
                                text = "Sem conta, você edita e exporta normalmente. " +
                                    "A conta serve para não perder seus presets ao " +
                                    "trocar de celular."
                            )
                            Text(
                                text = "ID do visitante: ${shortId(account.userId)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is AccountState.SignedIn -> {
                            Text(
                                text = account.displayName ?: "Conta criada",
                                style = MaterialTheme.typography.titleMedium
                            )
                            SupportingText(
                                text = account.email ?: "Sem e-mail cadastrado."
                            )
                        }
                    }
                    Text(
                        text = "${state.userPresetCount} presets criados · " +
                            "${state.draftCount} rascunhos neste aparelho",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            SectionTitle("Entrar ou criar conta")
            SupportingText(
                text = "A conta é opcional. Ela nunca bloqueia a edição " +
                    "nem a exportação."
            )
            Button(
                onClick = { viewModel.signInWithGoogle() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Entrar com Google")
            }
            OutlinedButton(
                onClick = { viewModel.emailAuthNotAvailable() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Entrar com e-mail")
            }
            if (state.accountState is AccountState.SignedIn) {
                TextButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sair da conta")
                }
            }

            VerticalGap()
            SectionTitle("Seus dados")
            OutlinedButton(
                onClick = { viewModel.prepareExport() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exportar meus presets")
            }
            TextButton(
                onClick = { showDeleteDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Excluir conta",
                    color = MaterialTheme.colorScheme.error
                )
            }
            SupportingText(
                text = "Excluir a conta não cancela uma assinatura na Play " +
                    "Store. Assinaturas são gerenciadas na Play."
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir conta?") },
            text = {
                Text(
                    "O registro da conta neste aparelho será apagado. " +
                        "Seus presets e rascunhos neste aparelho serão mantidos. " +
                        "Suas fotos nunca saíram do aparelho. " +
                        "Exporte seus presets antes, se quiser guardá-los."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteAccount()
                    }
                ) {
                    Text(
                        text = "Excluir",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Voltar")
                }
            }
        )
    }
}

private fun shortId(userId: String): String {
    return if (userId.length > 8) userId.take(8) else userId.ifBlank { "—" }
}
