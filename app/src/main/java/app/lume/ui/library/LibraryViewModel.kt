package app.lume.ui.library

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lume.core.Preset
import app.lume.data.PresetLimitReached
import app.lume.core.RecipeJson
import app.lume.data.Draft
import app.lume.data.DraftRepository
import app.lume.data.PresetRepository
import app.lume.data.pro.EntitlementRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val presets: List<Preset> = emptyList(),
    val drafts: List<Draft> = emptyList(),
    val isPro: Boolean = false,
    val message: String? = null,
    val paywallRequested: Boolean = false
)

class LibraryViewModel(
    private val presetRepository: PresetRepository,
    private val draftRepository: DraftRepository,
    private val entitlementRepository: EntitlementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                presetRepository.presets,
                draftRepository.drafts,
                entitlementRepository.isPro
            ) { presets, drafts, isPro ->
                Triple(presets, drafts, isPro)
            }.collect { (presets, drafts, isPro) ->
                _uiState.update {
                    it.copy(presets = presets, drafts = drafts, isPro = isPro)
                }
            }
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            presetRepository.deleteUserPreset(id)
            _uiState.update { it.copy(message = "Preset apagado.") }
        }
    }

    fun deleteDraft(id: String) {
        viewModelScope.launch {
            draftRepository.deleteDraft(id)
        }
    }

    /**
     * Importa presets de um arquivo JSON (formato próprio, ver RecipeJson).
     * Aceita um preset único ou uma lista. JSON inválido = mensagem, nunca
     * crash. Respeita o limite do plano grátis.
     */
    fun importPresets(context: Context, fileUri: Uri) {
        viewModelScope.launch {
            val raw = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(fileUri)?.use { stream ->
                        stream.bufferedReader().readText()
                    }
                } catch (e: Exception) {
                    null
                }
            }
            if (raw.isNullOrBlank()) {
                _uiState.update { it.copy(message = "Não foi possível ler o arquivo.") }
                return@launch
            }
            val imported = RecipeJson.decodePresetList(raw)
                ?: RecipeJson.decodePreset(raw)?.let { listOf(it) }
            if (imported.isNullOrEmpty()) {
                _uiState.update { it.copy(message = "Arquivo sem presets válidos.") }
                return@launch
            }
            var saved = 0
            var hitLimit = false
            for (preset in imported) {
                val result = presetRepository.saveUserPreset(
                    name = preset.name,
                    collection = preset.collection,
                    recipe = preset.recipe
                )
                if (result.isSuccess) {
                    saved++
                } else if (result.exceptionOrNull() is PresetLimitReached) {
                    hitLimit = true
                    break
                }
            }
            _uiState.update {
                it.copy(
                    message = if (hitLimit) {
                        "Importados $saved presets. Limite do plano grátis atingido."
                    } else {
                        "Importados $saved presets."
                    },
                    paywallRequested = hitLimit
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun consumePaywallRequest() {
        _uiState.update { it.copy(paywallRequested = false) }
    }
}
