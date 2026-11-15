package app.lume.data.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.lume.data.local.PreferenceKeys
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Implementação local de visitante. Funcional agora, sem rede, sem
 * segredos: cria um id estável, persiste no DataStore e o expõe como
 * [AccountState.Guest].
 *
 * Login real (Google / e-mail) é TODO atrás da interface — estes métodos
 * retornam [AuthOutcome.NotAvailable] com mensagem honesta, nunca fingem
 * que entraram e nunca guardam senha.
 */
class LocalGuestAuthRepository(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope
) : AuthRepository {

    private val _accountState = MutableStateFlow<AccountState>(
        AccountState.Guest(userId = PENDING_GUEST_ID)
    )
    override val accountState: StateFlow<AccountState> = _accountState.asStateFlow()

    init {
        scope.launch {
            val guestId = ensureGuestId()
            _accountState.value = AccountState.Guest(userId = guestId)
        }
    }

    override suspend fun signInWithGoogle(): AuthOutcome {
        return AuthOutcome.NotAvailable(
            "Entrar com Google ainda não está disponível nesta versão de testes."
        )
    }

    override suspend fun signInWithEmail(email: String, password: String): AuthOutcome {
        return AuthOutcome.NotAvailable(
            "Entrar com e-mail ainda não está disponível nesta versão de testes."
        )
    }

    override suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthOutcome {
        return AuthOutcome.NotAvailable(
            "Criar conta ainda não está disponível nesta versão de testes."
        )
    }

    override suspend fun signOut(): AuthOutcome {
        // Visitante não tem sessão: não há o que encerrar. Quando houver
        // conta real, sair volta ao visitante (sem apagar o trabalho local).
        return AuthOutcome.Success
    }

    override suspend fun deleteAccount(): AuthOutcome {
        return try {
            dataStore.edit { prefs ->
                prefs.remove(PreferenceKeys.GUEST_ID)
            }
            val newGuestId = ensureGuestId()
            _accountState.value = AccountState.Guest(userId = newGuestId)
            AuthOutcome.Success
        } catch (e: Exception) {
            AuthOutcome.Failed("Não foi possível excluir a conta local. Tente de novo.")
        }
    }

    private suspend fun ensureGuestId(): String {
        val existing = dataStore.data
            .map { prefs -> prefs[PreferenceKeys.GUEST_ID] }
            .first()
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.GUEST_ID] = fresh
        }
        return fresh
    }

    companion object {
        // Id temporário até o DataStore responder; nunca é exibido como
        // identidade definitiva (a UI mostra "Visitante" até lá).
        private const val PENDING_GUEST_ID = "guest-pending"
    }
}
