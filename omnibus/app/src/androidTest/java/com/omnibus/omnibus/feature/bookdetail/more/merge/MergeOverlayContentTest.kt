package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MergeOverlayContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val recorder = SectionContextRecorder()
    private val context = testSectionContext(book = mergeTarget(), recorder = recorder)
    private val shown = mutableListOf<Long>()
    private val undone = mutableListOf<Long>()

    /** Renders the overlay from [state], which the test changes. Outcomes are recorded, not cleared. */
    private fun setOverlay(state: MutableState<MergeUIState>) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                MergeOverlayContent(
                    state = state.value,
                    context = context,
                    actions = MergeSheetActions(),
                    onUndo = { undone += it },
                    onOutcomeShown = { shown += it },
                )
            }
        }
    }

    // TC-46
    @Test
    fun issue120_tc46_mergedPostsHeldUndoToastOnceAndReloads() {
        val state = mutableStateOf(MergeUIState(outcome = MergeOutcome.Merged(1, 42)))
        setOverlay(state)
        rule.waitForIdle()

        // An unrelated recomposition must not post the message again.
        rule.runOnIdle { state.value = state.value.copy(isOnline = false) }
        rule.waitForIdle()

        assertEquals(1, recorder.messages.size)
        val message = recorder.messages.single()
        assertEquals(detailText(R.string.book_detail_merge_done), message.text)
        assertEquals(detailText(R.string.book_detail_merge_undo), message.actionLabel)
        assertTrue(message.holdUntilDismissed)
        assertEquals(1, recorder.reloadCount)
        assertEquals(listOf(1L), shown)

        rule.runOnIdle { message.onAction!!.invoke() }
        assertEquals(listOf(42L), undone)
    }

    // TC-47
    @Test
    fun issue120_tc47_undonePostsShortMessageAndReloads() {
        val state = mutableStateOf(MergeUIState(outcome = MergeOutcome.Merged(1, 42)))
        setOverlay(state)
        rule.waitForIdle()

        rule.runOnIdle { state.value = state.value.copy(outcome = MergeOutcome.Undone(2, 42)) }
        rule.waitForIdle()

        assertEquals(
            listOf(detailText(R.string.book_detail_merge_done), detailText(R.string.book_detail_merge_undone)),
            recorder.messages.map { it.text },
        )
        val undoneMessage = recorder.messages[1]
        assertNull(undoneMessage.actionLabel)
        assertFalse(undoneMessage.holdUntilDismissed)
        assertEquals(2, recorder.reloadCount)
    }

    // TC-48
    @Test
    fun issue120_tc48_undoFailuresRetryOrExpire() {
        val state = mutableStateOf(MergeUIState(outcome = MergeOutcome.UndoFailed(3, 42, retryable = true)))
        setOverlay(state)
        rule.waitForIdle()

        val retryable = recorder.messages.single()
        assertEquals(detailText(R.string.book_detail_merge_undo_failed), retryable.text)
        assertEquals(detailText(R.string.book_detail_merge_retry), retryable.actionLabel)
        assertTrue(retryable.holdUntilDismissed)
        rule.runOnIdle { retryable.onAction!!.invoke() }
        assertEquals(listOf(42L), undone)

        rule.runOnIdle { state.value = state.value.copy(outcome = MergeOutcome.UndoFailed(4, 42, retryable = false)) }
        rule.waitForIdle()

        assertEquals(2, recorder.messages.size)
        val expired = recorder.messages[1]
        assertEquals(detailText(R.string.book_detail_merge_undo_expired), expired.text)
        assertNull(expired.actionLabel)
        assertFalse(expired.holdUntilDismissed)
        assertEquals(0, recorder.reloadCount)
    }
}
