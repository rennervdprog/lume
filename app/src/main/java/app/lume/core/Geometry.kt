package app.lume.core

import kotlinx.serialization.Serializable
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Geometria da foto: giro, espelho, endireitar e corte. É um estado
 * separado da [Recipe] de propósito — preset nenhum pode carregar um
 * corte: aplicar um preset numa foto não pode cortar a foto.
 *
 * Ordem de aplicação (contrato com [app.lume.imaging.GeometryTransform]):
 * espelhos + giro + endireitar numa matriz só, depois o corte sobre o
 * resultado. O corte vive em coordenadas normalizadas (0..1) da foto
 * já girada — por isso a moldura da tela e o arquivo exportado
 * concordam.
 *
 * Puro (sem Android): testável em JVM. A persistência em rascunhos
 * chega depois — por enquanto a geometria vive só na sessão.
 */
@Serializable
data class Geometry(
    /** Giro em passos de 90° (0, 90, 180, 270). */
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    /** Endireitar, em graus (-10..10). */
    val straightenDegrees: Float = 0f,
    /** Corte normalizado, aplicado após giro/espelho. Null = foto cheia. */
    val crop: CropRect? = null
) {
    fun isIdentity(): Boolean =
        rotationDegrees % 360 == 0 && !flipHorizontal && !flipVertical &&
            straightenDegrees == 0f && (crop == null || crop.isFull())

    fun clamped(): Geometry = copy(
        rotationDegrees = ((rotationDegrees % 360) + 360) % 360,
        straightenDegrees = straightenDegrees.coerceIn(-MAX_STRAIGHTEN, MAX_STRAIGHTEN),
        crop = crop?.clamped()?.takeUnless { it.isFull() }
    )

    /** Geometria sem o corte — o modo de prévia do Cortar renderiza sem cortar. */
    fun withoutCrop(): Geometry = copy(crop = null)

    /**
     * Dimensões da foto após giro/espelho (antes do corte). Giro de 90°
     * ou 270° troca largura por altura.
     */
    fun rotatedSize(width: Int, height: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 == 0) width to height else height to width

    /**
     * Resumo em português para a pilha de edição ("Giro 90° · Corte 4:5").
     * Precisa das dimensões da foto: o aspecto do corte só existe em pixels,
     * e o retângulo é normalizado.
     */
    fun summary(imageWidth: Int, imageHeight: Int): String {
        val parts = buildList {
            val rotation = ((rotationDegrees % 360) + 360) % 360
            if (rotation != 0) add("Giro ${rotation}°")
            if (flipHorizontal) add("Espelhado")
            if (flipVertical) add("Invertido")
            if (straightenDegrees != 0f) add("Endireitar ${formatDegrees(straightenDegrees)}")
            if (crop != null && !crop.isFull()) {
                val ratio = matchingRatio(crop, imageWidth, imageHeight)
                add(if (ratio == CropRatio.FREE) "Corte livre" else "Corte ${ratio.label}")
            }
        }
        return parts.joinToString(" · ")
    }

    companion object {
        const val MAX_STRAIGHTEN: Float = 10f
        val IDENTITY = Geometry()
    }
}

/** Formata graus com sinal e vírgula decimal: "+2,5°", "-0,4°", "0,0°". */
internal fun formatDegrees(value: Float): String {
    val text = String.format(Locale.US, "%.1f", value).replace('.', ',')
    return if (value > 0f) "+$text°" else "$text°"
}

/** Retângulo de corte em coordenadas normalizadas (0..1) da foto. */
@Serializable
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun isFull(): Boolean = left <= 0f && top <= 0f && right >= 1f && bottom >= 1f

    /** Ordena os lados e prende em [0, 1]. Não impõe tamanho mínimo. */
    fun clamped(): CropRect {
        val l = left.coerceIn(0f, 1f)
        val t = top.coerceIn(0f, 1f)
        val r = right.coerceIn(0f, 1f)
        val b = bottom.coerceIn(0f, 1f)
        return CropRect(
            left = min(l, r),
            top = min(t, b),
            right = maxOf(l, r),
            bottom = maxOf(t, b)
        )
    }
}

/** Lado ou canto da moldura sendo arrastado, ou o interior (mover). */
enum class CropEdge {
    LEFT, TOP, RIGHT, BOTTOM,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    MOVE
}

/**
 * Ajusta o retângulo por um arrasto ([dx]/[dy] normalizados). Prende
 * nos limites da foto e impõe tamanho mínimo de 8% por eixo. Com
 * [aspectLock], o retângulo mantém o aspecto EM PIXELS: arrasto
 * horizontal usa a largura como guia, arrasto vertical usa a altura;
 * o centro fica onde estava, preso nos limites. [imageAspect] é a
 * razão largura/altura da foto — sem ela, um retângulo normalizado
 * "quadrado" numa foto retrato pareceria 1:1 sem ser.
 */
