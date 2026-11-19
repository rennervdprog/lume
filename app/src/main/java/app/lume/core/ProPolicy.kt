package app.lume.core

/**
 * Tabela Grátis vs Pro codificada como feature flags.
 *
 * Fonte: estudo avançado §4.4 (tabela proposta com justificativa por
 * item). Esta é a ÚNICA fonte da regra de acesso: a UI e os repositórios
 * consultam [isUnlocked], nunca reinventam a regra.
 *
 * Recursos que ainda não existem (máscaras, lote, RAW...) já estão
 * enumerados aqui para o paywall e o roteiro não divergirem — a flag
 * existe antes da feature, e a feature nasce já respeitando a flag.
 *
 * NUNCA TRANCA (regra dura do estudo §4.4, testada em ProPolicyTest):
 * exportação total, presets do usuário, desfazer/histórico, abertura
 * sem conta, exclusão de conta/dados.
 */
enum class Feature {
    LIGHT_GLOBAL,
    COLOR_GLOBAL,
    CURVES_BASIC,
    CURVES_LIBRARY,
    HSL_BASIC,
    HSL_FULL,
    EFFECTS_DETAIL,
    CROP,
    GEOMETRY_BASIC,
    GEOMETRY_FULL,
    OWN_PRESETS,
    OWN_PRESETS_UNLIMITED,
    BUILT_IN_PRESETS,
    CREATOR_PRESETS,
    PRESET_INTENSITY,
    MASKS_BASIC,
    MASKS_FULL,
    AI_MASK,
    HEAL_BASIC,
    HEAL_FULL,
    BACKGROUND_REMOVAL,
    GENERATIVE_AI,
    RAW_BASIC,
    RAW_FULL,
    BATCH_SMALL,
    BATCH_UNLIMITED,
    EXPORT_FULL,
    EXPORT_FORMATS,
    HISTORY,
    CAMERA_BASIC,
    CAMERA_MANUAL,
    SYNC_PRESETS,
    SYNC_EDITS,
    SUPPORT_PRIORITY,
    /** Ajustar Pro (0.14.0): níveis, balanço de cor, névoa, gama, giro de cor. */
    ADJUST_PRO,
    /** Desfoque gaussiano + suavizar pele (0.14.0). */
    BLUR_SKIN,
    /** Os 10 efeitos artísticos (0.14.0). */
    ARTISTIC_EFFECTS,
    /** Mistura / dupla exposição (0.14.0). */
    BLEND_MODES
}

object ProPolicy {

    /** Recursos liberados para todo mundo, para sempre. */
    private val freeFeatures: Set<Feature> = setOf(
        Feature.LIGHT_GLOBAL,
        Feature.COLOR_GLOBAL,
        Feature.CURVES_BASIC,
        Feature.HSL_BASIC,
        Feature.EFFECTS_DETAIL,
        Feature.CROP,
        Feature.GEOMETRY_BASIC,
        Feature.OWN_PRESETS,
        Feature.BUILT_IN_PRESETS,
        Feature.PRESET_INTENSITY,
        Feature.MASKS_BASIC,
        Feature.HEAL_BASIC,
        Feature.RAW_BASIC,
        Feature.BATCH_SMALL,
        Feature.EXPORT_FULL,
        Feature.HISTORY,
        Feature.CAMERA_BASIC,
        Feature.SYNC_PRESETS
    )

    /**
     * Recursos que NUNCA podem ser trancados, sob nenhuma hipótese.
     * Subconjunto de [freeFeatures], verificado por teste unitário.
     * Abertura sem conta e exclusão de conta/dados não são [Feature]
     * porque não passam por paywall nenhum — são garantias da arquitetura
     * (ver AuthRepository e AccountScreen).
     */
    val neverLocked: Set<Feature> = setOf(
        Feature.EXPORT_FULL,
        Feature.OWN_PRESETS,
        Feature.HISTORY,
        Feature.PRESET_INTENSITY
    )

