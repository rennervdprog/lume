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
    fun hslChannels_firstFourFreeRestRequirePro() {
        for (index in 0 until ProPolicy.FREE_HSL_CHANNELS) {
            assertTrue(
                "canal $index é grátis",
                ProPolicy.hslChannelUnlocked(index, isPro = false)
            )
        }
        for (index in ProPolicy.FREE_HSL_CHANNELS until HslChannels.COUNT) {
            assertFalse(
                "canal $index exige Pro",
                ProPolicy.hslChannelUnlocked(index, isPro = false)
            )
            assertTrue(
                "canal $index libera com Pro",
                ProPolicy.hslChannelUnlocked(index, isPro = true)
            )
        }
    }

    @Test
    fun hslChannels_outOfRangeIsLocked() {
        assertFalse(ProPolicy.hslChannelUnlocked(-1, isPro = true))
        assertFalse(ProPolicy.hslChannelUnlocked(HslChannels.COUNT, isPro = true))
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

    @Test
    fun ajustarPro_blurSkin_artistic_blend_requirePro() {
        for (feature in listOf(
            Feature.ADJUST_PRO,
            Feature.BLUR_SKIN,
            Feature.ARTISTIC_EFFECTS,
            Feature.BLEND_MODES
        )) {
            assertFalse("$feature é grátis", ProPolicy.isUnlocked(feature, isPro = false))
            assertTrue("$feature libera com Pro", ProPolicy.isUnlocked(feature, isPro = true))
        }
    }

    @Test
    fun curvasBasicas_rgbIsFree() {
        // A tabela aprovada (19/11) diz "Master + RGB" no Grátis:
        // o RGB é CURVES_BASIC, não Pro. Só "Curvas salvas" é Pro.
        assertTrue(ProPolicy.isUnlocked(Feature.CURVES_BASIC, isPro = false))
        assertFalse(ProPolicy.isUnlocked(Feature.CURVES_LIBRARY, isPro = false))
        assertTrue(ProPolicy.isUnlocked(Feature.CURVES_LIBRARY, isPro = true))
    }

    @Test
    fun paywallRows_coverNovosRecursos014() {
        val labels = ProPolicy.paywallRows.map { it.label }
        assertTrue(labels.contains("Ajustar Pro"))
        assertTrue(labels.contains("Desfoque e pele"))
        assertTrue(labels.contains("Efeitos artísticos"))
        assertTrue(labels.contains("Mistura"))
    }
}
