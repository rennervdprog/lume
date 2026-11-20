package app.lume.core

/**
 * Um LUT 3D lido de um arquivo .cube: a grade de [size]³ cores, com
 * o vermelho variando mais rápido (ordem do formato .cube), seguida
 * do verde e do azul. [data] guarda RGB triplo por ponto:
 * índice ((b * size + g) * size + r) * 3.
 *
 * [domainMin]/[domainMax] definem a faixa de entrada que a grade
 * cobre (quase sempre 0..1). Valores fora do domínio são aparados.
 */
data class ParsedCube(
    val size: Int,
    val title: String?,
    val domainMin: FloatArray,
    val domainMax: FloatArray,
    val data: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParsedCube) return false
        return size == other.size &&
            title == other.title &&
            domainMin.contentEquals(other.domainMin) &&
            domainMax.contentEquals(other.domainMax) &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = size
        result = 31 * result + (title?.hashCode() ?: 0)
        result = 31 * result + domainMin.contentHashCode()
        result = 31 * result + domainMax.contentHashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}

/** Resultado da leitura de um .cube: a grade, ou o motivo da recusa. */
sealed interface CubeParseResult {
    data class Ok(val cube: ParsedCube) : CubeParseResult
    data class Fail(val reason: String) : CubeParseResult
}

/**
 * Leitor do formato .cube (Adobe/IRIDAS): texto simples, uma linha
 * por ponto da grade. Suporta LUT 3D (LUT_3D_SIZE); LUT 1D é
 * recusado — o motor do Lume só aplica grade 3D.
 *
 * Nunca estoura: qualquer arquivo fora do formato volta [Fail] com
 * o motivo em português, para mostrar ao usuário. Linhas de
 * comentário (#) e palavras-chave desconhecidas são ignoradas.
 */
object CubeParser {

    private const val MAX_SIZE = 64
    private const val MIN_SIZE = 2

    fun parse(text: String): CubeParseResult {
        var size: Int? = null
        var title: String? = null
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>()
        var sawData = false

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val tokens = line.split(Regex("\\s+"))
            val head = tokens.first()
            when {
                head.equals("TITLE", ignoreCase = true) -> {
                    title = line.substringAfter("TITLE").trim()
                        .trim('"', '\'').ifBlank { null }
                }
                head.equals("LUT_1D_SIZE", ignoreCase = true) -> {
                    return CubeParseResult.Fail(
                        "Este é um LUT 1D — o Solarize só importa LUT 3D (.cube)."
                    )
                }
                head.equals("LUT_3D_SIZE", ignoreCase = true) -> {
                    if (sawData) {
                        return CubeParseResult.Fail(
                            "Tamanho da grade declarado depois dos dados."
                        )
                    }
                    val parsed = tokens.getOrNull(1)?.toIntOrNull()
                        ?: return CubeParseResult.Fail(
                            "Tamanho da grade inválido."
                        )
                    if (parsed < MIN_SIZE || parsed > MAX_SIZE) {
                        return CubeParseResult.Fail(
                            "Tamanho da grade $parsed fora do suportado (2 a 64)."
                        )
                    }
                    size = parsed
                }
                head.equals("DOMAIN_MIN", ignoreCase = true) -> {
                    domainMin = readTriplet(tokens)
                        ?: return CubeParseResult.Fail("DOMAIN_MIN inválido.")
                }
                head.equals("DOMAIN_MAX", ignoreCase = true) -> {
                    domainMax = readTriplet(tokens)
                        ?: return CubeParseResult.Fail("DOMAIN_MAX inválido.")
                }
                head.equals("LUT_1D_INPUT_RANGE", ignoreCase = true) ||
                    head.equals("LUT_3D_INPUT_RANGE", ignoreCase = true) -> {
                    // Faixa de entrada 1D: ignorada (o domínio 3D manda).
                }
                looksLikeData(tokens) -> {
                    val parsedSize = size
                        ?: return CubeParseResult.Fail(
                            "Faltou o tamanho da grade (LUT_3D_SIZE)."
                        )
                    if (parsedSize < MIN_SIZE || parsedSize > MAX_SIZE) {
                        return CubeParseResult.Fail(
                            "Tamanho da grade $parsedSize fora do suportado (2 a 64)."
                        )
                    }
                    sawData = true
                    val r = tokens[0].toFloatOrNull()
                    val g = tokens[1].toFloatOrNull()
                    val b = tokens[2].toFloatOrNull()
                    if (r == null || g == null || b == null) {
                        return CubeParseResult.Fail(
                            "Linha de dados inválida: \"$line\"."
                        )
                    }
                    values.add(r)
                    values.add(g)
                    values.add(b)
                }
                else -> {
                    // Palavra-chave desconhecida: ignora (o formato
                    // permite comentários de ferramentas).
                }
            }
        }

        val finalSize = size
            ?: return CubeParseResult.Fail("Faltou o tamanho da grade (LUT_3D_SIZE).")
        val expected = finalSize * finalSize * finalSize * 3
        if (values.size != expected) {
            return CubeParseResult.Fail(
                "O arquivo tem ${values.size / 3} cores, mas a grade $finalSize " +
                    "precisa de ${expected / 3}."
            )
        }
        return CubeParseResult.Ok(
            ParsedCube(
                size = finalSize,
                title = title,
                domainMin = domainMin,
                domainMax = domainMax,
                data = values.toFloatArray()
            )
        )
    }

    private fun looksLikeData(tokens: List<String>): Boolean {
        if (tokens.size != 3) return false
        // Linha de dados = 3 números. Qualquer outra coisa com 3
        // tokens (ex.: palavra-chave desconhecida) cai no ramo
        // genérico e é ignorada.
        return tokens.all { it.toFloatOrNull() != null }
    }

    private fun readTriplet(tokens: List<String>): FloatArray? {
        if (tokens.size != 4) return null
        val a = tokens[1].toFloatOrNull() ?: return null
        val b = tokens[2].toFloatOrNull() ?: return null
        val c = tokens[3].toFloatOrNull() ?: return null
        return floatArrayOf(a, b, c)
    }
}
