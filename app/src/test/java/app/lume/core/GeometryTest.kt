package app.lume.core

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {

    @Test
    fun identity_hasNoEffect() {
        assertTrue(Geometry.IDENTITY.isIdentity())
        assertTrue(Geometry(crop = CropRect()).isIdentity())
        assertFalse(Geometry(rotationDegrees = 90).isIdentity())
        assertFalse(Geometry(flipHorizontal = true).isIdentity())
        assertFalse(Geometry(straightenDegrees = 1f).isIdentity())
        assertFalse(Geometry(crop = CropRect(0.1f, 0.1f, 0.9f, 0.9f)).isIdentity())
    }

    @Test
    fun clamped_normalizesRotationAndStraighten() {
        val geometry = Geometry(
            rotationDegrees = 450,
            straightenDegrees = 42f,
            crop = CropRect()
        ).clamped()
        assertEquals(90, geometry.rotationDegrees)
        assertEquals(10f, geometry.straightenDegrees, 0.001f)
        // Corte cheio vira null: não há o que cortar.
        assertEquals(null, geometry.crop)
        assertEquals(-10f, Geometry(straightenDegrees = -99f).clamped().straightenDegrees, 0.001f)
        assertEquals(270, Geometry(rotationDegrees = -90).clamped().rotationDegrees)
    }

    @Test
    fun rotatedSize_swapsOnQuarterTurns() {
        assertEquals(1000 to 2000, Geometry.IDENTITY.rotatedSize(1000, 2000))
        assertEquals(2000 to 1000, Geometry(rotationDegrees = 90).rotatedSize(1000, 2000))
        assertEquals(1000 to 2000, Geometry(rotationDegrees = 180).rotatedSize(1000, 2000))
        assertEquals(2000 to 1000, Geometry(rotationDegrees = 270).rotatedSize(1000, 2000))
    }

    @Test
    fun cropRect_clamped_ordersAndCoerces() {
        val rect = CropRect(0.9f, 1.4f, 0.1f, -0.2f).clamped()
        assertEquals(0.1f, rect.left, 0.001f)
        assertEquals(0f, rect.top, 0.001f)
        assertEquals(0.9f, rect.right, 0.001f)
        assertEquals(1f, rect.bottom, 0.001f)
    }

    @Test
    fun centeredRect_matchesAspectAndCenters() {
        // Foto retrato 1000x2000, corte 1:1: largura cheia da metade.
        val square = CropRatio.R1_1.centeredRect(1000, 2000)
        assertEquals(0f, square.left, 0.001f)
        assertEquals(1f, square.right, 0.001f)
        assertEquals(0.25f, square.top, 0.001f)
        assertEquals(0.75f, square.bottom, 0.001f)
        // Foto quadrada, corte 16:9: largura cheia, altura 9/16.
        val wide = CropRatio.R16_9.centeredRect(1000, 1000)
        assertEquals(0f, wide.left, 0.001f)
        assertEquals(1f, wide.right, 0.001f)
        assertEquals(0.21875f, wide.top, 0.001f)
        assertEquals(0.78125f, wide.bottom, 0.001f)
        // Livre = foto cheia.
        assertTrue(CropRatio.FREE.centeredRect(1000, 2000).isFull())
    }

    @Test
    fun matchingRatio_findsNearestPreset() {
        assertEquals(CropRatio.FREE, matchingRatio(null, 1000, 2000))
        assertEquals(CropRatio.FREE, matchingRatio(CropRect(), 1000, 2000))
        assertEquals(
            CropRatio.R1_1,
            matchingRatio(CropRatio.R1_1.centeredRect(1000, 2000), 1000, 2000)
        )
        assertEquals(
            CropRatio.R4_5,
            matchingRatio(CropRatio.R4_5.centeredRect(2000, 1000), 2000, 1000)
        )
        assertEquals(
            CropRatio.R9_16,
            matchingRatio(CropRatio.R9_16.centeredRect(1000, 2000), 1000, 2000)
        )
        // Retângulo arbitrário não casa com nenhuma proporção pronta.
        assertEquals(
            CropRatio.FREE,
            matchingRatio(CropRect(0.13f, 0.21f, 0.77f, 0.66f), 1000, 1000)
        )
    }

    @Test
    fun adjusted_edgeDragsClampAndKeepMinimum() {
        val rect = CropRect(0.2f, 0.2f, 0.8f, 0.8f)
        // Arrastar a esquerda para fora da foto: prende em 0.
        val left = rect.adjusted(CropEdge.LEFT, dx = -0.9f, dy = 0f, aspectLock = null, imageAspect = 1f)
        assertEquals(0f, left.left, 0.001f)
        assertEquals(0.8f, left.right, 0.001f)
        // Arrastar a direita para dentro além do mínimo: prende no mínimo.
        val right = rect.adjusted(CropEdge.RIGHT, dx = -0.9f, dy = 0f, aspectLock = null, imageAspect = 1f)
        assertEquals(0.28f, right.right, 0.001f)
        // Mover: o retângulo inteiro desliza, preso nos limites.
        val moved = rect.adjusted(CropEdge.MOVE, dx = 0.5f, dy = 0.5f, aspectLock = null, imageAspect = 1f)
        assertEquals(0.4f, moved.left, 0.001f)
        assertEquals(1f, moved.right, 0.001f)
        assertEquals(0.6f, moved.width, 0.001f)
    }

    @Test
    fun adjusted_aspectLockKeepsRatio() {
        val rect = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        val locked = rect.adjusted(CropEdge.RIGHT, dx = 0.1f, dy = 0f, aspectLock = 1f, imageAspect = 1f)
        assertEquals(1f, locked.width / locked.height, 0.001f)
        val tall = rect.adjusted(CropEdge.BOTTOM, dx = 0f, dy = 0.1f, aspectLock = 9f / 16f, imageAspect = 1f)
        assertEquals(9f / 16f, tall.width / tall.height, 0.001f)
    }

    @Test
    fun adjusted_aspectLockHoldsInPixelsOnPortraitPhoto() {
        // Foto retrato 1000x2000: um retângulo normalizado "quadrado"
        // é 500x1000 px. A trava 1:1 precisa valer em pixels.
        val rect = CropRect(0.25f, 0.25f, 0.75f, 0.75f)
        val locked = rect.adjusted(
            CropEdge.RIGHT, dx = 0.1f, dy = 0f, aspectLock = 1f, imageAspect = 0.5f
        )
        val pixelAspect = (locked.width * 1000f) / (locked.height * 2000f)
        assertEquals(1f, pixelAspect, 0.001f)
    }

    @Test
    fun inscribedRect_zeroAngleIsFull() {
        assertTrue(inscribedRectForRotation(1000, 2000, 0f).isFull())
        assertTrue(inscribedRectForRotation(1000, 2000, 90f).isFull())
    }

    @Test
    fun inscribedRect_cornersStayInsideRotatedPhoto() {
        // Propriedade: os 4 cantos do retângulo inscrito precisam caber
        // no retângulo girado. Verifica pela rotação inversa do canto.
        val cases = listOf(
            Triple(1000, 2000, 10f),
            Triple(2000, 1000, -7f),
            Triple(1000, 1000, 10f),
            Triple(800, 1200, 0.5f),
            Triple(4000, 3000, 3f)
        )
        cases.forEach { (w, h, degrees) ->
            val rect = inscribedRectForRotation(w, h, degrees)
            assertTrue(rect.width < 1f)
            // Mesmo aspecto da foto, em pixels.
            val pixelAspect = (rect.width * w) / (rect.height * h)
            assertEquals(w.toFloat() / h, pixelAspect, 0.001f)
            val theta = Math.toRadians(abs(degrees).toDouble())
            val c = cos(theta)
            val s = sin(theta)
            val corners = listOf(
                rect.left to rect.top,
                rect.right to rect.top,
                rect.left to rect.bottom,
                rect.right to rect.bottom
            )
            corners.forEach { (nx, ny) ->
                // Canto em px, relativo ao centro.
                val x = (nx * w - w / 2.0)
                val y = (ny * h - h / 2.0)
                val rx = abs(x * c + y * s)
                val ry = abs(-x * s + y * c)
                assertTrue("canto fora: rx=$rx ry=$ry", rx <= w / 2.0 + 1.0)
                assertTrue("canto fora: rx=$rx ry=$ry", ry <= h / 2.0 + 1.0)
            }
        }
    }

    @Test
    fun summary_describesGeometryInPortuguese() {
        assertEquals("", Geometry.IDENTITY.summary(1000, 2000))
        assertEquals(
            "Giro 90° · Corte 1:1",
            // O corte vive na foto JÁ girada: o retângulo 1:1 é o da
            // foto 2000x1000, e o resumo recebe essas dimensões.
            Geometry(rotationDegrees = 90, crop = CropRatio.R1_1.centeredRect(2000, 1000))
                .summary(2000, 1000)
        )
        assertEquals(
            "Espelhado · Endireitar +2,5°",
            Geometry(flipHorizontal = true, straightenDegrees = 2.5f).summary(1000, 1000)
        )
    }
}
