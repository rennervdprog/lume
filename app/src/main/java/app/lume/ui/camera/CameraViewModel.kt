package app.lume.ui.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lume.core.Preset
import app.lume.core.Recipe
import app.lume.data.PresetRepository
import app.lume.data.pro.EntitlementRepository
import app.lume.imaging.LutGrids
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraUiState(
    val presets: List<Preset> = emptyList(),
    val selectedPresetId: String? = null,
    val isPro: Boolean = false,
    /** Grade do LUT do preset selecionado (null = sem LUT). */
    val lutGrid: Bitmap? = null
)

class CameraViewModel(
    private val appContext: Context,
    private val presetRepository: PresetRepository,
    private val entitlementRepository: EntitlementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

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

    /** Receita do preset selecionado (ou identidade = sem filtro). */
    val selectedRecipe: Recipe
        get() {
            val id = _uiState.value.selectedPresetId ?: return Recipe.IDENTITY
            return _uiState.value.presets.firstOrNull { it.id == id }?.recipe
                ?: Recipe.IDENTITY
        }

    /** Escolhe o preset do visor (null = original, sem filtro). */
    fun selectPreset(presetId: String?) {
        if (_uiState.value.selectedPresetId == presetId) return
        _uiState.update { it.copy(selectedPresetId = presetId, lutGrid = null) }
        val recipe = selectedRecipe
        val lutId = recipe.lutId
        if (lutId == null || recipe.lutIntensity <= 0f) return
        viewModelScope.launch(Dispatchers.IO) {
            val grid = LutGrids.bitmapFor(appContext, lutId)
            // Outro preset pode ter sido tocado no meio do caminho:
            // só vale a grade do preset que ainda está selecionado.
            if (_uiState.value.selectedPresetId == presetId) {
                _uiState.update { it.copy(lutGrid = grid) }
            }
        }
    }
}
