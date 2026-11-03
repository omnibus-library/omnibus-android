package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** What the overlay reported to its callers, in order. */
private class OverlayReports {
    val outcomesShown = mutableListOf<Long>()
    val toasts = mutableListOf<String>()

    /** The page's close count at the moment each toast was shown, so a test can check the toast came first. */
    val closeCountAtToast = mutableListOf<Int>()
}

/**
 * Renders the overlay for [ui] (read on each composition) with a recording page context and recording callbacks.
 */
private fun ComposeContentTestRule.setOverlay(
    ui: () -> DeleteBookUIState,
    recorder: SectionContextRecorder,
    reports: OverlayReports,
    taps: DeleteTaps = DeleteTaps(),
    book: Book = testDetailBook(formats = listOf("epub")),
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            DeleteOverlayContent(
                state = ui(),
                context = testSectionContext(book = book, recorder = recorder),
                callbacks = taps.callbacks(),
                onOutcomeShown = { reports.outcomesShown += it },
                showToast = { text ->
                    reports.toasts += text
                    reports.closeCountAtToast += recorder.closeCount
                },
            )
        }
    }
}

class DeleteOverlayTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-55
    @Test
    fun issue121_tc55_theSheetShowsOnlyWhileAStepIsOpen() {
        val manifest = deleteManifest(files = listOf(deleteFile(1)))
        var ui by mutableStateOf(DeleteBookUIState(isAdmin = true))
        rule.setOverlay({ ui }, SectionContextRecorder(), OverlayReports())

        rule.onAllNodesWithTag(DeleteTestTags.SHEET).assertCountEquals(0)
        rule.onAllNodesWithTag(DeleteTestTags.TITLE).assertCountEquals(0)

        rule.runOnIdle { ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Choosing(manifest)) }
        rule.onNodeWithTag(DeleteTestTags.SHEET).assertIsDisplayed()
        rule.onNodeWithTag(DeleteTestTags.TITLE).assertIsDisplayed()

        rule.runOnIdle { ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Closed) }
        rule.onAllNodesWithTag(DeleteTestTags.SHEET).assertCountEquals(0)
    }

    // TC-56
    @Test
    fun issue121_tc56_aWholeBookDeleteShowsTheToastThenClosesThePageOnce() {
        val ui = mutableStateOf(
            DeleteBookUIState(
                isAdmin = true,
                outcome = DeleteOutcome(eventId = 1, bookDeleted = true, deletedFiles = 2, deletedCopies = 0),
            ),
        )
        val recorder = SectionContextRecorder()
        val reports = OverlayReports()
        rule.setOverlay({ ui.value }, recorder, reports)
        rule.waitForIdle()

        assertEquals(listOf(detailText(R.string.book_detail_delete_done_book)), reports.toasts)
        assertEquals(listOf(0), reports.closeCountAtToast)
        assertEquals(1, recorder.closeCount)
        assertTrue(recorder.messages.isEmpty())
        assertEquals(0, recorder.reloadCount)
        assertEquals(listOf(1L), reports.outcomesShown)
    }

    // TC-57
    @Test
    fun issue121_tc57_aPartialDeleteShowsItsMessageAndReloadsForEachKind() {
        val cases = listOf(
            Triple(1, 0, detailText(R.string.book_detail_delete_done_file)),
            Triple(2, 0, deletePlural(R.plurals.book_detail_delete_done_files, 2)),
            Triple(0, 1, detailText(R.string.book_detail_delete_done_copy)),
            Triple(0, 2, deletePlural(R.plurals.book_detail_delete_done_items_removed, 2)),
            Triple(1, 1, deletePlural(R.plurals.book_detail_delete_done_items_deleted, 2)),
        )
        val ui = mutableStateOf(DeleteBookUIState(isAdmin = true))
        val recorder = SectionContextRecorder()
        val reports = OverlayReports()
        rule.setOverlay({ ui.value }, recorder, reports)

        cases.forEachIndexed { index, (files, copies, _) ->
            rule.runOnIdle {
                ui.value = ui.value.copy(
                    outcome = DeleteOutcome(
                        eventId = index + 1L,
                        bookDeleted = false,
                        deletedFiles = files,
                        deletedCopies = copies,
                    ),
                )
            }
            rule.waitForIdle()
        }

        assertEquals(cases.map { BookDetailMessage(it.third) }, recorder.messages)
        assertEquals(cases.size, recorder.reloadCount)
        assertTrue(reports.toasts.isEmpty())
        assertEquals(0, recorder.closeCount)
        assertEquals((1L..cases.size.toLong()).toList(), reports.outcomesShown)
    }

    // TC-58
    @Test
    fun issue121_tc58_eachOutcomeIsReportedOnceAcrossRecompositions() {
        val ui = mutableStateOf(
            DeleteBookUIState(
                isAdmin = true,
                outcome = DeleteOutcome(eventId = 1, bookDeleted = false, deletedFiles = 1, deletedCopies = 0),
            ),
        )
        val recorder = SectionContextRecorder()
        rule.setOverlay({ ui.value }, recorder, OverlayReports())
        rule.waitForIdle()
        assertEquals(listOf(BookDetailMessage(detailText(R.string.book_detail_delete_done_file))), recorder.messages)

        rule.runOnIdle { ui.value = ui.value.copy(pickedFiles = setOf(1L)) }
        rule.waitForIdle()
        assertEquals(1, recorder.messages.size)
        assertEquals(1, recorder.reloadCount)

        rule.runOnIdle {
            ui.value = ui.value.copy(outcome = DeleteOutcome(eventId = 2, bookDeleted = false, deletedFiles = 2, deletedCopies = 0))
        }
        rule.waitForIdle()
        assertEquals(2, recorder.messages.size)
        assertEquals(BookDetailMessage(deletePlural(R.plurals.book_detail_delete_done_files, 2)), recorder.messages[1])
        assertEquals(2, recorder.reloadCount)
    }

    // TC-61
    @Test
    fun issue121_tc61_whileDeletingBackDoesNotDismissTheSheetAndAfterwardItDoes() {
        val manifest = deleteManifest(files = listOf(deleteFile(1), deleteFile(2)))
        var ui by mutableStateOf(
            DeleteBookUIState(
                isAdmin = true,
                sheet = DeleteSheet.Confirming(manifest),
                pickedFiles = setOf(1L),
                deleting = true,
            ),
        )
        val taps = DeleteTaps()
        rule.setOverlay({ ui }, SectionContextRecorder(), OverlayReports(), taps)
        rule.onNodeWithTag(DeleteTestTags.SHEET).assertIsDisplayed()

        Espresso.pressBack()
        rule.waitForIdle()
        rule.onNodeWithTag(DeleteTestTags.SHEET).assertIsDisplayed()
        assertEquals(0, taps.closes)

        rule.runOnIdle { ui = ui.copy(deleting = false) }
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(1, taps.closes)
    }
}
