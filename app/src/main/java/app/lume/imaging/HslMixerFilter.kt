package app.lume.imaging

import android.opengl.GLES20
import app.lume.core.HslChannel
import app.lume.core.HslChannels
import jp.co.cyberagent.android.gpuimage.filter.GPUImageFilter

/**
 * O ÚNICO filtro próprio do Lume: o mixer HSL de 8 canais. A GPUImage
 * não tem um filtro de HSL por faixa de cor (só matiz global), então
 * este filtro usa o mecanismo de extensão da própria biblioteca — o
 * contexto EGL, o renderer, as texturas e a cadeia de FBOs são todos
 * dela; aqui vive só a matemática HSL, já revisada.
 *
 * DECISÃO ANTI-BUG: os parâmetros vão em uniforms vec4 escalares
 * (dois vec4 por atributo: canais 0–3 e 4–7), NUNCA em arrays de
 * uniforms. O motor anterior usava `uniform float uHslHue[8]` e a
 * consulta do endereço pelo nome pelado volta -1 em algumas GPUs —
 * o HSL não fazia nada no aparelho do Renner, em silêncio. Sem
 * arrays, essa classe de bug não existe aqui. Os centros dos canais
 * são constantes e vão em dois vec4 fixos.
 *
 * Quando todos os canais estão na identidade, o shader devolve a
 * textura sem tocar (evita perda de precisão na volta RGB→HSL→RGB).
 */
internal class HslMixerFilter : GPUImageFilter(
    GPUImageFilter.NO_FILTER_VERTEX_SHADER,
    FRAGMENT_SHADER
) {

    private var uActive = -1
    private var uHueLo = -1
    private var uHueHi = -1
    private var uSatLo = -1
    private var uSatHi = -1
    private var uLumLo = -1
    private var uLumHi = -1
    private var uCentersLo = -1
    private var uCentersHi = -1

    private var channels: List<HslChannel> =
        List(HslChannels.COUNT) { HslChannel() }

    override fun onInit() {
        super.onInit()
        uActive = GLES20.glGetUniformLocation(program, "uActive")
        uHueLo = GLES20.glGetUniformLocation(program, "uHueLo")
        uHueHi = GLES20.glGetUniformLocation(program, "uHueHi")
        uSatLo = GLES20.glGetUniformLocation(program, "uSatLo")
        uSatHi = GLES20.glGetUniformLocation(program, "uSatHi")
        uLumLo = GLES20.glGetUniformLocation(program, "uLumLo")
        uLumHi = GLES20.glGetUniformLocation(program, "uLumHi")
        uCentersLo = GLES20.glGetUniformLocation(program, "uCentersLo")
        uCentersHi = GLES20.glGetUniformLocation(program, "uCentersHi")
    }

    override fun onInitialized() {
        super.onInitialized()
        applyCenters()
        applyChannels()
    }

    /** Atualiza os 8 canais. Seguro de qualquer thread (fila runOnDraw). */
    fun setHsl(channels: List<HslChannel>) {
        this.channels = HslChannels.normalize(channels)
        applyChannels()
    }

    private fun applyChannels() {
        val c = channels
        val active = c.any { !it.isIdentity() }
        setFloat(uActive, if (active) 1f else 0f)
        setFloatVec4(uHueLo, floatArrayOf(
            hueDegrees(c[0]), hueDegrees(c[1]), hueDegrees(c[2]), hueDegrees(c[3])
        ))
        setFloatVec4(uHueHi, floatArrayOf(
            hueDegrees(c[4]), hueDegrees(c[5]), hueDegrees(c[6]), hueDegrees(c[7])
        ))
        setFloatVec4(uSatLo, floatArrayOf(
            c[0].saturation / 100f, c[1].saturation / 100f,
            c[2].saturation / 100f, c[3].saturation / 100f
        ))
        setFloatVec4(uSatHi, floatArrayOf(
            c[4].saturation / 100f, c[5].saturation / 100f,
            c[6].saturation / 100f, c[7].saturation / 100f
        ))
        setFloatVec4(uLumLo, floatArrayOf(
            c[0].luminance / 100f, c[1].luminance / 100f,
            c[2].luminance / 100f, c[3].luminance / 100f
        ))
        setFloatVec4(uLumHi, floatArrayOf(
            c[4].luminance / 100f, c[5].luminance / 100f,
            c[6].luminance / 100f, c[7].luminance / 100f
        ))
    }

    private fun applyCenters() {
        val centers = HslChannels.CENTERS_DEGREES
        setFloatVec4(uCentersLo, floatArrayOf(centers[0], centers[1], centers[2], centers[3]))
        setFloatVec4(uCentersHi, floatArrayOf(centers[4], centers[5], centers[6], centers[7]))
    }

    private fun hueDegrees(channel: HslChannel): Float =
        channel.hue / 100f * HslChannels.MAX_HUE_SHIFT_DEGREES

    private companion object {
        /**
         * Matemática HSL padrão (conversão RGB↔HSL, peso triangular
         * suavizado de 45° por canal, normalizado pelo peso total).
         * Mesmas fórmulas do motor anterior, revisadas — mudou só o
         * transporte dos parâmetros (vec4, sem arrays).
         */
        private const val FRAGMENT_SHADER: String = """
            precision highp float;
            varying highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            uniform float uActive;
            uniform vec4 uHueLo;
            uniform vec4 uHueHi;
            uniform vec4 uSatLo;
            uniform vec4 uSatHi;
            uniform vec4 uLumLo;
            uniform vec4 uLumHi;
            uniform vec4 uCentersLo;
            uniform vec4 uCentersHi;

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

            vec4 hueWeights(float h, vec4 centers) {
                vec4 d = abs(mod(h - centers + 180.0, 360.0) - 180.0);
                vec4 w = clamp(1.0 - d / 45.0, 0.0, 1.0);
                return w * w * (3.0 - 2.0 * w);
            }

            void main() {
                vec4 tex = texture2D(inputImageTexture, textureCoordinate);
                if (uActive > 0.5) {
                    vec3 hsl = rgb2hsl(clamp(tex.rgb, 0.0, 1.0));
                    vec4 wLo = hueWeights(hsl.x, uCentersLo);
                    vec4 wHi = hueWeights(hsl.x, uCentersHi);
                    float hueShift = dot(wLo, uHueLo) + dot(wHi, uHueHi);
                    float satDelta = dot(wLo, uSatLo) + dot(wHi, uSatHi);
                    float lumDelta = dot(wLo, uLumLo) + dot(wHi, uLumHi);
                    float totalW = dot(wLo, vec4(1.0)) + dot(wHi, vec4(1.0));
                    if (totalW > 0.001) {
                        hsl.x = mod(hsl.x + hueShift / totalW + 360.0, 360.0);
                        hsl.y = clamp(hsl.y * (1.0 + satDelta / totalW), 0.0, 1.0);
                        hsl.z = clamp(hsl.z + lumDelta / totalW * 0.3, 0.0, 1.0);
                        tex.rgb = hsl2rgb(hsl);
                    }
                }
                gl_FragColor = tex;
            }
        """
    }
}
