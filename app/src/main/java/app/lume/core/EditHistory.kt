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

    /**
     * Receita de ANTES do gesto atual (arrasto de slider). O primeiro
     * [preview] do gesto guarda a base; o [commit] do fim do gesto
     * empilha a base — sem isso, o arrasto sumiria do desfazer, porque
     * o preview já moveu [current] para o valor final.
     */
    private var gestureBase: Recipe? = null

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
        if (gestureBase == null) {
            gestureBase = current
        }
        current = recipe.clamped()
    }

    /** Confirma uma edição: empilha a receita anterior e limpa o redo. */
    fun commit(recipe: Recipe) {
        val next = recipe.clamped()
        val base = gestureBase
        gestureBase = null
        if (base != null) {
            // Fim de gesto: uma única entrada com a receita de ANTES
            // do gesto, para o desfazer voltar ao início do arrasto.
            if (next == base) return
            past.addLast(base)
            trim()
            current = next
            future.clear()
            return
        }
        if (next == current) return
        past.addLast(current)
        trim()
        current = next
        future.clear()
    }

    /** Substitui tudo (abrir outra foto, aplicar preset como base nova). */
    fun reset(recipe: Recipe = Recipe.IDENTITY) {
        past.clear()
        future.clear()
        gestureBase = null
        current = recipe.clamped()
    }

    fun undo(): Recipe? {
        if (past.isEmpty()) return null
        gestureBase = null
        future.addLast(current)
        current = past.removeLast()
        return current
    }

    fun redo(): Recipe? {
        if (future.isEmpty()) return null
        gestureBase = null
        past.addLast(current)
        current = future.removeLast()
        return current
    }

    private fun trim() {
        while (past.size > MAX_ENTRIES) {
            past.removeFirst()
        }
    }

    companion object {
        // Snapshots pesam KBs; 100 entradas é generoso sem custo relevante.
        const val MAX_ENTRIES: Int = 100
    }
}
