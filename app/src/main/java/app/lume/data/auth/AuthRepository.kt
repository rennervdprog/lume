package app.lume.data.auth

import kotlinx.coroutines.flow.StateFlow

/**
 * Estado da conta. A conta NUNCA é muro: o app abre, edita e exporta
 * como visitante. A conta só aparece quando agrega valor (backup de
 * presets, sync, compra) — estudo §5.2.
 */
sealed interface AccountState {
    /** Visitante: id local estável, sem e-mail, sem servidor. */
    data class Guest(val userId: String) : AccountState

    /** Conta definitiva (futura: Google / e-mail). */
    data class SignedIn(
        val userId: String,
        val email: String?,
        val displayName: String?,
        val provider: AuthProvider
    ) : AccountState
}

enum class AuthProvider {
    GOOGLE,
    EMAIL
}

/** Resultado de uma ação de conta, em linguagem de UI (sem exceções cruas). */
sealed interface AuthOutcome {
    data object Success : AuthOutcome
    data class NotAvailable(val message: String) : AuthOutcome
    data class Failed(val message: String) : AuthOutcome
}

/**
 * Abstração de conta (estudo §5.5: camada de abstração desde o início
 * para poder trocar de provedor sem reescrever a UI).
 *
 * TODO (Fase 1, com projeto backend PRÓPRIO — nunca reutilizar o backend
 * de outro app): implementação real com Credential Manager + Google e
 * e-mail/senha no provedor escolhido (Firebase ou Supabase), migração
 * visitante → conta com mesclagem, reautenticação para excluir conta e
 * exclusão no servidor + link web (exigência da Play + LGPD).
 *
 * NENHUMA implementação desta interface pode chamar rede na Fase 0.
 */
interface AuthRepository {
    val accountState: StateFlow<AccountState>

    suspend fun signInWithGoogle(): AuthOutcome

    suspend fun signInWithEmail(email: String, password: String): AuthOutcome

    suspend fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String
    ): AuthOutcome

    suspend fun signOut(): AuthOutcome

    /**
     * Exclui a conta e os dados associados. Na Fase 0 (só local), apaga
     * o registro local do visitante. Com backend, deve excluir também
     * os dados no servidor, após reautenticação e oferta de exportação.
     * Excluir a conta NÃO cancela assinatura na Play — a UI avisa isso.
     */
    suspend fun deleteAccount(): AuthOutcome
}
