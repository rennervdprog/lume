package app.lume.core

/**
 * Histórico de edição sobre snapshots da receita — NUNCA bitmap por etapa.
 *
 * Um gesto contínuo de slider vira UMA entrada: a UI chama [preview]
 * durante o arrasto e [commit] quando o gesto termina. Presets aplicados
 * e resets também passam por [commit].
 *
 * Puro (sem Android): testável em JVM.
 */
class EditHistory(initial: Recipe = Recipe.IDENTITY) {

    private val past: ArrayDeque<Recipe> = ArrayDeque()
    private val future: ArrayDeque<Recipe> = ArrayDeque()

    var current: Recipe = initial
        private set

    val canUndo: Boolean
        get() = past.isNotEmpty()

    val canRedo: Boolean
        get() = future.isNotEmpty()

    val undoCount: Int
        get() = past.size

    /** Atualização transitória (arrasto de slider): não entra no histórico. */
    fun preview(recipe: Recipe) {
        current = recipe.clamped()
    }

    /** Confirma uma edição: empilha a receita anterior e limpa o redo. */
    fun commit(recipe: Recipe) {
        val next = recipe.clamped()
        if (next == current) return
        past.addLast(current)
        while (past.size > MAX_ENTRIES) {
            past.removeFirst()
        }
        current = next
        future.clear()
    }

    /** Substitui tudo (abrir outra foto, aplicar preset como base nova). */
    fun reset(recipe: Recipe = Recipe.IDENTITY) {
        past.clear()
        future.clear()
        current = recipe.clamped()
    }

    fun undo(): Recipe? {
        if (past.isEmpty()) return null
        future.addLast(current)
        current = past.removeLast()
        return current
    }

    fun redo(): Recipe? {
        if (future.isEmpty()) return null
        past.addLast(current)
        current = future.removeLast()
        return current
    }

    companion object {
        // Snapshots pesam KBs; 100 entradas é generoso sem custo relevante.
        const val MAX_ENTRIES: Int = 100
    }
}
