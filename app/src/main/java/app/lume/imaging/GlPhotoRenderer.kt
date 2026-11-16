package app.lume.imaging

import android.graphics.Bitmap
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import app.lume.core.HslChannels
import app.lume.core.Recipe
import app.lume.core.ToneCurveLut
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Renderizador OpenGL ES 2.0 offscreen (pbuffer) — o motor da Fase 1.
 *
 * Um único shader aplica a receita inteira num passe só, na ordem fixa
 * documentada no fragment shader: claridade → exposição → balanço de
 * branco → faixas tonais → contraste → vibração/saturação → HSL →
 * curvas → desbotado → vinheta → grão. A ordem é contrato: presets só
 * são portáveis se a ordem for estável.
 *
 * CONFINAMENTO DE THREAD: um contexto EGL pertence à thread que o
 * criou. TODOS os métodos desta classe devem ser chamados sempre da
 * mesma thread — quem garante isso é [GlEngine]. Chamar de outra thread
 * falha no EGL e vira exceção (o chamador cai para o fallback da matriz).
 *
 * Duas armadilhas clássicas, resolvidas aqui e documentadas para não
 * serem "corrigidas" por engano:
 * 1. ESPELHO VERTICAL: a textura sobe com a linha de cima do bitmap em
 *    v=1, e o glReadPixels lê o framebuffer de baixo para cima. Os UVs
 *    do quad já vêm com o V invertido (v=1 nos vértices de baixo), de
 *    modo que o bitmap de saída sai na orientação certa. Não "consertar"
 *    os UVs sem testar no aparelho.
 * 2. TROCA R/B: o readback cai num Bitmap ARGB_8888, cujos bytes em
 *    memória (little-endian) são B,G,R,A — mas o glReadPixels entrega
 *    R,G,B,A. O shader emite BGRA na última linha, e os bytes caem
 *    certos sem varrer o bitmap na CPU.
 *
 * Limites honestos: fotos maiores que o limite de textura da GPU
 * ([GL_MAX_TEXTURE_SIZE]) estouram em [setSource] — o chamador cai para
 * o caminho da matriz (CPU, sem limite de tamanho). Tiles para texturas
 * gigantes ficam para uma fase futura.
 */
internal class GlPhotoRenderer {

    private var display: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var surface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var config: EGLConfig? = null
    private var program: Int = 0
    private var textureId: Int = 0
    private var curveTextureId: Int = 0
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    private var sourceWidth: Int = 0
    private var sourceHeight: Int = 0
    private var maxTextureSize: Int = 0

    private var positionBuffer: FloatBuffer? = null
    private var uvBuffer: FloatBuffer? = null

    // Localizações de uniforms (consultadas uma vez, após o link).
    private var uTexture = -1
    private var uCurveTex = -1
    private var uTexelSize = -1
    private var uAspect = -1
    private var uExposure = -1
    private var uContrast = -1
    private var uHighlights = -1
    private var uShadows = -1
    private var uWhites = -1
    private var uBlacks = -1
    private var uTemperature = -1
    private var uTint = -1
    private var uVibrance = -1
    private var uSaturation = -1
    private var uHslHue = -1
    private var uHslSat = -1
    private var uHslLum = -1
    private var uHslCenters = -1
    private var uHslActive = -1
    private var uCurveActive = -1
    private var uClarity = -1
    private var uVignette = -1
    private var uGrain = -1
    private var uFade = -1

