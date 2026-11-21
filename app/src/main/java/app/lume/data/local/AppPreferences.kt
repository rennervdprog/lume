package app.lume.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/**
 * Preferências locais (DataStore). Só guarda: id do visitante e a flag
 * de simulação Pro de debug. Nenhum segredo, nenhuma credencial, nenhum
 * dado de compra real — a compra real vive na Play + backend (TODO).
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lume_prefs"
)

object PreferenceKeys {
    val GUEST_ID: Preferences.Key<String> = stringPreferencesKey("guest_id")
    val DEBUG_PRO: Preferences.Key<Boolean> = booleanPreferencesKey("debug_pro")
    /** Dica do gesto segurar-pra-ver-original já foi exibida uma vez. */
    val ORIGINAL_HINT_SHOWN: Preferences.Key<Boolean> =
        booleanPreferencesKey("original_hint_shown")
    /** Ids dos últimos presets usados, separados por vírgula (máx. 4). */
    val RECENT_PRESET_IDS: Preferences.Key<String> =
        stringPreferencesKey("recent_preset_ids")
}
