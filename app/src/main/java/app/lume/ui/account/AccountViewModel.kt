package app.lume.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lume.core.RecipeJson
import app.lume.data.DraftRepository
import app.lume.data.PresetRepository
import app.lume.data.auth.AccountState
import app.lume.data.auth.AuthOutcome
import app.lume.data.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val accountState: AccountState = AccountState.Guest(userId = ""),
    val userPresetCount: Int = 0,
    val draftCount: Int = 0,
    val message: String? = null,
    /** JSON dos presets do usuário, pronto para compartilhar (exportar). */
    val exportPayload: String? = null
)

class AccountViewModel(
    private val authRepository: AuthRepository,
    private val presetRepository: PresetRepository,
    private val draftRepository: DraftRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.accountState.collect { state ->
                _uiState.update { it.copy(accountState = state) }
            }
        }
        viewModelScope.launch {
            presetRepository.userPresetCount.collect { count ->
                _uiState.update { it.copy(userPresetCount = count) }
            }
        }
        viewModelScope.launch {
            draftRepository.drafts.collect { drafts ->
                _uiState.update { it.copy(draftCount = drafts.size) }
            }
        }
    }

    fun signInWithGoogle() {
        viewModelScope.launch {
            showOutcome(authRepository.signInWithGoogle())
        }
    }

    /** E-mail/senha é TODO atrás da interface; a UI mostra isso com clareza. */
    fun emailAuthNotAvailable() {
        _uiState.update {
            it.copy(
                message = "Entrar com e-mail ainda não está disponível nesta versão de testes."
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            showOutcome(authRepository.signOut())
        }
    }

    /**
     * Exclui a conta local. Presets e rascunhos NESTE aparelho não são
     * apagados — são trabalho do usuário no próprio aparelho; a exclusão
     * remove o registro da conta. A UI oferece exportar antes e explica
     * o que é apagado (estudo §5.4). Com backend, este fluxo ganha
     * reautenticação + exclusão no servidor + link web.
     */
    fun deleteAccount() {
        viewModelScope.launch {
            val outcome = authRepository.deleteAccount()
            if (outcome is AuthOutcome.Success) {
                _uiState.update {
                    it.copy(message = "Conta local excluída. Seus presets neste aparelho foram mantidos.")
                }
            } else {
                showOutcome(outcome)
            }
        }
    }

    /** Prepara o JSON dos presets do usuário para exportar/compartilhar. */
    fun prepareExport() {
        viewModelScope.launch {
            val userPresets = presetRepository.presets.first()
                .filter { !it.isBuiltIn }
            if (userPresets.isEmpty()) {
                _uiState.update { it.copy(message = "Você ainda não criou presets.") }
                return@launch
            }
            _uiState.update {
                it.copy(exportPayload = RecipeJson.encodePresetList(userPresets))
            }
        }
    }

    fun consumeExportPayload() {
        _uiState.update { it.copy(exportPayload = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun showOutcome(outcome: AuthOutcome) {
        when (outcome) {
            is AuthOutcome.Success -> {
                _uiState.update { it.copy(message = "Pronto.") }
            }
            is AuthOutcome.NotAvailable -> {
                _uiState.update { it.copy(message = outcome.message) }
            }
            is AuthOutcome.Failed -> {
                _uiState.update { it.copy(message = outcome.message) }
            }
        }
    }
}
