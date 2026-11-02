package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.manualShelfRow
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddToShelfOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    private val recorder = SectionContextRecorder()
    private val context = testSectionContext(recorder = recorder)
    private val shelf = manualShelfRow(1, "Book club", count = 12)

    /** Renders the overlay from [state], which the test changes; [shown] records the failure events it cleared. */
    private fun setOverlay(state: androidx.compose.runtime.MutableState<AddToShelfUIState>, shown: MutableList<Long>) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddToShelfOverlayContent(
                    state = state.value,
                    context = context,
                    onToggle = {},
                    onRetry = {},
                    onClose = {},
                    onFailureShown = {
                        shown += it
                        state.value = state.value.copy(failure = null)
                    },
                    reducedMotion = true,
                )
            }
        }
    }

    // TC-33
    @Test
    fun issue116_tc33_failureMessageShowsInTheOpenSheetOnce() {
        val shown = mutableListOf<Long>()
        val state = mutableStateOf(
            AddToShelfUIState(
                sheetOpen = true,
                content = ShelfSheetContent.Loaded(listOf(shelf), emptyList()),
                failure = ShelfToggleFailure(eventId = 1, shelfName = "Book club", wasAdding = true),
            ),
        )
        setOverlay(state, shown)

        val message = detailText(R.string.book_detail_more_shelf_add_failed, "Book club")
        rule.onNode(hasText(message) and hasAnyAncestor(hasTestTag(ShelfSheetTestTags.SHEET))).assertIsDisplayed()
        assertTrue("page messages while the sheet is open: ${recorder.messages}", recorder.messages.isEmpty())
        assertEquals(listOf(1L), shown)

        // A recomposition must not repeat the message.
        rule.runOnIdle { state.value = state.value.copy(sheetOpen = true) }
        rule.waitForIdle()
        assertEquals(listOf(1L), shown)
        assertTrue(recorder.messages.isEmpty())
    }

    // TC-33
    @Test
    fun issue116_tc33_failureGoesToThePageWhenTheSheetIsClosed() {
        val shown = mutableListOf<Long>()
        val state = mutableStateOf(
            AddToShelfUIState(
                sheetOpen = false,
                content = ShelfSheetContent.Loaded(listOf(shelf), emptyList()),
                failure = ShelfToggleFailure(eventId = 2, shelfName = "Book club", wasAdding = false),
            ),
        )
        setOverlay(state, shown)
        rule.waitForIdle()

        rule.onAllNodesWithTag(ShelfSheetTestTags.SHEET).assertCountEquals(0)
        val expected = detailText(R.string.book_detail_more_shelf_remove_failed, "Book club")
        assertEquals(listOf(expected), recorder.messages.map { it.text })
        assertEquals(listOf(2L), shown)
    }
}
