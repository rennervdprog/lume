package app.lume.core

/**
 * Amostra curvas tonais em tabelas (LUT) de 256 entradas — a forma que
 * o shader consome (textura 1D, filtragem linear).
 *
 * Matemática: interpolação linear entre os pontos de controle, com
 * extensão plana além das pontas (x menor que o primeiro ponto usa o
 * y do primeiro; x maior que o último usa o y do último). Saída sempre
 * em 0..1. Funções puras, sem Android: testadas em JVM.
 */
object ToneCurveLut {

    const val SIZE: Int = 256

    /** Amostra [points] em 256 valores 0..1 (índice i = entrada i/255). */
    fun build(points: List<CurvePoint>): FloatArray {
        val sorted = points.sortedBy { it.x }
        val lut = FloatArray(SIZE)
        if (sorted.isEmpty()) {
            for (i in 0 until SIZE) lut[i] = i / (SIZE - 1).toFloat()
            return lut
        }
        if (sorted.size == 1) {
            val y = sorted[0].y.coerceIn(0f, 1f)
            lut.fill(y)
            return lut
        }
        var segment = 0
        for (i in 0 until SIZE) {
            val x = i / (SIZE - 1).toFloat()
            while (
                segment < sorted.size - 2 &&
                x > sorted[segment + 1].x
            ) {
                segment++
            }
            val a = sorted[segment]
            val b = sorted[segment + 1]
            val span = b.x - a.x
            val t = if (span <= 0f) 1f else ((x - a.x) / span).coerceIn(0f, 1f)
            lut[i] = (a.y + (b.y - a.y) * t).coerceIn(0f, 1f)
        }
        return lut
    }

    /** True quando a LUT de [points] é a rampa identidade. */
    fun isIdentity(points: List<CurvePoint>): Boolean {
        val lut = build(points)
        for (i in 0 until SIZE) {
            val expected = i / (SIZE - 1).toFloat()
            if (kotlin.math.abs(lut[i] - expected) > 0.002f) return false
        }
        return true
    }

    /**
     * Empacota as 4 curvas de [curve] em 256×4 bytes RGBA, na ordem
     * R=vermelho, G=verde, B=azul, A=master. O shader amostra um texel
     * por valor de entrada e lê o canal correspondente.
     */
    fun buildRgba(curve: ToneCurve): ByteArray {
        val r = build(curve.red)
        val g = build(curve.green)
        val b = build(curve.blue)
        val master = build(curve.master)
        val bytes = ByteArray(SIZE * 4)
        for (i in 0 until SIZE) {
            bytes[i * 4] = (r[i] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            bytes[i * 4 + 1] = (g[i] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            bytes[i * 4 + 2] = (b[i] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
            bytes[i * 4 + 3] = (master[i] * 255f + 0.5f).toInt().coerceIn(0, 255).toByte()
        }
        return bytes
    }
}
