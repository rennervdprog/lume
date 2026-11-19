package app.lume.imaging

import android.graphics.Bitmap
import android.opengl.GLES20
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter
import jp.co.cyberagent.android.gpuimage.util.OpenGlUtils

/**
 * Modos de mistura / dupla exposição (0.14.0). O id é o que a
 * [app.lume.core.Recipe] guarda (o campo `blendMode`); a segunda
 * foto vive só na sessão do editor e a opacidade é 0..1.
 */
enum class BlendMode(
    val id: String,
    val label: String
) {
    MULTIPLY("multiply", "Multiplicar"),
    SCREEN("screen", "Tela"),
    OVERLAY("overlay", "Sobrepor"),
    SOFT_LIGHT("softlight", "Luz suave"),
    HARD_LIGHT("hardlight", "Luz forte"),
    COLOR("color", "Cor"),
    LUMINOSITY("luminosity", "Luminosidade"),
    LIGHTEN("lighten", "Clarear");

    companion object {
        fun fromId(id: String?): BlendMode? =
            entries.firstOrNull { it.id == id }
    }
}

/**
 * O TERCEIRO filtro próprio do Lume: mistura a segunda foto sobre
 * a primeira, com 8 modos e opacidade 0..1. Mesmo molde do
 * [LutFilter] — a textura da segunda foto é gerenciada por este
 * filtro (apaga a antiga, sobe a nova), porque o `setBitmap` do
 * TwoInputFilter da biblioteca só sobe a textura uma vez.
 *
 * As fórmulas são as padrão de mistura (Photoshop/GIMP): multiplicar,
 * tela, sobrepor, luz suave/forte, cor, luminosidade e clarear. Com
 * opacidade 0 o shader devolve a foto base sem amostrar a segunda
 * textura — a cadeia pode manter este filtro sempre ligado sem
 * custo visual.
 */
internal class BlendFilter : GPUImageFilter(
    GPUImageFilter.NO_FILTER_VERTEX_SHADER,
    FRAGMENT_SHADER
) {

    private var uMode = -1
    private var uOpacity = -1
    private var uBlendTexture = -1

    private var mode: Int = 0
    private var opacity: Float = 0f
    private var pendingBitmap: Bitmap? = null
    private var blendTexture: Int = OpenGlUtils.NO_TEXTURE

    override fun onInit() {
        super.onInit()
        uMode = GLES20.glGetUniformLocation(program, "uMode")
        uOpacity = GLES20.glGetUniformLocation(program, "uOpacity")
        uBlendTexture = GLES20.glGetUniformLocation(program, "inputImageTexture2")
    }

    override fun onInitialized() {
        super.onInitialized()
        // Re-inicialização (novo contexto após destruir o grupo):
        // reaplica o estado guardado — uniforms e textura morreram
        // com o programa antigo.
        applyMode()
        applyOpacity()
        pendingBitmap?.let { uploadBitmap(it) }
    }

    /**
     * Define a segunda foto. Seguro de qualquer thread (fila
     * runOnDraw): o bitmap vira textura na thread GL, no próximo
     * desenho. O bitmap NÃO é reciclado aqui — quem guarda é o
     * chamador (o ViewModel). Null apaga a textura.
     */
    fun setBlendBitmap(bitmap: Bitmap?) {
        pendingBitmap = bitmap
        if (bitmap == null) {
            runOnDraw {
                deleteTexture()
            }
        } else {
            uploadBitmap(bitmap)
        }
    }

    /** Modo de mistura, como índice do [BlendMode] (0..7). */
    fun setMode(blendMode: BlendMode) {
        mode = blendMode.ordinal
        applyMode()
    }

    /** Opacidade 0..1: 0 = só a foto base, 1 = mistura total. */
    fun setOpacity(value: Float) {
        opacity = value.coerceIn(0f, 1f)
        applyOpacity()
    }

    override fun onDrawArraysPre() {
        super.onDrawArraysPre()
        if (blendTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blendTexture)
            GLES20.glUniform1i(uBlendTexture, 3)
        }
    }

    override fun onDestroy() {
        deleteTexture()
        pendingBitmap = null
        super.onDestroy()
    }

    private fun applyMode() {
        setInteger(uMode, mode)
    }

    private fun applyOpacity() {
        setFloat(uOpacity, opacity)
    }

    private fun uploadBitmap(bitmap: Bitmap) {
        runOnDraw {
            deleteTexture()
            if (!bitmap.isRecycled) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE3)
                blendTexture = OpenGlUtils.loadTexture(bitmap, OpenGlUtils.NO_TEXTURE, false)
            }
        }
    }

    private fun deleteTexture() {
        if (blendTexture != OpenGlUtils.NO_TEXTURE) {
            GLES20.glDeleteTextures(1, intArrayOf(blendTexture), 0)
            blendTexture = OpenGlUtils.NO_TEXTURE
        }
    }

    private companion object {
        private const val FRAGMENT_SHADER: String = """
            precision highp float;
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform sampler2D inputImageTexture2;
            uniform lowp int uMode;
            uniform lowp float uOpacity;

            float lum(vec3 c) { return dot(c, vec3(0.3, 0.59, 0.11)); }

            vec3 clipColor(vec3 c) {
                float l = lum(c);
                float mn = min(min(c.r, c.g), c.b);
                float mx = max(max(c.r, c.g), c.b);
                if (mn < 0.0) {
                    c = l + ((c - l) * l / (l - mn));
                }
                if (mx > 1.0) {
                    c = l + ((c - l) * (1.0 - l) / (mx - l));
                }
                return c;
            }

            vec3 setLum(vec3 c, float l) {
                return clipColor(c + (l - lum(c)));
            }

            float sat(vec3 c) {
                return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
            }

            vec3 setSat(vec3 c, float s) {
                float mn = min(min(c.r, c.g), c.b);
                float mx = max(max(c.r, c.g), c.b);
                if (mx > mn) {
                    return (c - mn) * (s / (mx - mn));
                }
                return vec3(0.0);
            }

            vec3 blend(vec3 b, vec3 l, int mode) {
                if (mode == 0) {
                    return b * l;
                } else if (mode == 1) {
                    return vec3(1.0) - (vec3(1.0) - b) * (vec3(1.0) - l);
                } else if (mode == 2) {
                    return mix(
                        2.0 * b * l,
                        vec3(1.0) - 2.0 * (vec3(1.0) - b) * (vec3(1.0) - l),
                        step(0.5, b)
                    );
                } else if (mode == 3) {
                    return (vec3(1.0) - 2.0 * l) * b * b + 2.0 * l * b;
                } else if (mode == 4) {
                    return mix(
                        2.0 * b * l,
                        vec3(1.0) - 2.0 * (vec3(1.0) - b) * (vec3(1.0) - l),
                        step(0.5, l)
                    );
                } else if (mode == 5) {
                    return setLum(setSat(l, sat(b)), lum(b));
                } else if (mode == 6) {
                    return setLum(b, lum(l));
                } else {
                    return max(b, l);
                }
            }

            void main() {
                vec4 tex = texture2D(inputImageTexture, textureCoordinate);
                if (uOpacity <= 0.001) {
                    gl_FragColor = tex;
                    return;
                }
                vec3 second = texture2D(inputImageTexture2, textureCoordinate).rgb;
                vec3 mixed = blend(clamp(tex.rgb, 0.0, 1.0), clamp(second, 0.0, 1.0), uMode);
                gl_FragColor = vec4(mix(tex.rgb, mixed, uOpacity), tex.a);
            }
        """
    }
}
