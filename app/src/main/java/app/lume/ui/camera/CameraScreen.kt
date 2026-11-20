package app.lume.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Camera
import android.net.Uri
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lume.imaging.buildCameraFilterGroup
import app.lume.ui.library.CameraPermissionDenied
import app.lume.ui.library.CameraPermissionEducation
import app.lume.ui.library.CameraPermissionStatus
import app.lume.ui.library.findActivity
import app.lume.ui.library.rememberCameraPermissionState
import app.lume.ui.theme.LumeSpacing
import jp.co.cyberagent.android.gpuimage.GPUImageView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Câmera dentro do app (0.14.0): visor ao vivo com o preset
 * aplicado, tira de presets embaixo e botão de captura. A foto
 * capturada já nasce com o filtro — e abre no Editor, onde o resto
 * da receita continua valendo.
 *
 * v1: só a câmera traseira, sem troca frontal, sem flash e sem
 * foco por toque. A permissão segue o mesmo desenho da de fotos:
 * explicação do Lume antes da janela do sistema, e negar nunca
 * quebra o app (o botão + continua funcionando).
 */
@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onPhotoCaptured: (Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionState = rememberCameraPermissionState()
    val permissionActivity = remember(context) { findActivity(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionState.refresh(permissionActivity)
    }
    val requestCameraPermission: () -> Unit = {
        permissionState.request { permission ->
            permissionLauncher.launch(permission)
        }
    }

    val status = permissionState.status
    val showEducation = status == CameraPermissionStatus.NOT_ASKED &&
        !permissionState.educationDismissed
    when {
        showEducation -> {
            CameraPermissionEducation(
                onAllow = requestCameraPermission,
                onNotNow = { permissionState.dismissEducation() },
                modifier = modifier
            )
        }
        status == CameraPermissionStatus.DENIED -> {
            CameraPermissionDenied(
                canReask = permissionState.canReask,
                onAction = {
                    if (permissionState.canReask) {
                        requestCameraPermission()
                    } else {
                        permissionState.openAppSettings()
                    }
                },
                onBack = onBack,
                modifier = modifier
            )
        }
        status == CameraPermissionStatus.NOT_ASKED -> {
            // Explicação dispensada com "Agora não": o sistema
            // ainda mostraria a janela (nunca pedida), então o
            // botão pede de novo em vez de ir às Configurações.
            CameraPermissionDenied(
                canReask = true,
                onAction = requestCameraPermission,
                onBack = onBack,
                modifier = modifier
            )
        }
        else -> {
            CameraViewfinder(
                viewModel = viewModel,
                state = state,
                onPhotoCaptured = onPhotoCaptured,
                onBack = onBack,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun CameraViewfinder(
    viewModel: CameraViewModel,
    state: CameraUiState,
    onPhotoCaptured: (Uri) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var gpuImageView by remember { mutableStateOf<GPUImageView?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }

    // O grupo de filtros do preset selecionado: reconstruído a cada
    // troca de preset (o visor recebe o grupo novo). A grade do LUT
    // resolve assíncrona no ViewModel e reconstrói junto.
    val filterGroup = remember(state.selectedPresetId, state.lutGrid) {
        buildCameraFilterGroup(viewModel.selectedRecipe, state.lutGrid)
    }

    // Troca de preset com o visor aberto: o grupo novo entra no visor.
    LaunchedEffect(filterGroup) {
        gpuImageView?.setFilter(filterGroup)
    }

    // Ciclo de vida do visor: pausa/retoma com a tela.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        gpuImageView?.onPause()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        gpuImageView?.onResume()
    }

    fun capturePhoto() {
        val view = gpuImageView ?: return
        if (isCapturing || camera == null) return
        isCapturing = true
        captureError = null
        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = view.capture()
                val uri = saveCapture(context, bitmap)
                bitmap.recycle()
                withContext(Dispatchers.Main) {
                    isCapturing = false
                    if (uri != null) {
                        onPhotoCaptured(uri)
                    } else {
                        captureError = "Não foi possível salvar a foto."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isCapturing = false
                    captureError = "Não foi possível fotografar."
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    GPUImageView(ctx).also { view ->
                        gpuImageView = view
                        val opened = openBackCamera()
                        camera = opened
                        if (opened != null) {
                            val degrees = cameraRotationDegrees(ctx)
                            view.setUpCamera(opened, degrees, false, false)
                        }
                        view.setFilter(filterGroup)
                    }
                },
                onRelease = { view ->
                    view.onPause()
                    camera?.release()
                    camera = null
                    gpuImageView = null
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(LumeSpacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Color.White
                )
            }
            if (camera == null) {
                Text(
                    text = "Não foi possível abrir a câmera.",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            val error = captureError
            if (error != null) {
                Text(
                    text = error,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = LumeSpacing.md)
                )
            }
        }
        // Tira de presets: o filtro ao vivo do visor.
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(LumeSpacing.xs),
            contentPadding = PaddingValues(
                horizontal = LumeSpacing.md,
                vertical = LumeSpacing.xs
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                FilterChip(
                    selected = state.selectedPresetId == null,
                    onClick = { viewModel.selectPreset(null) },
                    label = { Text("Original", color = Color.White) }
                )
            }
            items(items = state.presets, key = { it.id }) { preset ->
                FilterChip(
                    selected = state.selectedPresetId == preset.id,
                    onClick = { viewModel.selectPreset(preset.id) },
                    label = { Text(preset.name, color = Color.White) }
                )
            }
        }
        // Botão de captura: círculo branco com centro escuro.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = LumeSpacing.lg, top = LumeSpacing.xs),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(enabled = !isCapturing && camera != null) {
                        capturePhoto()
                    }
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.Black
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(2.dp, Color.White, CircleShape)
                    )
                }
            }
        }
    }
}

/** Abre a câmera traseira (null se ocupada ou inexistente). */
private fun openBackCamera(): Camera? {
    return try {
        Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK)
    } catch (e: Exception) {
        null
    }
}

/**
 * Rotação do visor (fórmula padrão da Camera1): a orientação da
 * câmera menos a rotação do aparelho. Sem ela, a foto sai deitada
 * em aparelho em pé.
 */
@Suppress("DEPRECATION")
private fun cameraRotationDegrees(context: Context): Int {
    val info = Camera.CameraInfo()
    Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info)
    val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    val rotation = when (windowManager.defaultDisplay.rotation) {
        Surface.ROTATION_0 -> 0
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
    return (info.orientation - rotation + 360) % 360
}

/**
 * Salva a captura em arquivo do app (filesDir/camera/) e devolve
 * a Uri. Arquivo do app = persistível, diferente da URI do
 * seletor: o rascunho desta foto reabre depois sem expirar.
 */
private fun saveCapture(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val dir = File(context.filesDir, "camera").apply { mkdirs() }
        // Nunca java.time (minSdk 24). SimpleDateFormat + catch.
        val stamp = try {
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        } catch (t: Throwable) {
            System.currentTimeMillis().toString()
        }
        val file = File(dir, "Solarize_$stamp.jpg")
        FileOutputStream(file).use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        }
        Uri.fromFile(file)
    } catch (e: Exception) {
        null
    }
}