fun CropRect.adjusted(
    edge: CropEdge,
    dx: Float,
    dy: Float,
    aspectLock: Float?,
    imageAspect: Float
): CropRect {
    val minSize = 0.08f
    var l = left
    var t = top
    var r = right
    var b = bottom
    when (edge) {
        CropEdge.LEFT -> l = (l + dx).coerceIn(0f, r - minSize)
        CropEdge.RIGHT -> r = (r + dx).coerceIn(l + minSize, 1f)
        CropEdge.TOP -> t = (t + dy).coerceIn(0f, b - minSize)
        CropEdge.BOTTOM -> b = (b + dy).coerceIn(t + minSize, 1f)
        CropEdge.TOP_LEFT -> {
            l = (l + dx).coerceIn(0f, r - minSize)
            t = (t + dy).coerceIn(0f, b - minSize)
        }
        CropEdge.TOP_RIGHT -> {
            r = (r + dx).coerceIn(l + minSize, 1f)
            t = (t + dy).coerceIn(0f, b - minSize)
        }
        CropEdge.BOTTOM_LEFT -> {
            l = (l + dx).coerceIn(0f, r - minSize)
            b = (b + dy).coerceIn(t + minSize, 1f)
        }
        CropEdge.BOTTOM_RIGHT -> {
            r = (r + dx).coerceIn(l + minSize, 1f)
            b = (b + dy).coerceIn(t + minSize, 1f)
        }
        CropEdge.MOVE -> {
            val w = r - l
            val h = b - t
            l = (l + dx).coerceIn(0f, 1f - w)
            t = (t + dy).coerceIn(0f, 1f - h)
            r = l + w
            b = t + h
        }
    }
    var rect = CropRect(l, t, r, b)
    if (aspectLock != null && edge != CropEdge.MOVE) {
        val verticalDriver = edge == CropEdge.TOP || edge == CropEdge.BOTTOM
        rect = rect.withAspect(aspectLock, verticalDriver, imageAspect)
    }
    return rect.clamped()
}

/**
 * Reajusta o retângulo para o aspecto dado (em pixels), mantendo o
 * centro. O cálculo roda em "unidades de altura": a largura
 * normalizada vira largura × [imageAspect], para o aspecto valer em
 * pixels e não em coordenadas normalizadas.
 */
private fun CropRect.withAspect(
    aspect: Float,
    verticalDriver: Boolean,
    imageAspect: Float
): CropRect {
    // u = largura em unidades de altura; v = altura normalizada.
    var u: Float
    var v: Float
    if (verticalDriver) {
        v = height
        u = v * aspect
        if (u > imageAspect) {
            u = imageAspect
            v = u / aspect
        }
    } else {
        u = width * imageAspect
        v = u / aspect
        if (v > 1f) {
            v = 1f
            u = v * aspect
        }
    }
    val w = u / imageAspect
    val h = v
    val cx = (left + right) / 2f
    val cy = (top + bottom) / 2f
    val l = (cx - w / 2f).coerceIn(0f, 1f - w)
    val t = (cy - h / 2f).coerceIn(0f, 1f - h)
    return CropRect(l, t, l + w, t + h)
}

/** Proporções de corte prontas. Livre = sem trava de aspecto. */
enum class CropRatio(val label: String, val aspect: Float?) {
    FREE("Livre", null),
    R1_1("1:1", 1f),
    R4_5("4:5", 4f / 5f),
    R9_16("9:16", 9f / 16f),
    R3_2("3:2", 3f / 2f),
    R16_9("16:9", 16f / 9f);

    /** Retângulo centralizado de maior área com este aspecto sobre w×h. */
    fun centeredRect(width: Int, height: Int): CropRect {
        val target = aspect ?: return CropRect()
        val imageAspect = width.toFloat() / height.toFloat()
        return if (imageAspect > target) {
            // Foto mais larga que o aspecto: altura cheia, largura limitada.
            val w = height * target / width
            val margin = (1f - w) / 2f
            CropRect(margin, 0f, margin + w, 1f)
        } else {
            // Foto mais alta: largura cheia, altura limitada.
            val h = width / target / height
            val margin = (1f - h) / 2f
            CropRect(0f, margin, 1f, margin + h)
        }
    }

    /**
     * Verdadeiro quando [crop] tem (aproximadamente) este aspecto EM
     * PIXELS sobre uma foto de [imageWidth]×[imageHeight].
     */
    fun matches(crop: CropRect?, imageWidth: Int, imageHeight: Int): Boolean {
        if (aspect == null) return crop == null || crop.isFull()
        if (crop == null || crop.isFull()) return false
        if (crop.height <= 0f || imageHeight <= 0) return false
        val pixelAspect = (crop.width * imageWidth) / (crop.height * imageHeight)
        return abs(pixelAspect - aspect) < 0.02f
    }
}

/** A proporção pronta que descreve [crop], ou Livre quando nenhuma casa. */
fun matchingRatio(crop: CropRect?, imageWidth: Int, imageHeight: Int): CropRatio {
    if (crop == null || crop.isFull()) return CropRatio.FREE
    return CropRatio.entries.firstOrNull {
        it.aspect != null && it.matches(crop, imageWidth, imageHeight)
    } ?: CropRatio.FREE
}

/**
 * Maior retângulo alinhado aos eixos, centrado, com o MESMO aspecto da
 * foto, contido no retângulo w×h girado por [degrees]. O endireitar
 * usa este corte para não deixar cantos vazios: girar e depois cortar
 * as bordas, em vez de mostrar fundo.
 *
 * Cálculo fechado: os cantos de sinais opostos dão duas restrições
 * sobre o fator s — o canto (+,+) limita pela largura, o canto (+,-)
 * limita pela altura. Ângulos múltiplos de 90° não criam cantos
 * vazios: devolve a foto cheia.
 */
fun inscribedRectForRotation(width: Int, height: Int, degrees: Float): CropRect {
    val theta = Math.toRadians(abs(degrees).toDouble() % 180.0)
    if (theta < 1e-6 || abs(theta - Math.PI) < 1e-6) return CropRect()
    // Simetria de 90°: o primeiro quadrante basta.
    val t = theta % (Math.PI / 2.0)
    if (t < 1e-6) return CropRect()
    val c = cos(t)
    val s = sin(t)
    val w = width.toDouble()
    val h = height.toDouble()
    val s1 = w / (w * c + h * s)
    val s2 = h / (h * c + w * s)
    val scale = min(1.0, min(s1, s2)).toFloat()
    val margin = (1f - scale) / 2f
    return CropRect(margin, margin, 1f - margin, 1f - margin)
}
