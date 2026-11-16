package app.lume.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditHistoryTest {

    @Test
    fun preview_doesNotEnterHistory() {
        val history = EditHistory()
        history.preview(Recipe(exposure = 1f))
        assertEquals(Recipe(exposure = 1f), history.current)
        assertFalse(history.canUndo)
        assertNull(history.undo())
    }

    @Test
    fun commitUndoRedo_walkThroughRecipes() {
        val history = EditHistory()
        history.commit(Recipe(exposure = 1f))
        history.commit(Recipe(exposure = 1f, contrast = 50f))
        assertTrue(history.canUndo)
        assertEquals(Recipe(exposure = 1f), history.undo())
        assertTrue(history.canRedo)
        assertEquals(Recipe(exposure = 1f, contrast = 50f), history.redo())
    }

    @Test
    fun commitAfterUndo_clearsRedo() {
        val history = EditHistory()
        history.commit(Recipe(exposure = 1f))
        history.commit(Recipe(exposure = 2f))
        history.undo()
        history.commit(Recipe(contrast = 10f))
        assertFalse(history.canRedo)
    }

    @Test
    fun commitSameRecipe_isNoOp() {
        val history = EditHistory()
        history.commit(Recipe.IDENTITY)
        assertFalse(history.canUndo)
    }

    @Test
    fun history_isCapped() {
        val history = EditHistory()
        for (i in 1..150) {
            history.commit(Recipe(contrast = (i % 100).toFloat()))
        }
        assertEquals(EditHistory.MAX_ENTRIES, history.undoCount)
    }

    @Test
    fun previewThenCommit_createsSingleUndoEntry() {
        val history = EditHistory()
        history.preview(Recipe(exposure = 0.5f))
        history.preview(Recipe(exposure = 1f))
        history.commit(history.current)
        assertTrue(history.canUndo)
        assertEquals(Recipe.IDENTITY, history.undo())
        assertFalse(history.canUndo)
    }

    @Test
    fun previewThenCommit_backToStart_isNoOp() {
        val history = EditHistory()
        history.preview(Recipe(exposure = 1f))
        history.preview(Recipe.IDENTITY)
        history.commit(history.current)
        assertFalse(history.canUndo)
    }

    @Test
    fun reset_clearsHistory() {
        val history = EditHistory()
        history.commit(Recipe(exposure = 1f))
        history.reset(Recipe(saturation = 20f))
        assertEquals(Recipe(saturation = 20f), history.current)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }
}