    fun isUnlocked(feature: Feature, isPro: Boolean): Boolean {
        if (feature in freeFeatures) return true
        if (feature == Feature.GENERATIVE_AI) return false // Créditos, Fase 4+. Nunca "Pro ilimitado".
        return isPro
    }

    /** Limite de presets próprios no plano grátis (estudo §4.4). */
    fun ownPresetLimit(isPro: Boolean): Int =
        if (isPro) Int.MAX_VALUE else FREE_OWN_PRESET_LIMIT

    /** Limite de fotos por lote no plano grátis (estudo §4.4). */
    fun batchLimit(isPro: Boolean): Int =
        if (isPro) Int.MAX_VALUE else FREE_BATCH_LIMIT

    /**
     * Canais HSL liberados no plano grátis: os 4 primeiros (vermelho,
     * laranja, amarelo, verde — os da pele e da luz brasileira). Os
     * outros 4 exigem Pro (flag HSL_FULL). Índice fora de 0..7 = trancado.
     */
    fun hslChannelUnlocked(channelIndex: Int, isPro: Boolean): Boolean {
        if (channelIndex !in 0 until HslChannels.COUNT) return false
        if (isPro) return true
        return channelIndex < FREE_HSL_CHANNELS
    }

    const val FREE_OWN_PRESET_LIMIT: Int = 10
    const val FREE_BATCH_LIMIT: Int = 5
    const val FREE_AI_TRIAL_USES: Int = 3
    const val FREE_HSL_CHANNELS: Int = 4

    /** Linha da tabela do paywall. Textos em PT-BR, sem nome de concorrente. */
    data class PaywallRow(
        val label: String,
        val freeText: String,
        val proText: String
    )

    /** Tabela exibida no paywall — mesmo conteúdo da §4.4, resumido. */
    val paywallRows: List<PaywallRow> = listOf(
        PaywallRow("Luz e cor", "Total", "Total"),
        PaywallRow("Curvas", "Master + RGB", "Curvas salvas"),
        PaywallRow("HSL", "4 canais essenciais", "8 canais + toque na foto"),
        PaywallRow("Ajustar Pro", "—", "Níveis, balanço, névoa, gama e giro de cor"),
        PaywallRow("Desfoque e pele", "—", "Desfoque e suavizar pele"),
        PaywallRow("Efeitos artísticos", "—", "10 efeitos"),
        PaywallRow("Mistura", "—", "Dupla exposição"),
        PaywallRow("Efeitos e detalhe", "Total", "Total"),
        PaywallRow("Cortar e proporções", "Total", "Total"),
        PaywallRow("Geometria", "Básico", "Completa + correção de lente"),
        PaywallRow("Seus presets", "Até 10", "Ilimitados + pastas"),
        PaywallRow("Presets do app", "Coleções essenciais", "Todas + lançamentos"),
        PaywallRow("Presets de criadores", "Amostra grátis", "Catálogo completo"),
        PaywallRow("Máscaras", "1 por foto", "Ilimitadas + refinamento"),
        PaywallRow("Máscara por IA", "3 usos de teste", "Ilimitada no aparelho"),
        PaywallRow("Remover manchas", "Manchas pequenas", "Completo"),
        PaywallRow("Remover fundo", "3 usos de teste", "No aparelho"),
        PaywallRow("RAW e DNG", "DNG do celular", "RAW de câmera"),
        PaywallRow("Edição em lote", "Até 5 fotos", "Ilimitada"),
        PaywallRow("Exportação", "Total, sem marca d'água", "Formatos e automação"),
        PaywallRow("Histórico", "Total no aparelho", "Total + versões na nuvem"),
        PaywallRow("Câmera", "Básica", "Manual + DNG"),
        PaywallRow("Conta e backup", "Presets e favoritos", "Edições e rascunhos"),
        PaywallRow("Anúncios e marca d'água", "Nunca", "Nunca")
    )
}
