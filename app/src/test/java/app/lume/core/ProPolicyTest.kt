package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProPolicyTest {

    @Test
    fun neverLockedFeatures_areUnlockedWithoutPro() {
        for (feature in ProPolicy.neverLocked) {
            assertTrue(
                "$feature nunca pode ser trancado.",
                ProPolicy.isUnlocked(feature, isPro = false)
            )
        }
    }

    @Test
    fun exportFull_isUnlockedWithoutPro() {
        assertTrue(ProPolicy.isUnlocked(Feature.EXPORT_FULL, isPro = false))
    }

    @Test
    fun generativeAi_isLockedEvenWithPro() {
        // Créditos, Fase 4+. Nunca "Pro ilimitado".
        assertFalse(ProPolicy.isUnlocked(Feature.GENERATIVE_AI, isPro = true))
    }

    @Test
    fun hslFull_requiresPro() {
        assertFalse(ProPolicy.isUnlocked(Feature.HSL_FULL, isPro = false))
        assertTrue(ProPolicy.isUnlocked(Feature.HSL_FULL, isPro = true))
    }

    @Test
    fun hslBasic_isFree() {
        assertTrue(ProPolicy.isUnlocked(Feature.HSL_BASIC, isPro = false))
    }

    @Test
    fun ownPresetLimit_isTenFreeUnlimitedPro() {
        assertEquals(10, ProPolicy.ownPresetLimit(isPro = false))
        assertEquals(Int.MAX_VALUE, ProPolicy.ownPresetLimit(isPro = true))
    }

    @Test
    fun batchLimit_isFiveFreeUnlimitedPro() {
        assertEquals(5, ProPolicy.batchLimit(isPro = false))
        assertEquals(Int.MAX_VALUE, ProPolicy.batchLimit(isPro = true))
    }

    @Test
    fun paywallRows_coverExportAndPresets() {
        val labels = ProPolicy.paywallRows.map { it.label }
        assertTrue(labels.contains("Exportação"))
        assertTrue(labels.contains("Seus presets"))
    }
}
