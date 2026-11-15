package app.lume.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.lume.ui.theme.LumeSpacing

/**
 * Seletor de fotos do sistema (Photo Picker). Não exige permissão de
 * mídia. Retorna um lançador: chame o lambda para abrir o seletor.
 */
@Composable
fun rememberPhotoPickerLauncher(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onPicked(uri)
        }
    }
    return {
        launcher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }
}

/** Título de seção: um chefe por grupo, tipografia M3, sem tamanho inventado. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(bottom = LumeSpacing.xs)
    )
}

/** Texto de apoio curto, em tom secundário legível (nunca cinza-claro). */
@Composable
fun SupportingText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
fun VerticalGap() {
    Spacer(modifier = Modifier.height(LumeSpacing.md))
}

/**
 * Exibe [message] como Snackbar uma vez e consome. Cada tela tem seu
 * próprio SnackbarHostState; este efeito só orquestra a exibição.
 */
@Composable
fun ShowMessageAsSnackbar(
    message: String?,
    snackbarHostState: SnackbarHostState,
    onConsume: () -> Unit
) {
    LaunchedEffect(message) {
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onConsume()
        }
    }
}

/** Coluna padrão de tela com respiro (gutter único de 16dp). */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = LumeSpacing.md),
        content = content
    )
}
