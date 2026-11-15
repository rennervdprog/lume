package app.lume.data.pro

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.lume.data.local.PreferenceKeys
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Implementação local: o Pro é uma flag de debug persistida no DataStore,
 * ligada/desligada na tela do paywall. Serve para testar os dois planos
 * sem Billing. NÃO é prova de compra — ver [EntitlementRepository].
 */
class LocalEntitlementRepository(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope
) : EntitlementRepository {

    private val _isPro = MutableStateFlow(false)
    override val isPro: StateFlow<Boolean> = _isPro.asStateFlow()

    init {
        scope.launch {
            dataStore.data
                .map { prefs -> prefs[PreferenceKeys.DEBUG_PRO] ?: false }
                .collect { flag -> _isPro.value = flag }
        }
    }

    override suspend fun setDebugPro(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.DEBUG_PRO] = enabled
        }
        _isPro.value = enabled
    }

    override suspend fun refresh() {
        // Sem fonte externa na Fase 0: o estado já é o da flag local.
    }

    override suspend fun restorePurchases(): RestoreOutcome {
        return RestoreOutcome.NotAvailable(
            "Restaurar compras ainda não está disponível nesta versão de testes."
        )
    }
}
