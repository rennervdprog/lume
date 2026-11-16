package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltInPresetsTest {

    @Test
    fun has36Presets() {
        assertEquals(36, BuiltInPresets.all.size)
    }

    @Test
    fun idsAndNamesAreUnique() {
        val ids = BuiltInPresets.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        val names = BuiltInPresets.all.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun allAreBuiltInWithCollectionAndStableId() {
        BuiltInPresets.all.forEach { preset ->
            assertTrue(preset.isBuiltIn)
            assertTrue(preset.collection.isNotBlank())
            assertTrue(preset.id.startsWith("builtin-"))
        }
    }

    @Test
    fun allRecipesAreAlreadyClamped() {
        // Um preset fora de intervalo seria "corrigido" em silêncio pelo
        // clamped() — o teste trava os valores escritos no código.
        BuiltInPresets.all.forEach { preset ->
            assertEquals(preset.id, preset.recipe, preset.recipe.clamped())
        }
    }

    @Test
    fun noPresetIsIdentity() {
        BuiltInPresets.all.forEach { preset ->
            assertFalse(preset.id, preset.recipe.isIdentity())
        }
    }

    @Test
    fun hslListsHaveEightChannels() {
        BuiltInPresets.all.forEach { preset ->
            assertEquals(preset.id, HslChannels.COUNT, preset.recipe.hsl.size)
        }
    }

    @Test
    fun byIdFindsEveryPreset() {
        BuiltInPresets.all.forEach { preset ->
            assertEquals(preset, BuiltInPresets.byId(preset.id))
        }
    }
}
