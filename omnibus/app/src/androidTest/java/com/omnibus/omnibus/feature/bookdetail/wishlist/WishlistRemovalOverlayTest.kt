package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** What the overlay reported to its callers, in order. */
private class OverlayReports {
    var confirms = 0
    var dismisses = 0
    val outcomesShown = mutableListOf<Long>()
    val toasts = mutableListOf<String>()

    /** The page's close count at the moment each toast was shown, so a test can check the toast came first. */
    val closeCountAtToast = mutableListOf<Int>()
}

/**
 * Renders the overlay content for [book] with [ui] state. Both are read on each composition, so a test can change them
 * with `runOnIdle`.
 */
private fun ComposeContentTestRule.setOverlay(
    ui: () -> WishlistRemovalUIState,
    book: () -> Book,
    recorder: SectionContextRecorder,
    reports: OverlayReports,
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            WishlistRemovalOverlayContent(
                state = ui(),
                context = testSectionContext(book = book(), recorder = recorder),
                onConfirm = { reports.confirms++ },
                onDismiss = { reports.dismisses++ },
                onOutcomeShown = { reports.outcomesShown += it },
                showToast = { text ->
                    reports.toasts += text
                    reports.closeCountAtToast += recorder.closeCount
                },
            )
        }
    }
}

private val removedText get() = detailText(R.string.book_detail_wishlist_removed)
private val failedText get() = detailText(R.string.book_detail_wishlist_remove_failed)

class WishlistRemovalOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-67
    @Test
    fun issue119_tc67_removeConfirmsOnce() {
        val ui = mutableStateOf(WishlistRemovalUIState(confirmOpen = true))
        val reports = OverlayReports()
        rule.setOverlay({ ui.value }, { testDetailBook(formats = listOf("epub")) }, SectionContextRecorder(), reports)

        rule.onNodeWithTag(WishlistTestTags.CONFIRM).performClick()
        rule.waitForIdle()
        assertEquals(1, reports.confirms)
    }

    // TC-67
    @Test
    fun issue119_tc67_cancelDismissesOnce() {
        val ui = mutableStateOf(WishlistRemovalUIState(confirmOpen = true))
        val reports = OverlayReports()
        rule.setOverlay({ ui.value }, { testDetailBook(formats = listOf("epub")) }, SectionContextRecorder(), reports)

        rule.onNodeWithTag(WishlistTestTags.CANCEL).performClick()
        rule.waitForIdle()
        assertEquals(1, reports.dismisses)
    }

    // TC-68
    @Test
    fun issue119_tc68_backDismissesTheDialogWithoutConfirming() {
        val ui = mutableStateOf(WishlistRemovalUIState(confirmOpen = true))
        val reports = OverlayReports()
        rule.setOverlay({ ui.value }, { testDetailBook(formats = listOf("epub")) }, SectionContextRecorder(), reports)
        rule.onNodeWithTag(WishlistTestTags.DIALOG).assertIsDisplayed()

        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(1, reports.dismisses)
        assertEquals(0, reports.confirms)
    }

    // TC-69
    @Test
    fun issue119_tc69_aRemovalThatKeepsThePageStaysAndReloads() {
        val ui = mutableStateOf(WishlistRemovalUIState(outcome = RemovalOutcome.Removed(eventId = 1, bookDeleted = false)))
        val recorder = SectionContextRecorder()
        val reports = OverlayReports()
        rule.setOverlay({ ui.value }, { testDetailBook(formats = listOf("epub")) }, recorder, reports)
        rule.waitForIdle()

        assertEquals(listOf(BookDetailMessage(removedText)), recorder.messages)
        assertEquals(1, recorder.reloadCount)
        assertEquals(0, recorder.closeCount)
    }

    // TC-70
    @Test
    fun issue119_tc70_aRemovalThatDeletesTheBookClosesThePageAfterTheToast() {
        val ui = mutableStateOf(WishlistRemovalUIState(outcome = RemovalOutcome.Removed(eventId = 1, bookDeleted = true)))
        val recorder = SectionContextRecorder()
        val reports = OverlayReports()
        rule.setOverlay({ ui.value }, { testDetailBook(formats = listOf("epub")) }, recorder, reports)
        rule.waitForIdle()

        assertEquals(listOf(removedText), reports.toasts)
        assertEquals(listOf(0), reports.closeCountAtToast)
        assertEquals(1, recorder.closeCount)
    }

    // TC-72
    @Test
    fun issue119_tc72_aFailureIsReportedOncePerEvent() {
        val ui = mutableStateOf(WishlistRemovalUIState(outcome = RemovalOutcome.Failed(eventId = 1)))
        val recorder = SectionContextRecorder()
        val reports = OverlayReports()
        rule.setOverlay({ ui.value }, { testDetailBook(formats = listOf("epub")) }, recorder, reports)
        rule.waitForIdle()
        assertEquals(listOf(BookDetailMessage(failedText)), recorder.messages)

        // A recomposition with the same outcome repeats nothing.
        rule.runOnIdle { ui.value = ui.value.copy(removing = true) }
        rule.waitForIdle()
        rule.runOnIdle { ui.value = ui.value.copy(removing = false) }
        rule.waitForIdle()
        assertEquals(1, recorder.messages.size)

        rule.runOnIdle { ui.value = ui.value.copy(outcome = RemovalOutcome.Failed(eventId = 2)) }
        rule.waitForIdle()
        assertEquals(listOf(BookDetailMessage(failedText), BookDetailMessage(failedText)), recorder.messages)
        assertEquals(listOf(1L, 2L), reports.outcomesShown)
    }
}
