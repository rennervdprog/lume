package app.lume.data.pro

import app.lume.core.Feature
import app.lume.core.ProPolicy
import kotlinx.coroutines.flow.StateFlow

/** Resultado de restaurar compras, em linguagem de UI. */
sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome
    data class NothingToRestore(val message: String) : RestoreOutcome
    data class NotAvailable(val message: String) : RestoreOutcome
    data class Failed(val message: String) : RestoreOutcome
}

/**
 * Abstração de direitos de acesso (entitlements).
 *
 * TODO (Fase 1): implementação com Play Billing Library + verificação
 * da compra no servidor (Play Developer API / RTDN). A compra pertence
 * à conta Google Play, não à conta do app — o vínculo conta-app deve
 * ser desenhado para o Pro seguir a compra (estudo §4.3).
 *
 * NENHUMA implementação pode tratar flag só-no-cliente como prova de
 * compra em produção: APK modificado burla flag local. A flag local
 * desta fase é EXCLUSIVAMENTE simulação de debug, identificada como tal
 * na UI do paywall.
 */
interface EntitlementRepository {
    /** True quando há direito Pro ativo (nesta fase: só a flag de debug). */
    val isPro: StateFlow<Boolean>

    fun isUnlocked(feature: Feature): Boolean =
        ProPolicy.isUnlocked(feature, isPro.value)

    /** Simulação local de debug. Some quando o Billing real chegar. */
    suspend fun setDebugPro(enabled: Boolean)

    /** Recarrega direitos da fonte de verdade (Play + servidor, no futuro). */
    suspend fun refresh()

    suspend fun restorePurchases(): RestoreOutcome
}
