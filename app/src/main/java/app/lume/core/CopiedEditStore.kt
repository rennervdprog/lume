package app.lume.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Edição copiada no Editor, pronta para colar em rascunhos na Biblioteca. */
data class CopiedEdit(
    val recipe: Recipe,
    val presetName: String?
)

/**
 * Área de transferência de edição, em memória, válida na sessão.
 *
 * Simples de propósito: copiar no Editor e colar em lote na Biblioteca
 * não precisa persistir. Ao fechar o app, a cópia se perde — igual a
 * copiar texto.
 */
object CopiedEditStore {

    private val _copied = MutableStateFlow<CopiedEdit?>(null)
    val copied: StateFlow<CopiedEdit?> = _copied.asStateFlow()

    fun save(recipe: Recipe, presetName: String?) {
        _copied.value = CopiedEdit(recipe.clamped(), presetName)
    }

    fun clear() {
        _copied.value = null
    }
}
