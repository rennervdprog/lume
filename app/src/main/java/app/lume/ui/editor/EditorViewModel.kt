package app.lume.ui.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lume.core.EditHistory
import app.lume.core.Preset
import app.lume.data.PresetLimitReached
import app.lume.core.Recipe
import app.lume.data.DraftRepository
import app.lume.data.PresetRepository
import app.lume.data.pro.EntitlementRepository
import app.lume.imaging.BitmapLoader
import app.lume.imaging.PhotoExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class EditorUiState(
    val photoUri: Uri? = null,
    val preview: ImageBitmap? = null,
    val recipe: Recipe = Recipe.IDENTITY,
    val presets: List<Preset> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isLoadingPhoto: Boolean = false,
    val isExporting: Boolean = false,
    val exportedUri: Uri? = null,
    val message: String? = null,
    val paywallRequested: Boolean = false,
    val appliedPresetName: String? = null,
    val presetIntensity: Float = 1f,
    val isPro: Boolean = false
)

class EditorViewModel(
    private val appContext: Context,
    private val presetRepository: PresetRepository,
    private val draftRepository: DraftRepository,
    private val entitlementRepository: EntitlementRepository
) : ViewModel() {

    private val history = EditHistory()
    private var previewBitmap: Bitmap? = null
    private var presetBaseRecipe: Recipe? = null
    private var appliedPreset: Preset? = null

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            entitlementRepository.isPro.collect { isPro ->
                _uiState.update { it.copy(isPro = isPro) }
            }
        }
        viewModelScope.launch {
            presetRepository.presets.collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
    }

    fun openPhoto(uri: Uri) {
        val currentUri = _uiState.value.photoUri
        if (currentUri == uri && _uiState.value.preview != null) return
        try {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // Nem todo provedor concede permissão persistível; a edição da
            // sessão atual continua funcionando com a permissão temporária.
        }
        recyclePreview()
        history.reset()
        appliedPreset = null
        presetBaseRecipe = null
        _uiState.update {
            it.copy(
                photoUri = uri,
                preview = null,
                recipe = Recipe.IDENTITY,
                canUndo = false,
                canRedo = false,
                isLoadingPhoto = true,
                exportedUri = null,
                appliedPresetName = null,
                presetIntensity = 1f
            )
        }
        viewModelScope.launch {
            val draftRecipe = draftRepository.drafts.first()
                .firstOrNull { it.photoUri == uri.toString() }
                ?.recipe
            val bitmap = withContext(Dispatchers.Default) {
                BitmapLoader.decodePreview(appContext, uri)
            }
            if (bitmap == null) {
                _uiState.update {
                    it.copy(
                        isLoadingPhoto = false,
                        message = "Não foi possível abrir esta foto."
                    )
                }
                return@launch
            }
            previewBitmap = bitmap
            val startRecipe = (draftRecipe ?: Recipe.IDENTITY).clamped()
            history.reset(startRecipe)
            _uiState.update {
                it.copy(
                    preview = bitmap.asImageBitmap(),
                    recipe = startRecipe,
                    isLoadingPhoto = false
                )
            }
        }
    }

    /** Arrasto de slider: atualiza o preview sem entrar no histórico. */
    fun updateRecipeDraft(recipe: Recipe) {
        history.preview(recipe)
        clearAppliedPresetTracking()
        _uiState.update { it.copy(recipe = history.current) }
    }

    /** Fim do gesto: confirma a edição no histórico e salva o rascunho. */
    fun commitRecipe() {
        history.commit(history.current)
        persistDraft()
        _uiState.update {
            it.copy(canUndo = history.canUndo, canRedo = history.canRedo)
        }
    }

    fun undo() {
        val recipe = history.undo() ?: return
        clearAppliedPresetTracking()
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = recipe,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
    }

    fun redo() {
        val recipe = history.redo() ?: return
        clearAppliedPresetTracking()
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = recipe,
                canUndo = history.canUndo,
                canRedo = history.canRedo
            )
        }
    }

    fun resetAdjustments() {
        history.commit(Recipe.IDENTITY)
        clearAppliedPresetTracking()
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = Recipe.IDENTITY,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                appliedPresetName = null,
                presetIntensity = 1f
            )
        }
    }

    fun applyPreset(preset: Preset) {
        presetBaseRecipe = history.current
        appliedPreset = preset
        val blended = history.current.interpolatedTo(preset.recipe, 1f)
        history.commit(blended)
        persistDraft()
        _uiState.update {
            it.copy(
                recipe = blended,
                canUndo = history.canUndo,
                canRedo = history.canRedo,
                appliedPresetName = preset.name,
                presetIntensity = 1f
            )
        }
    }

    /** Intensidade do preset (0..1): interpola da base ao preset. */
    fun updatePresetIntensity(intensity: Float) {
        val preset = appliedPreset ?: return
        val base = presetBaseRecipe ?: Recipe.IDENTITY
        val blended = base.interpolatedTo(preset.recipe, intensity)
        history.preview(blended)
        _uiState.update { it.copy(recipe = blended, presetIntensity = intensity) }
    }

    fun commitPresetIntensity() {
        history.commit(history.current)
        persistDraft()
        _uiState.update {
            it.copy(canUndo = history.canUndo, canRedo = history.canRedo)
        }
    }

    fun savePreset(name: String, collection: String) {
        viewModelScope.launch {
            val result = presetRepository.saveUserPreset(
                name = name,
                collection = collection,
                recipe = history.current
            )
            result.fold(
                onSuccess = { preset ->
                    _uiState.update {
                        it.copy(message = "Preset \"${preset.name}\" salvo.")
                    }
                },
                onFailure = { error ->
                    if (error is PresetLimitReached) {
                        _uiState.update {
                            it.copy(
                                message = "Limite de ${error.limit} presets no plano grátis.",
                                paywallRequested = true
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(message = error.message ?: "Não foi possível salvar o preset.")
                        }
                    }
                }
            )
        }
    }

    fun exportPhoto() {
        val uri = _uiState.value.photoUri ?: return
        if (_uiState.value.isExporting) return
        _uiState.update { it.copy(isExporting = true, exportedUri = null) }
        val recipe = history.current
        viewModelScope.launch {
            val result = PhotoExporter.exportJpeg(appContext, uri, recipe)
            result.fold(
                onSuccess = { savedUri ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            exportedUri = savedUri,
                            message = "Foto exportada para a galeria."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isExporting = false,
                            message = error.message ?: "Não foi possível exportar a foto."
                        )
                    }
                }
            )
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumePaywallRequest() {
        _uiState.update { it.copy(paywallRequested = false) }
    }

    fun clearExportedUri() {
        _uiState.update { it.copy(exportedUri = null) }
    }

    private fun clearAppliedPresetTracking() {
        // Ajuste manual após preset: a intensidade perde o sentido, porque
        // a receita já não é mais "base + preset". O nome some do painel.
        if (appliedPreset != null) {
            appliedPreset = null
            presetBaseRecipe = null
            _uiState.update { it.copy(appliedPresetName = null, presetIntensity = 1f) }
        }
    }

    private fun persistDraft() {
        val uri = _uiState.value.photoUri ?: return
        val recipe = history.current
        viewModelScope.launch {
            try {
                draftRepository.saveDraft(uri.toString(), recipe)
            } catch (e: Exception) {
                // Rascunho é conveniência: falhar aqui nunca pode quebrar
                // a edição. A exportação não depende do rascunho.
            }
        }
    }

    private fun recyclePreview() {
        previewBitmap?.recycle()
        previewBitmap = null
    }

    override fun onCleared() {
        recyclePreview()
        super.onCleared()
    }
}
