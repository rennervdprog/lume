package app.lume.imaging

import android.graphics.Bitmap
import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.util.OpenGlUtils

/**
 * O SEGUNDO filtro próprio do Lume: aplica um LUT (grade HALD 64,
 * ver [LutGrid]) como acabamento de cor, com intensidade 0..1.
 *
 * Por que próprio, em vez do GPUImageLookupFilter da biblioteca:
 * o `setBitmap` dele só sobe a textura UMA vez — o runnable interno
 * verifica `filterSourceTexture2 == NO_TEXTURE` e ignora qualquer
 * bitmap posterior (verificado no bytecode do AAR 2.1.0). Trocar de
 * LUT numa cadeia persistente não funcionaria. Aqui a textura é
 * gerenciada por este filtro: cada bitmap novo apaga a textura
 * antiga e sobe a nova, e a re-inicialização (após o grupo ser
 * destruído) reaplica a intensidade e o bitmap guardados.
 *
 * O shader é implementação própria da matemática padrão de lookup
 * 3D (a mesma ideia que todo app usa para LUT): o azul escolhe o
 * bloco da grade, o vermelho e o verde posicionam dentro do bloco,
 * e dois blocos vizinhos são misturados pela fração do azul. A
 * orientação exata da grade é provada pelos testes de pixel da GPU
 * (`GpuImagePipelineTest`, no source set de testes).
 *
 * Com intensidade 0 o shader devolve a textura sem amostrar o LUT —
 * a cadeia pode manter este filtro sempre ligado sem custo visual.
 */
internal class LutFilter : GPUImageFilter(
    GPUImageFilter.NO_FILTER_VERTEX_SHADER,
    FRAGMENT_SHADER
) {

    private var uIntensity = -1
    private var uLutTexture = -1

    private var intensity: Float = 0f
    private var pendingBitmap: Bitmap? = null
    private var lutTexture: Int = OpenGlUtils.NO_TEXTURE

    override fun onInit() {
        super.onInit()
        uIntensity = GLES20.glGetUniformLocation(program, "uIntensity")
        uLutTexture = GLES20.glGetUniformLocation(program, "inputImageTexture2")
    }

    override fun onInitialized() {
        super.onInitialized()
        // Re-inicialização (novo contexto após destruir o grupo):
        // reaplica o estado guardado — uniforms e textura morreram
        // com o programa antigo.
        applyIntensity()
        pendingBitmap?.let { uploadBitmap(it) }
    }

    /**
     * Define a grade a aplicar. Seguro de qualquer thread (fila
     * runOnDraw): o bitmap vira textura na thread GL, no próximo
     * desenho. O bitmap NÃO é reciclado aqui — quem guarda é o
     * chamador (o cache de grades). Null apaga a textura.
     */
    fun setLutBitmap(bitmap: Bitmap?) {
        pendingBitmap = bitmap
        if (bitmap == null) {
            runOnDraw {
                deleteTexture()
            }
        } else {
            uploadBitmap(bitmap)
        }
    }

    /** Intensidade 0..1: 0 = foto sem LUT, 1 = LUT total. */
    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        applyIntensity()
    }

    override fun onDrawArraysPre() {
        super.onDrawArraysPre()
        if (lutTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, lutTexture)
            GLES20.glUniform1i(uLutTexture, 3)
        }
    }

    override fun onDestroy() {
        deleteTexture()
        pendingBitmap = null
        super.onDestroy()
    }

    private fun applyIntensity() {
        setFloat(uIntensity, intensity)
    }

    private fun uploadBitmap(bitmap: Bitmap) {
        runOnDraw {
            deleteTexture()
            if (!bitmap.isRecycled) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
                lutTexture = OpenGlUtils.loadTexture(bitmap, OpenGlUtils.NO_TEXTURE, false)
            }
        }
    }

    private fun deleteTexture() {
        if (lutTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, intArrayOf(lutTexture), 0)
            lutTexture = OpenGlUtils.NO_TEXTURE
        }
    }

    private companion object {
        private const val FRAGMENT_SHADER: String = """
            precision highp float;
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform sampler2D inputImageTexture2;
            uniform lowp float uIntensity;

            void main() {
                vec4 tex = texture2D(inputImageTexture, textureCoordinate);
                if (uIntensity <= 0.001) {
                    gl_FragColor = tex;
                    return;
                }
                vec3 c = clamp(tex.rgb, 0.0, 1.0);
                float blueScaled = c.b * 63.0;
                float b0 = floor(blueScaled);
                float b1 = min(b0 + 1.0, 63.0);
                float bf = fract(blueScaled);
                vec2 quad0 = vec2(mod(b0, 8.0), floor(b0 / 8.0));
                vec2 quad1 = vec2(mod(b1, 8.0), floor(b1 / 8.0));
                vec2 pos0 = quad0 * 0.125 + vec2(0.5 / 512.0)
                    + (0.125 - 1.0 / 512.0) * c.rg;
                vec2 pos1 = quad1 * 0.125 + vec2(0.5 / 512.0)
                    + (0.125 - 1.0 / 512.0) * c.rg;
                vec3 lut0 = texture2D(inputImageTexture2, pos0).rgb;
                vec3 lut1 = texture2D(inputImageTexture2, pos1).rgb;
                vec3 lut = mix(lut0, lut1, bf);
                gl_FragColor = vec4(mix(tex.rgb, lut, uIntensity), tex.a);
            }
        """
    }
}
