package app.lume.ui.editor

/**
 * Ferramenta que o Editor deve abrir, pedida pela Home.
 *
 * Só vive na sessão, em memória: a grade da Home abre o seletor de
 * fotos e, quando a foto chega, o Editor abre já na ferramenta
 * tocada (Ajustar, Cortar, HSL...). O Editor consome com [take] uma
 * única vez ao entrar; abrir um rascunho ou a foto pelo herói não
 * pede nada e o Editor abre no padrão (Ajustar, na Exposição).
 *
 * O QR cai em [EditorTool.FILTER] porque o QR do preset vive no
 * painel de filtros do Editor — não existe tela de QR sozinha.
 *
 * O banner da Home pode pedir um preset ([Target.presetId]): o
 * Editor aplica o preset à foto nova quando ela carrega. Foto com
 * rascunho salvo não recebe o preset — o rascunho vence.
 */
object PendingEditorTarget {

    data class Target(
        val tool: EditorTool,
        val adjustSub: String? = null,
        val presetId: String? = null
    )

    var target: Target? = null

    /** Consome o pedido (ou nulo, se ninguém pediu nada). */
    fun take(): Target? {
        val taken = target
        target = null
        return taken
    }
}
