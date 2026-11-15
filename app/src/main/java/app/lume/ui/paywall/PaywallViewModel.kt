package app.lume.ui.paywall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.lume.core.ProPolicy
import app.lume.data.pro.EntitlementRepository
import app.lume.data.pro.RestoreOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PaywallUiState(
    val isPro: Boolean = false,
    val message: String? = null,
    val rows: List<ProPolicy.PaywallRow> = ProPolicy.paywallRows
)

class PaywallViewModel(
    private val entitlementRepository: EntitlementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PaywallUiState())
    val uiState: StateFlow<PaywallUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            entitlementRepository.isPro.collect { isPro ->
                _uiState.update { it.copy(isPro = isPro) }
            }
        }
    }

    /** Simulação local de debug — some quando o Play Billing chegar. */
    fun setDebugPro(enabled: Boolean) {
        viewModelScope.launch {
            entitlementRepository.setDebugPro(enabled)
            _uiState.update {
                it.copy(
                    message = if (enabled) {
                        "Simulação Pro ligada (só nesta versão de testes)."
                    } else {
                        "Simulação Pro desligada."
                    }
                )
            }
        }
    }

    /**
     * Assinatura real é TODO (Play Billing + verificação no servidor).
     * Este botão NUNCA finge uma compra: informa que ainda não está
     * disponível. Preços não aparecem na UI porque são hipótese de teste
     * não validada (estudo §4.5) — nada de prometer preço.
     */
    fun onSubscribeClicked() {
        _uiState.update {
            it.copy(message = "A assinatura ainda não está disponível nesta versão de testes.")
        }
    }

    fun onRestoreClicked() {
        viewModelScope.launch {
            when (val outcome = entitlementRepository.restorePurchases()) {
                is RestoreOutcome.Restored -> {
                    _uiState.update { it.copy(message = "Compras restauradas.") }
                }
                is RestoreOutcome.NothingToRestore -> {
                    _uiState.update { it.copy(message = outcome.message) }
                }
                is RestoreOutcome.NotAvailable -> {
                    _uiState.update { it.copy(message = outcome.message) }
                }
                is RestoreOutcome.Failed -> {
                    _uiState.update { it.copy(message = outcome.message) }
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
