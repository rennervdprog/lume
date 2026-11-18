package app.lume.imaging

import android.graphics.Bitmap
import app.lume.core.LutColor
import app.lume.core.LutTransform
import app.lume.core.ParsedCube

/**
 * A grade de um LUT no formato HALD 64: uma imagem 512×512 formada
 * por 8×8 blocos de 64×64. Cada bloco é um nível de azul (o bloco
 * [b] fica na coluna b % 8, linha b / 8, contando do topo); dentro
 * do bloco, a coluna é o vermelho e a linha é o verde, ambos de
 * 0 a 63. O shader ([LutFilter]) amostra esta grade direto — o
 * formato é o padrão que todo .cube convertido vira.
 *
 * A grade vive como [pixels] (ARGB, linha a linha) para a amostragem
 * em CPU ser rápida ([sample], usada nas miniaturas da tira) e a
 * conversão para bitmap da GPU ser uma cópia ([toBitmap]). Gerar a
 * grade é matemática pura — sem Android — então os testes de unidade
 * provam a grade sem emulador; só [toBitmap]/[fromBitmap] tocam
 * em [Bitmap].
 *
 * A orientação (qual eixo é linha/coluna, e se o verde cresce para
 * baixo) é provada pelos testes de pixel da GPU
 * (`GpuImagePipelineTest`, no source set de testes): um LUT que
 * troca os canais só passa se a grade e o shader concordarem.
 */
class LutGrid(val pixels: IntArray) {

    init {
        require(pixels.size == SIZE * SIZE) {
            "Grade LUT precisa de ${SIZE * SIZE} pixels."
        }
    }

    /**
     * Amostra a grade em (r, g, b) 0..1 com interpolação trilinear
     * sobre os 8 vizinhos da grade 64³. Usado nas miniaturas da tira
     * de LUTs (CPU) — o preview e a exportação usam a GPU.
     */
    fun sample(r: Float, g: Float, b: Float): LutColor {
        val x = (r.coerceIn(0f, 1f) * (LEVELS - 1)).toDouble()
        val y = (g.coerceIn(0f, 1f) * (LEVELS - 1)).toDouble()
        val z = (b.coerceIn(0f, 1f) * (LEVELS - 1)).toDouble()
        val x0 = x.toInt().coerceIn(0, LEVELS - 2)
        val y0 = y.toInt().coerceIn(0, LEVELS - 2)
        val z0 = z.toInt().coerceIn(0, LEVELS - 2)
        val fx = (x - x0).toFloat()
        val fy = (y - y0).toFloat()
        val fz = (z - z0).toFloat()
        val c000 = lattice(x0, y0, z0)
        val c100 = lattice(x0 + 1, y0, z0)
        val c010 = lattice(x0, y0 + 1, z0)
        val c110 = lattice(x0 + 1, y0 + 1, z0)
        val c001 = lattice(x0, y0, z0 + 1)
        val c101 = lattice(x0 + 1, y0, z0 + 1)
        val c011 = lattice(x0, y0 + 1, z0 + 1)
        val c111 = lattice(x0 + 1, y0 + 1, z0 + 1)
        return LutColor(
            trilinear(c000.r, c100.r, c010.r, c110.r, c001.r, c101.r, c011.r, c111.r, fx, fy, fz),
            trilinear(c000.g, c100.g, c010.g, c110.g, c001.g, c101.g, c011.g, c111.g, fx, fy, fz),
            trilinear(c000.b, c100.b, c010.b, c110.b, c001.b, c101.b, c011.b, c111.b, fx, fy, fz)
        )
    }

    /**
     * Aplica a grade a [source] pixel a pixel (CPU) e devolve um
     * bitmap novo. O chamador reduz a foto antes — isto é para
     * miniaturas, não para a foto inteira.
     */
    fun applyTo(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val input = IntArray(width * height)
        source.getPixels(input, 0, width, 0, 0, width, height)
        val output = IntArray(width * height)
        for (i in input.indices) {
            val pixel = input[i]
            val sampled = sample(
                ((pixel shr 16) and 0xFF) / 255f,
                ((pixel shr 8) and 0xFF) / 255f,
                (pixel and 0xFF) / 255f
            ).clamped()
            output[i] = (0xFF shl 24) or
                ((sampled.r * 255f).toInt() shl 16) or
                ((sampled.g * 255f).toInt() shl 8) or
                (sampled.b * 255f).toInt()
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(output, 0, width, 0, 0, width, height)
        }
    }

