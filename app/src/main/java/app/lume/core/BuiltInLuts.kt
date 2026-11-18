package app.lume.core

/**
 * Uma cor em 0..1, a unidade de trabalho das tabelas de LUT. Valores
 * fora de 0..1 são aparados na hora de virar pixel da grade.
 */
data class LutColor(val r: Float, val g: Float, val b: Float) {
    fun clamped(): LutColor = LutColor(
        r.coerceIn(0f, 1f),
        g.coerceIn(0f, 1f),
        b.coerceIn(0f, 1f)
    )
}

/** Uma transformação de cor pura: recebe RGB em 0..1, devolve RGB em 0..1. */
typealias LutTransform = (r: Float, g: Float, b: Float) -> LutColor

/** Um LUT nomeado: id estável + nome de exibição + a transformação. */
data class LutDefinition(
    val id: String,
    val name: String,
    val transform: LutTransform
)

/**
 * Os LUTs próprios do Lume: 6 visuais gerados por matemática inventada
 * aqui — nenhum copia LUT de fotógrafo, pack comercial ou concorrente.
 * A grade 512×512 de cada um é gerada sob demanda (ver
 * [app.lume.imaging.LutGrid]) e a prova de teste em pele e luz
 * brasileiras continua pendente, igual à dos presets.
 *
 * Os ids são estáveis e entram na receita ([Recipe.lutId]): nunca
 * renomear um id — receitas e presets salvos referenciam por ele.
 */
object BuiltInLuts {

    val all: List<LutDefinition> = listOf(
        LutDefinition(
            id = "lut-teal-orange",
            name = "Laranja & Verde-azulado",
            transform = ::tealOrange
        ),
        LutDefinition(
            id = "lut-faded-film",
            name = "Filme Desbotado",
            transform = ::fadedFilm
        ),
        LutDefinition(
            id = "lut-golden",
            name = "Dourado Quente",
            transform = ::golden
        ),
        LutDefinition(
            id = "lut-cool-urban",
            name = "Frio Urbano",
            transform = ::coolUrban
        ),
        LutDefinition(
            id = "lut-bw-contrast",
            name = "P&B Contraste",
            transform = ::bwContrast
        ),
        LutDefinition(
            id = "lut-vintage",
            name = "Vintage Suave",
            transform = ::vintage
        )
    )

    fun definition(id: String?): LutDefinition? =
        all.firstOrNull { it.id == id }

    fun displayName(id: String?): String? = definition(id)?.name

    // ---- As 6 matemáticas (todas originais, valores inventados aqui) ----

    /**
     * Cinema teal & orange: sombras puxam para o verde-azulado,
     * realces para o laranja, com um ganho leve de saturação. O peso
     * de cada lado segue a luminância, sem fronteira visível.
     */
    private fun tealOrange(r: Float, g: Float, b: Float): LutColor {
        val luma = luminance(r, g, b)
        val shadowW = 1f - smoothstep(0.15f, 0.65f, luma)
        val highlightW = smoothstep(0.45f, 0.9f, luma)
        val boosted = saturate(r, g, b, 1.12f)
        return LutColor(
            boosted.r - 0.055f * shadowW + 0.075f * highlightW,
            boosted.g + 0.028f * shadowW + 0.018f * highlightW,
            boosted.b + 0.055f * shadowW - 0.045f * highlightW
        ).clamped()
    }

    /**
     * Filme desbotado: pretos levantados, brancos comprimidos e
     * saturação reduzida — o "mate" dos filmes antigos.
     */
    private fun fadedFilm(r: Float, g: Float, b: Float): LutColor {
        val lifted = LutColor(
            0.075f + r * 0.85f,
            0.075f + g * 0.85f,
            0.08f + b * 0.85f
        )
        return saturate(lifted.r, lifted.g, lifted.b, 0.82f).clamped()
    }

    /** Dourado quente: ganhos quentes nos canais + saturação. */
    private fun golden(r: Float, g: Float, b: Float): LutColor {
        val warmed = LutColor(r * 1.09f, g * 1.02f, b * 0.87f)
        return saturate(warmed.r, warmed.g, warmed.b, 1.15f).clamped()
    }

    /** Frio urbano: ganhos frios, saturação menor, contraste leve. */
    private fun coolUrban(r: Float, g: Float, b: Float): LutColor {
        val cooled = LutColor(r * 0.92f, g * 0.99f, b * 1.08f)
        val desaturated = saturate(cooled.r, cooled.g, cooled.b, 0.9f)
        return LutColor(
            contrast(desaturated.r, 1.08f),
            contrast(desaturated.g, 1.08f),
            contrast(desaturated.b, 1.08f)
        ).clamped()
    }

    /** Preto e branco com contraste: luminância + curva em S suave. */
    private fun bwContrast(r: Float, g: Float, b: Float): LutColor {
        val luma = luminance(r, g, b)
        val graded = contrast(luma, 1.28f)
        return LutColor(graded, graded, graded).clamped()
    }

    /**
     * Vintage suave: matriz sépia parcial (60% sépia, 40% original)
     * + levantamento leve dos pretos. O sépia total envelhece
     * demais; a mistura mantém a foto legível.
     */
    private fun vintage(r: Float, g: Float, b: Float): LutColor {
        val sepiaR = 0.393f * r + 0.769f * g + 0.189f * b
        val sepiaG = 0.349f * r + 0.686f * g + 0.168f * b
        val sepiaB = 0.272f * r + 0.534f * g + 0.131f * b
        val mixed = LutColor(
            r + (sepiaR - r) * 0.6f,
            g + (sepiaG - g) * 0.6f,
            b + (sepiaB - b) * 0.6f
        )
        return LutColor(
            0.03f + mixed.r * 0.95f,
            0.03f + mixed.g * 0.95f,
            0.03f + mixed.b * 0.95f
        ).clamped()
    }

    // ---- Ajudantes ----

    private fun luminance(r: Float, g: Float, b: Float): Float =
        0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun saturate(r: Float, g: Float, b: Float, amount: Float): LutColor {
        val luma = luminance(r, g, b)
        return LutColor(
            luma + (r - luma) * amount,
            luma + (g - luma) * amount,
            luma + (b - luma) * amount
        )
    }

    private fun contrast(v: Float, amount: Float): Float =
        0.5f + (v - 0.5f) * amount
}
