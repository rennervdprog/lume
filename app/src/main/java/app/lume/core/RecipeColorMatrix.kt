package app.lume.core

import kotlin.math.pow

/**
 * Converte uma [Recipe] numa matriz de cor 4x5 (20 floats, ordem RGBA).
 *
 * Desde a Fase 1, esta é a ROTA DE FALLBACK do motor: o preview e a
 * exportação passam primeiro pelo shader ([app.lume.imaging.GlEngine])
 * e só caem aqui se o GL falhar — e a tira de presets e as amostras da
 * Biblioteca usam a matriz direto (aproximação rápida dos looks).
 *
 * Se um dia a matriz sumir, preview e exportação continuam idênticos
 * porque ambos passam pela mesma fronteira ([PhotoExporter]).
 *
 * Matemática: operações padrão de cor (ganhos por canal, escala de
 * exposição, contraste com pivô, matriz de saturação com pesos de
 * luminância Rec. 709). Implementação original, escrita para este app.
 *
 * LIMITAÇÃO HONESTA (documentada, não escondida):
 * uma matriz 4x5 é linear — ela NÃO consegue selecionar faixas tonais
 * (só realces, só sombras), vibrance por pixel, HSL, curva tonal nem
 * efeitos. Nesta rota:
 * - realces/sombras/brancos/pretos viram um ajuste tonal combinado
 *   (deslocamento + contraste) que se move na direção certa, mas não é
 *   seletivo por faixa;
 * - vibrance vira saturação mais suave (60% da força);
 * - HSL, curva, claridade, vinheta, grão e desbotado são ignorados.
 * O shader é o caminho que aplica a receita inteira.
 */
object RecipeColorMatrix {

    // Pesos de luminância Rec. 709 (padrão público de vídeo/cor).
    private const val LUMA_R = 0.2126f
    private const val LUMA_G = 0.7152f
    private const val LUMA_B = 0.0722f

    private const val PIVOT = 127.5f

    /** Retorna os 20 floats da matriz, em ordem de linha (RGBA + offset). */
    fun values(recipe: Recipe): FloatArray {
        val r = recipe.clamped()

        // 1. Ganhos de balanço de branco (temperatura/matiz) por canal.
        // Aproximação: o balanço de branco "correto" passa por espaço XYZ;
        // ganhos por canal são a aproximação padrão de editores simples.
        val temp = r.temperature / 100f
        val tint = r.tint / 100f
        val gainR = (1f + temp * 0.28f) * (1f - tint * 0.10f)
        val gainG = (1f + temp * 0.04f) * (1f + tint * 0.20f)
        val gainB = (1f - temp * 0.28f) * (1f - tint * 0.10f)
        val gains = diagonal(gainR, gainG, gainB)

        // 2. Exposição: escala 2^EV.
        val exposureScale = 2f.pow(r.exposure).coerceIn(0.25f, 4f)
        val exposure = diagonal(exposureScale, exposureScale, exposureScale)

        // 3. Contraste + ajuste tonal combinado (aproximação — ver KDoc).
        val contrastFactor = (1f + (r.contrast / 100f) * 0.9f).coerceIn(0.1f, 1.9f)
        val tonalContrast = (1f + (r.blacks - r.highlights) * 0.001f).coerceIn(0.8f, 1.2f)
        val totalContrast = contrastFactor * tonalContrast
        val tonalOffset =
            (r.shadows * 0.30f + r.whites * 0.25f - r.highlights * 0.20f - r.blacks * 0.30f) * 0.5f
        val offset = PIVOT * (1f - totalContrast) + tonalOffset
        val contrast = contrastMatrix(totalContrast, offset)

        // 4. Saturação (+ vibrance como saturação suave — aproximação).
        val saturationAmount =
            (1f + r.saturation / 100f + r.vibrance / 100f * 0.6f).coerceIn(0f, 2.5f)
        val saturation = saturationMatrix(saturationAmount)

        // Ordem de aplicação: ganhos -> exposição -> contraste -> saturação.
        // multiply(a, b) aplica b primeiro e depois a.
        val combined = multiply(saturation, multiply(contrast, multiply(exposure, gains)))
        return combined
    }

    private fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    )

    private fun diagonal(r: Float, g: Float, b: Float): FloatArray {
        val m = identity()
        m[0] = r
        m[6] = g
        m[12] = b
        return m
    }

    private fun contrastMatrix(factor: Float, offset: Float): FloatArray = floatArrayOf(
        factor, 0f, 0f, 0f, offset,
        0f, factor, 0f, 0f, offset,
        0f, 0f, factor, 0f, offset,
        0f, 0f, 0f, 1f, 0f
    )

    private fun saturationMatrix(amount: Float): FloatArray {
        val inv = 1f - amount
        val r = inv * LUMA_R
        val g = inv * LUMA_G
        val b = inv * LUMA_B
        return floatArrayOf(
            r + amount, g, b, 0f, 0f,
            r, g + amount, b, 0f, 0f,
            r, g, b + amount, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    }

    /**
     * Multiplicação de matrizes 4x5 (com a linha implícita [0,0,0,0,1]).
     * O resultado aplica [inner] primeiro e [outer] depois.
     */
    internal fun multiply(outer: FloatArray, inner: FloatArray): FloatArray {
        require(outer.size == 20 && inner.size == 20) { "Matrizes de cor têm 20 floats." }
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var sum = 0f
                for (k in 0 until 4) {
                    sum += outer[row * 5 + k] * inner[k * 5 + col]
                }
                // Coluna de offset (col == 4) soma o offset da matriz externa
                // vezes a linha implícita (1): já incluída quando k cobre a
                // 4ª linha? Não — a linha implícita contribui com outer[row,4]*1
                // apenas na coluna 4. O loop acima com k=3 multiplica
                // outer[row,3] * inner[3,col]; para col==4, inner[3,4] é 0 na
                // linha alfa, então somamos outer[row,4] aqui.
                if (col == 4) {
                    sum += outer[row * 5 + 4]
                }
                out[row * 5 + col] = sum
            }
        }
        // A linha alfa deve permanecer identidade (sem tocar na transparência).
        out[15] = 0f
        out[16] = 0f
        out[17] = 0f
        out[18] = 1f
        out[19] = 0f
        return out
    }
}
