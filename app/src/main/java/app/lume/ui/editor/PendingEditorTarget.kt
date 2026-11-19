package app.lume.ui.editor

/**
 * Ferramenta que o Editor deve abrir, pedida pela Home.
 *
 * Só vive na sessão, em memória: a aba Presets abre o seletor de
 * fotos e, quando a foto chega, o Editor abre já com o preset
 * pedido aplicado (ou na ferramenta pedida, quando há). O Editor
 * consome com [take] uma única vez ao entrar; abrir um rascunho,
 * uma foto da grade ou a foto pelo botão + não pede nada e o
 * Editor abre no padrão (Ajustar, na Exposição).
 *
 * O preset pedido ([Target.presetId]) só entra em foto sem edição:
 * foto com rascunho salvo não recebe o preset — o rascunho vence.
 */
object PendingEditorTarget {

    data class Target(
        val tool: EditorTool,
        val adjustSub: String? = null,
        val presetId: String? = null,
        /**
         * Aba com que o painel Filtro deve abrir (só vale quando
         * [tool] é FILTER). Nulo abre na aba Todos, o padrão.
         */
        val filterTab: FilterTab? = null
    )

    var target: Target? = null

    /** Consome o pedido (ou nulo, se ninguém pediu nada). */
    fun take(): Target? {
        val taken = target
        target = null
        return taken
    }
}