    fun init() {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display === EGL14.EGL_NO_DISPLAY) {
            throw IllegalStateException("Sem display EGL.")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw IllegalStateException("eglInitialize falhou.")
        }
        val configAttrs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, numConfigs, 0) ||
            numConfigs[0] <= 0
        ) {
            throw IllegalStateException("Nenhuma config EGL RGBA8888/pbuffer.")
        }
        config = configs[0]
        val contextAttrs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttrs, 0)
        if (context === EGL14.EGL_NO_CONTEXT) {
            throw IllegalStateException("eglCreateContext falhou.")
        }
        program = linkProgram()
        queryUniforms()
        positionBuffer = floatBufferOf(QUAD_POSITIONS)
        uvBuffer = floatBufferOf(QUAD_UVS_FLIPPED_V)
    }

    /**
     * Sobe [bitmap] como textura. Recria o pbuffer se o tamanho mudou.
     * A textura é uma cópia na GPU: reciclar o bitmap depois é seguro.
     */
    fun setSource(bitmap: Bitmap) {
        require(bitmap.width > 0 && bitmap.height > 0) { "Bitmap vazio." }
        ensureSurface(bitmap.width, bitmap.height)
        if (maxTextureSize > 0 &&
            (bitmap.width > maxTextureSize || bitmap.height > maxTextureSize)
        ) {
            throw IllegalStateException(
                "Foto maior que o limite de textura da GPU ($maxTextureSize)."
            )
        }
        if (textureId == 0) {
            textureId = genTexture()
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        // Filtragem LINEAR explícita: o default (mipmap) deixaria a
        // textura incompleta sem mipmaps — e a tela sairia preta.
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        sourceWidth = bitmap.width
        sourceHeight = bitmap.height
    }

    /** Renderiza [recipe] sobre a foto de [setSource] e devolve um bitmap novo. */
    fun render(recipe: Recipe): Bitmap {
        check(textureId != 0) { "setSource não chamado." }
        val r = recipe.clamped()
        GLES20.glViewport(0, 0, sourceWidth, sourceHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        setInt(uTexture, 0)

        uploadCurveTexture(r)
        setInt(uCurveTex, 1)

        setVec2(uTexelSize, 1f / sourceWidth, 1f / sourceHeight)
        setFloat(uAspect, sourceWidth.toFloat() / sourceHeight)
        setFloat(uExposure, r.exposure)
        setFloat(uContrast, r.contrast)
        setFloat(uHighlights, r.highlights)
        setFloat(uShadows, r.shadows)
        setFloat(uWhites, r.whites)
        setFloat(uBlacks, r.blacks)
        setFloat(uTemperature, r.temperature)
        setFloat(uTint, r.tint)
        setFloat(uVibrance, r.vibrance)
        setFloat(uSaturation, r.saturation)
        setFloat(uClarity, r.clarity)
        setFloat(uVignette, r.vignette)
        setFloat(uGrain, r.grain)
        setFloat(uFade, r.fade)
        setFloat(uHslActive, if (r.hsl.all { it.isIdentity() }) 0f else 1f)
        setFloat(uCurveActive, if (r.curve.isEffectiveIdentity()) 0f else 1f)
        setFloatArray(uHslHue, FloatArray(HslChannels.COUNT) { i ->
            r.hsl[i].hue / 100f * HslChannels.MAX_HUE_SHIFT_DEGREES
        })
        setFloatArray(uHslSat, FloatArray(HslChannels.COUNT) { i ->
            r.hsl[i].saturation / 100f
        })
        setFloatArray(uHslLum, FloatArray(HslChannels.COUNT) { i ->
            r.hsl[i].luminance / 100f
        })
        setFloatArray(uHslCenters, HslChannels.CENTERS_DEGREES)

        val positions = checkNotNull(positionBuffer) { "Renderer sem init." }
        val uvs = checkNotNull(uvBuffer) { "Renderer sem init." }
        GLES20.glEnableVertexAttribArray(ATTRIB_POSITION)
        GLES20.glVertexAttribPointer(
            ATTRIB_POSITION, 2, GLES20.GL_FLOAT, false, 0, positions
        )
        GLES20.glEnableVertexAttribArray(ATTRIB_UV)
        GLES20.glVertexAttribPointer(ATTRIB_UV, 2, GLES20.GL_FLOAT, false, 0, uvs)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)
        GLES20.glDisableVertexAttribArray(ATTRIB_POSITION)
        GLES20.glDisableVertexAttribArray(ATTRIB_UV)

        val buffer = ByteBuffer.allocateDirect(sourceWidth * sourceHeight * 4)
            .order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(
            0, 0, sourceWidth, sourceHeight,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
        )
        buffer.position(0)
        val out = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
        out.copyPixelsFromBuffer(buffer)
        return out
    }

    /** Libera tudo. Nunca estoura — falhar ao liberar não pode quebrar a edição. */
    fun release() {
        try {
            if (textureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
                textureId = 0
            }
            if (curveTextureId != 0) {
                GLES20.glDeleteTextures(1, intArrayOf(curveTextureId), 0)
                curveTextureId = 0
            }
            if (program != 0) {
                GLES20.glDeleteProgram(program)
                program = 0
            }
            if (display !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(
                    display,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT
                )
                if (surface !== EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(display, surface)
                    surface = EGL14.EGL_NO_SURFACE
                }
                if (context !== EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(display, context)
                    context = EGL14.EGL_NO_CONTEXT
                }
                EGL14.eglTerminate(display)
                display = EGL14.EGL_NO_DISPLAY
            }
        } catch (ignored: Exception) {
            // Melhor esforço: recursos EGL órfãos morrem com o processo.
        }
        surfaceWidth = 0
        surfaceHeight = 0
        sourceWidth = 0
        sourceHeight = 0
        maxTextureSize = 0
    }

    private fun ensureSurface(width: Int, height: Int) {
        if (surface !== EGL14.EGL_NO_SURFACE &&
            surfaceWidth == width && surfaceHeight == height
        ) {
            makeCurrent()
            return
        }
        if (surface !== EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, surface)
            surface = EGL14.EGL_NO_SURFACE
        }
        val surfaceAttrs = intArrayOf(
            EGL14.EGL_WIDTH, width,
            EGL14.EGL_HEIGHT, height,
            EGL14.EGL_NONE
        )
        surface = EGL14.eglCreatePbufferSurface(display, config, surfaceAttrs, 0)
        if (surface === EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("eglCreatePbufferSurface falhou (${width}x$height).")
        }
        surfaceWidth = width
        surfaceHeight = height
        makeCurrent()
        if (maxTextureSize == 0) {
            val value = IntArray(1)
            GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, value, 0)
            maxTextureSize = value[0]
        }
    }

    private fun makeCurrent() {
        if (!EGL14.eglMakeCurrent(display, surface, surface, context)) {
            throw IllegalStateException("eglMakeCurrent falhou.")
        }
    }

    private fun uploadCurveTexture(recipe: Recipe) {
        if (curveTextureId == 0) {
            curveTextureId = genTexture()
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, curveTextureId)
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES20.GL_TEXTURE_2D,
            GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        val rgba = ToneCurveLut.buildRgba(recipe.curve)
        val buffer = ByteBuffer.allocateDirect(rgba.size)
            .order(ByteOrder.nativeOrder())
            .apply {
                put(rgba)
                position(0)
            }
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            ToneCurveLut.SIZE, 1, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer
        )
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    }

    private fun genTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        if (ids[0] == 0) throw IllegalStateException("glGenTextures falhou.")
        return ids[0]
    }

    private fun linkProgram(): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        val linked = GLES20.glCreateProgram()
        if (linked == 0) throw IllegalStateException("glCreateProgram falhou.")
        GLES20.glAttachShader(linked, vertex)
        GLES20.glAttachShader(linked, fragment)
        // Posições de atributo fixas (antes do link): sem consulta por nome.
        GLES20.glBindAttribLocation(linked, ATTRIB_POSITION, "aPosition")
        GLES20.glBindAttribLocation(linked, ATTRIB_UV, "aUv")
        GLES20.glLinkProgram(linked)
        val status = IntArray(1)
        GLES20.glGetProgramiv(linked, GLES20.GL_LINK_STATUS, status, 0)
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(linked)
            GLES20.glDeleteProgram(linked)
            throw IllegalStateException("Link do shader falhou: $log")
        }
        return linked
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) throw IllegalStateException("glCreateShader falhou.")
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Shader não compilou: $log")
        }
        return shader
    }

    private fun queryUniforms() {
        fun loc(name: String): Int = GLES20.glGetUniformLocation(program, name)
        uTexture = loc("uTexture")
        uCurveTex = loc("uCurveTex")
        uTexelSize = loc("uTexelSize")
        uAspect = loc("uAspect")
        uExposure = loc("uExposure")
        uContrast = loc("uContrast")
        uHighlights = loc("uHighlights")
        uShadows = loc("uShadows")
        uWhites = loc("uWhites")
        uBlacks = loc("uBlacks")
        uTemperature = loc("uTemperature")
        uTint = loc("uTint")
        uVibrance = loc("uVibrance")
        uSaturation = loc("uSaturation")
        uHslHue = loc("uHslHue")
        uHslSat = loc("uHslSat")
        uHslLum = loc("uHslLum")
        uHslCenters = loc("uHslCenters")
        uHslActive = loc("uHslActive")
        uCurveActive = loc("uCurveActive")
        uClarity = loc("uClarity")
        uVignette = loc("uVignette")
        uGrain = loc("uGrain")
        uFade = loc("uFade")
    }

    private fun setFloat(location: Int, value: Float) {
        if (location >= 0) GLES20.glUniform1f(location, value)
    }

    private fun setInt(location: Int, value: Int) {
        if (location >= 0) GLES20.glUniform1i(location, value)
    }

    private fun setVec2(location: Int, x: Float, y: Float) {
        if (location >= 0) GLES20.glUniform2f(location, x, y)
    }

    private fun setFloatArray(location: Int, values: FloatArray) {
        if (location >= 0) {
            GLES20.glUniform1fv(location, values.size, floatBufferOf(values))
        }
    }

    private fun floatBufferOf(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    companion object {
        private const val ATTRIB_POSITION: Int = 0
        private const val ATTRIB_UV: Int = 1

        private val QUAD_POSITIONS: FloatArray = floatArrayOf(
            -1f, -1f, 1f, -1f, -1f, 1f,
            1f, -1f, 1f, 1f, -1f, 1f
        )

        /**
         * UVs com o V invertido (ver KDoc da classe, armadilha 1):
         * vértices de baixo amostram v=1 (topo do bitmap).
         */
        private val QUAD_UVS_FLIPPED_V: FloatArray = floatArrayOf(
            0f, 1f, 1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f, 0f, 0f
        )

        private const val VERTEX_SHADER: String = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        /**
         * Um passe só, ordem fixa (contrato de portabilidade de presets).
         * Fórmulas padrão de processamento de cor, escritas para este app:
         * conversão RGB↔HSL, pesos Rec. 709, curva por LUT. A claridade usa
         * a média 3x3 da fonte (aproximação honesta: borrar os vizinhos já
         * processados exigiria 9 avaliações do pipeline por pixel).
         */
        private const val FRAGMENT_SHADER: String = """
            precision mediump float;
            varying vec2 vUv;
            uniform sampler2D uTexture;
            uniform sampler2D uCurveTex;
            uniform vec2 uTexelSize;
            uniform float uAspect;
            uniform float uExposure;
            uniform float uContrast;
            uniform float uHighlights;
            uniform float uShadows;
            uniform float uWhites;
            uniform float uBlacks;
            uniform float uTemperature;
            uniform float uTint;
            uniform float uVibrance;
            uniform float uSaturation;
            uniform float uHslHue[8];
            uniform float uHslSat[8];
            uniform float uHslLum[8];
            uniform float uHslCenters[8];
            uniform float uHslActive;
            uniform float uCurveActive;
            uniform float uClarity;
            uniform float uVignette;
            uniform float uGrain;
            uniform float uFade;

            const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

            vec3 rgb2hsl(vec3 c) {
                float maxc = max(c.r, max(c.g, c.b));
                float minc = min(c.r, min(c.g, c.b));
                float l = (maxc + minc) * 0.5;
                float d = maxc - minc;
                if (d < 0.001) return vec3(0.0, 0.0, l);
                float s = d / (1.0 - abs(2.0 * l - 1.0) + 0.001);
                float h;
                if (maxc == c.r) {
                    h = mod((c.g - c.b) / d, 6.0);
                } else if (maxc == c.g) {
                    h = (c.b - c.r) / d + 2.0;
                } else {
                    h = (c.r - c.g) / d + 4.0;
                }
                h *= 60.0;
                if (h < 0.0) h += 360.0;
                return vec3(h, clamp(s, 0.0, 1.0), l);
            }

            vec3 hsl2rgb(vec3 c) {
                float h = c.x / 60.0;
                float s = c.y;
                float l = c.z;
                float chroma = (1.0 - abs(2.0 * l - 1.0)) * s;
                float x = chroma * (1.0 - abs(mod(h, 2.0) - 1.0));
                float m = l - chroma * 0.5;
                vec3 rgb;
                if (h < 1.0) rgb = vec3(chroma, x, 0.0);
                else if (h < 2.0) rgb = vec3(x, chroma, 0.0);
                else if (h < 3.0) rgb = vec3(0.0, chroma, x);
                else if (h < 4.0) rgb = vec3(0.0, x, chroma);
                else if (h < 5.0) rgb = vec3(x, 0.0, chroma);
                else rgb = vec3(chroma, 0.0, x);
                return rgb + m;
            }

            float hueWeight(float hue, float center) {
                float d = abs(mod(hue - center + 180.0, 360.0) - 180.0);
                float w = clamp(1.0 - d / 45.0, 0.0, 1.0);
                return w * w * (3.0 - 2.0 * w);
            }

            vec3 applyBand(vec3 c, float w, float a) {
                if (a >= 0.0) return c + (1.0 - c) * a * w;
                return c * (1.0 + a * w);
            }

            float hashNoise(vec2 p) {
                return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
            }

            void main() {
                vec3 color = texture2D(uTexture, vUv).rgb;

                if (uClarity != 0.0) {
                    vec3 blur = vec3(0.0);
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            blur += texture2D(
                                uTexture,
                                vUv + vec2(float(dx), float(dy)) * uTexelSize
                            ).rgb;
                        }
                    }
                    blur /= 9.0;
                    color += (color - blur) * (uClarity / 100.0) * 0.6;
                }

                color *= pow(2.0, uExposure);

                float temp = uTemperature / 100.0;
                float tint = uTint / 100.0;
                color *= vec3(
                    (1.0 + temp * 0.28) * (1.0 - tint * 0.10),
                    (1.0 + temp * 0.04) * (1.0 + tint * 0.20),
                    (1.0 - temp * 0.28) * (1.0 - tint * 0.10)
                );

                float lum = dot(color, LUMA);
                color = applyBand(color, smoothstep(0.55, 0.95, lum), uHighlights / 100.0);
                color = applyBand(color, 1.0 - smoothstep(0.05, 0.45, lum), uShadows / 100.0);
                color = applyBand(color, smoothstep(0.75, 1.0, lum), uWhites / 100.0);
                color = applyBand(color, 1.0 - smoothstep(0.0, 0.25, lum), uBlacks / 100.0);

                color = (color - 0.5) * (1.0 + (uContrast / 100.0) * 0.9) + 0.5;

                float luma = dot(color, LUMA);
                float maxc = max(color.r, max(color.g, color.b));
                float minc = min(color.r, min(color.g, color.b));
                float satPix = maxc > 0.001 ? (maxc - minc) / maxc : 0.0;
                float satAmt = 1.0 + uSaturation / 100.0
                    + (uVibrance / 100.0) * (1.0 - satPix) * 0.8;
                color = mix(vec3(luma), color, satAmt);

                if (uHslActive > 0.5) {
                    vec3 hsl = rgb2hsl(clamp(color, 0.0, 1.0));
                    float hueShift = 0.0;
                    float satDelta = 0.0;
                    float lumDelta = 0.0;
                    float totalW = 0.0;
                    for (int i = 0; i < 8; i++) {
                        float w = hueWeight(hsl.x, uHslCenters[i]);
                        hueShift += w * uHslHue[i];
                        satDelta += w * uHslSat[i];
                        lumDelta += w * uHslLum[i];
                        totalW += w;
                    }
                    if (totalW > 0.001) {
                        hsl.x = mod(hsl.x + hueShift / totalW + 360.0, 360.0);
                        hsl.y = clamp(hsl.y * (1.0 + satDelta / totalW), 0.0, 1.0);
                        hsl.z = clamp(hsl.z + lumDelta / totalW * 0.3, 0.0, 1.0);
                        color = hsl2rgb(hsl);
                    }
                }

                if (uCurveActive > 0.5) {
                    color = clamp(color, 0.0, 1.0);
                    float cr = texture2D(uCurveTex, vec2(color.r, 0.5)).r;
                    float cg = texture2D(uCurveTex, vec2(color.g, 0.5)).g;
                    float cb = texture2D(uCurveTex, vec2(color.b, 0.5)).b;
                    color = vec3(cr, cg, cb);
                    float luma2 = dot(color, LUMA);
                    float lumaCurved = texture2D(uCurveTex, vec2(luma2, 0.5)).a;
                    if (luma2 > 0.001) {
                        color *= lumaCurved / luma2;
                    }
                }

                color = mix(color, vec3(0.5), (uFade / 100.0) * 0.5);

                if (uVignette != 0.0) {
                    vec2 p = vUv - 0.5;
                    p.x *= uAspect;
                    float d = length(p) * 2.0;
                    color *= 1.0 - smoothstep(0.55, 1.25, d) * (uVignette / 100.0) * 0.75;
                }

                if (uGrain != 0.0) {
                    float n = hashNoise(gl_FragCoord.xy) - 0.5;
                    float luma3 = dot(color, LUMA);
                    color += n * (uGrain / 100.0) * 0.12 * (1.0 - luma3 * 0.6);
                }

                color = clamp(color, 0.0, 1.0);
                gl_FragColor = vec4(color.b, color.g, color.r, 1.0);
            }
        """
    }
}
