package app.lume.ui.library

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import app.lume.R
import app.lume.ui.theme.LumeSpacing
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Estado da permissão de câmera, na ordem em que o usuário vive:
 *
 * - [NOT_ASKED]: nunca pedida — mostra a tela de explicação do Lume.
 * - [GRANTED]: câmera liberada — o visor abre.
 * - [DENIED]: negada — mostra o estado vazio com o caminho de
 *   volta (pedir de novo ou abrir as Configurações).
 *
 * A permissão só existe por causa da câmera dentro do app (0.14.0):
 * sem ela, o atalho Câmera mostra o estado negado e o botão + e a
 * grade continuam funcionando normal.
 */
enum class CameraPermissionStatus {
    NOT_ASKED,
    GRANTED,
    DENIED
}

class CameraPermissionState internal constructor(
    private val appContext: Context
) {
    val permission: String = Manifest.permission.CAMERA

    var status by mutableStateOf(CameraPermissionStatus.NOT_ASKED)
        private set

    /**
     * O sistema ainda mostra a janela se pedirmos de novo. Falso
     * quando o usuário marcou "não perguntar de novo" (ou o
     * aparelho não tem Activity viva): aí o caminho é abrir as
     * Configurações, que sempre funciona.
     */
    var canReask by mutableStateOf(false)
        private set

    /**
     * O usuário dispensou a explicação com "Agora não" nesta
     * sessão: a tela mostra o estado negado (com o botão pedindo de
     * novo, já que o sistema ainda mostraria a janela), em vez da
     * explicação em loop. Não é persistido: na próxima abertura, a
     * explicação volta — ela é o pedido correto da 1ª vez.
     */
    var educationDismissed by mutableStateOf(false)
        private set

    fun dismissEducation() {
        educationDismissed = true
    }

    private val prefs
        get() = appContext.getSharedPreferences(
            "lume_camera_permission",
            Context.MODE_PRIVATE
        )

    /** Revalida o estado. [activity] é a Activity viva da tela. */
    fun refresh(activity: Activity?) {
        val granted = ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            status = CameraPermissionStatus.GRANTED
            canReask = false
            return
        }
        val asked = prefs.getBoolean(KEY_ASKED, false)
        status = if (asked) {
            CameraPermissionStatus.DENIED
        } else {
            CameraPermissionStatus.NOT_ASKED
        }
        canReask = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
        } ?: false
    }

    /**
     * Dispara o pedido ao sistema. Marca "já pedida" antes de
     * lançar: se o usuário negar, o próximo estado é DENIED (com o
     * caminho de volta), nunca a explicação de novo em loop.
     */
    fun request(launch: (String) -> Unit) {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        launch(permission)
    }

    /** Abre a tela do Lume nas Configurações do aparelho. */
    fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", appContext.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            appContext.startActivity(intent)
        } catch (e: Exception) {
            // Sem app de Configurações (emulador mínimo): nada a
            // fazer, o estado negado continua explicando o caminho.
        }
    }

    private companion object {
        const val KEY_ASKED = "asked"
    }
}

/**
 * Estado da permissão ligado à tela: revalida na entrada e a cada
 * volta ao app (o usuário pode ter liberado nas Configurações). O
 * pedido em si sai dos botões da tela, que usam o próprio launcher
 * de permissão e chamam [CameraPermissionState.request].
 */
@Composable
fun rememberCameraPermissionState(): CameraPermissionState {
    val context = LocalContext.current
    val state = remember(context.applicationContext) {
        CameraPermissionState(context.applicationContext)
    }
    val activity = remember(context) { findActivity(context) }
    LaunchedEffect(Unit) {
        state.refresh(activity)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        state.refresh(activity)
    }
    return state
}

/**
 * Tela de explicação do Lume, antes da janela do sistema: por que
 * a permissão, o que o app faz com a foto, e a saída sem permissão
 * (o botão + e a grade continuam funcionando).
 */
@Composable
fun CameraPermissionEducation(
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hero = rememberDrawableSample(R.drawable.sample_landscape)
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = LumeSpacing.lg, vertical = LumeSpacing.md),
        verticalArrangement = Arrangement.spacedBy(LumeSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            val bitmap = hero
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.55f)
                            )
                        )
                    )
            )
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(LumeSpacing.md)
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = LumeSpacing.sm,
                        vertical = LumeSpacing.xs
                    ),
                    horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF4CD964),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "A foto fica no seu aparelho",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
        Text(
            text = "Fotografe dentro do Lume",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = LumeSpacing.sm)
        )
        Text(
            text = "Para abrir a câmera com os seus presets ao vivo, o " +
                "Lume precisa da sua permissão para usar a câmera.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CameraPermissionBullet(
            "O Lume só fotografa quando você toca no botão. Nada é enviado para lugar nenhum."
        )
        CameraPermissionBullet(
            "A foto vai para a sua galeria só quando você exportar."
        )
        CameraPermissionBullet(
            "Dá para mudar de ideia a qualquer momento, nos ajustes do aparelho."
        )
        Spacer(modifier = Modifier.height(LumeSpacing.md))
        Button(
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Permitir acesso à câmera")
        }
        TextButton(
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Agora não")
        }
        Text(
            text = "Sem permissão, o botão + continua funcionando: ele " +
                "abre o seletor de fotos do aparelho.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CameraPermissionBullet(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Estado negado: o visor fica fechado, o app não quebra, e o botão
 * leva de volta — pedir de novo (se o sistema ainda permite) ou
 * abrir as Configurações (se não permite mais).
 */
@Composable
fun CameraPermissionDenied(
    canReask: Boolean,
    onAction: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = LumeSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(88.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Text(
            text = "Sem acesso à câmera",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = LumeSpacing.lg)
        )
        Text(
            text = "Você não permitiu. O visor fica fechado, mas nada " +
                "no app quebra por isso.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = LumeSpacing.xs)
        )
        Button(
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LumeSpacing.lg)
        ) {
            Text(
                if (canReask) {
                    "Pedir permissão de novo"
                } else {
                    "Permitir nas Configurações"
                }
            )
        }
        TextButton(onClick = onBack) {
            Text("Voltar")
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = LumeSpacing.md)
        ) {
            Text(
                text = "O botão + continua funcionando. Ele abre o " +
                    "seletor de fotos do próprio Android, que não " +
                    "precisa de nenhuma permissão — você escolhe a " +
                    "foto e edita normal.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(LumeSpacing.md)
            )
        }
    }
}

/**
 * Uma foto genérica do app, decodificada uma vez. Fica em
 * drawable-nodpi para o sistema não reescalar por densidade.
 * (Cópia local de [rememberDrawableSample]: a original é privada
 * do arquivo de permissão de fotos.)
 */
@Composable
private fun rememberDrawableSample(resId: Int): androidx.compose.ui.graphics.ImageBitmap? {
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