    /** A grade como bitmap 512×512, pronto para subir como textura. */
    fun toBitmap(): Bitmap =
        Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        }

    private fun lattice(r: Int, g: Int, b: Int): LutColor {
        val x = (b % TILES_PER_SIDE) * TILE + r
        val y = (b / TILES_PER_SIDE) * TILE + g
        val pixel = pixels[y * SIZE + x]
        return LutColor(
            ((pixel shr 16) and 0xFF) / 255f,
            ((pixel shr 8) and 0xFF) / 255f,
            (pixel and 0xFF) / 255f
        )
    }

    private fun trilinear(
        c000: Float, c100: Float, c010: Float, c110: Float,
        c001: Float, c101: Float, c011: Float, c111: Float,
        fx: Float, fy: Float, fz: Float
    ): Float {
        val x00 = c000 + (c100 - c000) * fx
        val x10 = c010 + (c110 - c010) * fx
        val x01 = c001 + (c101 - c001) * fx
        val x11 = c011 + (c111 - c011) * fx
        val y0 = x00 + (x10 - x00) * fy
        val y1 = x01 + (x11 - x01) * fy
        return y0 + (y1 - y0) * fz
    }

    companion object {
        const val SIZE: Int = 512
        const val TILE: Int = 64
        const val LEVELS: Int = 64
        const val TILES_PER_SIDE: Int = 8

        /** Grade identidade: amostrar devolve a própria cor. */
        fun identity(): LutGrid = fromTransform { r, g, b -> LutColor(r, g, b) }

        /** Gera a grade aplicando [transform] a cada ponto da grade 64³. */
        fun fromTransform(transform: LutTransform): LutGrid {
            val pixels = IntArray(SIZE * SIZE)
            for (b in 0 until LEVELS) {
                val tileX = (b % TILES_PER_SIDE) * TILE
                val tileY = (b / TILES_PER_SIDE) * TILE
                val bf = b / (LEVELS - 1).toFloat()
                for (g in 0 until LEVELS) {
                    val gf = g / (LEVELS - 1).toFloat()
                    val row = (tileY + g) * SIZE + tileX
                    for (r in 0 until LEVELS) {
                        val rf = r / (LEVELS - 1).toFloat()
                        val out = transform(rf, gf, bf).clamped()
                        pixels[row + r] = (0xFF shl 24) or
                            ((out.r * 255f).toInt() shl 16) or
                            ((out.g * 255f).toInt() shl 8) or
                            (out.b * 255f).toInt()
                    }
                }
            }
            return LutGrid(pixels)
        }

        /**
         * Converte um .cube ([ParsedCube], ver `CubeParser`) para a
         * grade 64³, reamostrando a grade do arquivo com interpolação
         * trilinear e respeitando o domínio declarado.
         */
        fun fromCube(cube: ParsedCube): LutGrid {
            val size = cube.size
            val pixels = IntArray(SIZE * SIZE)
            for (b in 0 until LEVELS) {
                val tileX = (b % TILES_PER_SIDE) * TILE
                val tileY = (b / TILES_PER_SIDE) * TILE
                val bf = b / (LEVELS - 1).toFloat()
                for (g in 0 until LEVELS) {
                    val gf = g / (LEVELS - 1).toFloat()
                    val row = (tileY + g) * SIZE + tileX
                    for (r in 0 until LEVELS) {
                        val rf = r / (LEVELS - 1).toFloat()
                        val out = sampleCube(cube, rf, gf, bf).clamped()
                        pixels[row + r] = (0xFF shl 24) or
                            ((out.r * 255f).toInt() shl 16) or
                            ((out.g * 255f).toInt() shl 8) or
                            (out.b * 255f).toInt()
                    }
                }
            }
            return LutGrid(pixels)
        }

        private fun sampleCube(cube: ParsedCube, r: Float, g: Float, b: Float): LutColor {
            val size = cube.size
            val span = (size - 1).toFloat()
            // Entrada fora do domínio: apara na borda da grade.
            val x = (((r - cube.domainMin[0]) / domainSpan(cube, 0)) * span)
                .coerceIn(0f, span).toDouble()
            val y = (((g - cube.domainMin[1]) / domainSpan(cube, 1)) * span)
                .coerceIn(0f, span).toDouble()
            val z = (((b - cube.domainMin[2]) / domainSpan(cube, 2)) * span)
                .coerceIn(0f, span).toDouble()
            val x0 = x.toInt().coerceIn(0, size - 2)
            val y0 = y.toInt().coerceIn(0, size - 2)
            val z0 = z.toInt().coerceIn(0, size - 2)
            val fx = (x - x0).toFloat()
            val fy = (y - y0).toFloat()
            val fz = (z - z0).toFloat()
            fun point(ri: Int, gi: Int, bi: Int): LutColor {
                val idx = ((bi * size + gi) * size + ri) * 3
                return LutColor(cube.data[idx], cube.data[idx + 1], cube.data[idx + 2])
            }
            val c000 = point(x0, y0, z0)
            val c100 = point(x0 + 1, y0, z0)
            val c010 = point(x0, y0 + 1, z0)
            val c110 = point(x0 + 1, y0 + 1, z0)
            val c001 = point(x0, y0, z0 + 1)
            val c101 = point(x0 + 1, y0, z0 + 1)
            val c011 = point(x0, y0 + 1, z0 + 1)
            val c111 = point(x0 + 1, y0 + 1, z0 + 1)
            fun mix(
                v000: Float, v100: Float, v010: Float, v110: Float,
                v001: Float, v101: Float, v011: Float, v111: Float
            ): Float {
                val x00 = v000 + (v100 - v000) * fx
                val x10 = v010 + (v110 - v010) * fx
                val x01 = v001 + (v101 - v001) * fx
                val x11 = v011 + (v111 - v011) * fx
                val y0v = x00 + (x10 - x00) * fy
                val y1v = x01 + (x11 - x01) * fy
                return y0v + (y1v - y0v) * fz
            }
            return LutColor(
                mix(c000.r, c100.r, c010.r, c110.r, c001.r, c101.r, c011.r, c111.r),
                mix(c000.g, c100.g, c010.g, c110.g, c001.g, c101.g, c011.g, c111.g),
                mix(c000.b, c100.b, c010.b, c110.b, c001.b, c101.b, c011.b, c111.b)
            )
        }

        private fun domainSpan(cube: ParsedCube, axis: Int): Float {
            val span = cube.domainMax[axis] - cube.domainMin[axis]
            return if (span > 0f) span else 1f
        }

        /** Lê uma grade salva como PNG 512×512. Null se não for 512×512. */
        fun fromBitmap(bitmap: Bitmap): LutGrid? {
            if (bitmap.width != SIZE || bitmap.height != SIZE) return null
            val pixels = IntArray(SIZE * SIZE)
            bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
            return LutGrid(pixels)
        }
    }
}
