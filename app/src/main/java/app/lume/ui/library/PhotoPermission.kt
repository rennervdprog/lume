package app.lume.ui.library

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import androidx.compose.material.icons.filled.PhotoLibrary
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Estado da permissão de fotos, na ordem em que o usuário vive:
 *
 * - [NOT_ASKED]: nunca pedida — mostra a tela de explicação do Lume.
 * - [GRANTED]: acesso total — a grade mostra todas as fotos.
 * - [PARTIAL]: Android 14+ com "só algumas fotos" — a grade mostra
 *   só as escolhidas, com o aviso e o botão de adicionar mais.
 * - [DENIED]: negada — mostra o estado vazio com o caminho de
 *   volta (pedir de novo ou abrir as Configurações) e o botão +
 *   continua funcionando pelo seletor do sistema.
 *
 * A permissão só existe por causa da grade da tela inicial: o
 * seletor de fotos do sistema (Photo Picker) não precisa de
 * permissão nenhuma, e é por ele que o app funcionava até aqui.
 */
enum class PhotoPermissionStatus {
    NOT_ASKED,
    GRANTED,
    PARTIAL,
    DENIED
}

class PhotoPermissionState internal constructor(
    private val appContext: Context
) {
    val permission: String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    var status by mutableStateOf(PhotoPermissionStatus.NOT_ASKED)
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
     * sessão: a aba mostra o estado negado (com o botão pedindo de
     * novo, já que o sistema ainda mostraria a janela), em vez da
     * explicação em loop. Não é persistido: na próxima abertura do
     * app, a explicação volta — ela é o pedido correto da 1ª vez.
     */
    var educationDismissed by mutableStateOf(false)
        private set

    fun dismissEducation() {
        educationDismissed = true
    }

    private val prefs
        get() = appContext.getSharedPreferences(
            "lume_photo_permission",
            Context.MODE_PRIVATE
        )

    /** Revalida o estado. [activity] é a Activity viva da tela. */
    fun refresh(activity: Activity?) {
        val fullGranted = ContextCompat.checkSelfPermission(
            appContext,
            permission
        ) == PackageManager.PERMISSION_GRANTED
        if (fullGranted) {
            status = PhotoPermissionStatus.GRANTED
            canReask = false
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val partialGranted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            ) == PackageManager.PERMISSION_GRANTED
            if (partialGranted) {
                status = PhotoPermissionStatus.PARTIAL
                canReask = false
                return
            }
        }
        val asked = prefs.getBoolean(KEY_ASKED, false)
        status = if (asked) {
            PhotoPermissionStatus.DENIED
        } else {
            PhotoPermissionStatus.NOT_ASKED
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
 * de permissão e chamam [PhotoPermissionState.request].
 */
@Composable
fun rememberPhotoPermissionState(): PhotoPermissionState {
    val context = LocalContext.current
    val state = remember(context.applicationContext) {
        PhotoPermissionState(context.applicationContext)
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

internal fun findActivity(context: Context): Activity? {
    var current: Context? = context
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Tela de explicação do Lume, antes da janela do sistema: por que
 * a permissão, o que o app faz com as fotos, e a saída sem
 * permissão (o botão + continua funcionando pelo seletor).
 */
@Composable
fun PhotoPermissionEducation(
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
                        text = "Suas fotos ficam no seu aparelho",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
        Text(
            text = "Suas fotos, aqui dentro",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = LumeSpacing.sm)
        )
        Text(
            text = "Para mostrar suas fotos na tela inicial, o Solarize " +
                "precisa da sua permissão para ver a galeria.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PermissionBullet(
            "O Solarize só lê as fotos. Nada é enviado para lugar nenhum."
        )
        PermissionBullet(
            "Você pode liberar só algumas fotos, em vez da galeria toda."
        )
        PermissionBullet(
            "Dá para mudar de ideia a qualquer momento, nos ajustes do aparelho."
        )
        Spacer(modifier = Modifier.height(LumeSpacing.md))
        Button(
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Permitir acesso às fotos")
        }
        TextButton(
            onClick = onNotNow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Agora não")
        }
        Text(
            text = "Sem permissão, o botão + continua funcionando: " +
                "ele abre o seletor do sistema.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        // Espaço para o botão + flutuante não cobrir o texto final.
        Spacer(modifier = Modifier.height(88.dp))
    }
}

@Composable
private fun PermissionBullet(text: String) {
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
 * Estado negado: a grade fica vazia, o app não quebra, e o botão
 * leva de volta — pedir de novo (se o sistema ainda permite) ou
 * abrir as Configurações (se não permite mais).
 */
@Composable
fun PhotoPermissionDenied(
    canReask: Boolean,
    onAction: () -> Unit,
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
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Text(
            text = "Sem acesso às suas fotos",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = LumeSpacing.lg)
        )
        Text(
            text = "Você não permitiu. A grade fica vazia, mas nada " +
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
 * Aviso do acesso parcial (Android 14+): quantas fotos foram
 * liberadas e o botão para adicionar mais (reabre a escolha do
 * sistema).
 */
@Composable
fun PartialAccessNotice(
    count: Int,
    onAddMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(LumeSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = if (count == 1) {
                    "Você liberou 1 foto. Só ela aparece aqui."
                } else {
                    "Você liberou $count fotos. Só elas aparecem aqui."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onAddMore) {
                Text("Adicionar")
            }
        }
    }
}

/**
 * Uma foto genérica do app, decodificada uma vez. Fica em
 * drawable-nodpi para o sistema não reescalar por densidade.
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
