package com.omnibus.omnibus.feature.bookdetail.more.delete

import android.text.format.Formatter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.deletion.DeletionImpact
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Renders the sheet body for the state and book that [ui] and [book] return, with [taps] recording its callbacks. */
private fun ComposeContentTestRule.setSheet(
    ui: () -> DeleteBookUIState,
    book: () -> Book,
    taps: DeleteTaps,
    callbacks: DeleteSheetCallbacks = taps.callbacks(),
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize()) {
                DeleteBookSheetContent(
                    state = ui(),
                    book = book(),
                    accent = testSectionContext().accent.tint,
                    callbacks = callbacks,
                )
            }
        }
    }
}

/** The title, in the app's words for the title's own step. */
private val choosePickedTitle get() = detailText(R.string.book_detail_delete_choose_title, bookTitle)

private val bookTitle = testDetailBook().displayTitle

/** A book with one epub file, so its row label is "Delete files…". */
private fun fileBook(): Book = testDetailBook(formats = listOf("epub"))

/** A book with no files, so its row label is "Delete record…". */
private fun recordBook(): Book = testDetailBook(formats = emptyList())

/** Two files, the first with a label and the second without a size, and two copies (one with an ISBN). */
private val mixedManifest = deleteManifest(
    files = listOf(
        deleteFile(1, label = "Read copy", format = "epub", sizeBytes = 2048),
        deleteFile(2, label = null, format = "pdf", filename = "b.pdf", sizeBytes = 0),
    ),
    copies = listOf(
        deleteCopy(5, isbn = "9780000000001", note = null),
        deleteCopy(6, isbn = null, note = "shelf 3"),
    ),
)

/** Two files, no copies. */
private val filesOnlyManifest = deleteManifest(files = listOf(deleteFile(1), deleteFile(2)))

class DeleteBookSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-44
    @Test
    fun issue121_tc44_theLoadingAndLoadFailedStatesShowTheRowLabelAndTheirMessages() {
        var ui by mutableStateOf(DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Loading))
        var book by mutableStateOf(fileBook())
        val taps = DeleteTaps()
        rule.setSheet({ ui }, { book }, taps)

        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(detailText(R.string.book_detail_delete_row_files))
        rule.onNodeWithTag(DeleteTestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_delete_loading)).assertIsDisplayed()
        rule.onAllNodesWithTag(DeleteTestTags.fileRow(1)).assertCountEquals(0)

        rule.runOnIdle { book = recordBook() }
        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(detailText(R.string.book_detail_delete_row_record))

        rule.runOnIdle { ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.LoadFailed) }
        rule.onNodeWithTag(DeleteTestTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_delete_load_error_title)).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_delete_load_error_message)).assertIsDisplayed()
        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(detailText(R.string.book_detail_delete_row_record))

        rule.onNodeWithText(detailText(R.string.book_detail_delete_retry)).performClick()
        rule.waitForIdle()
        assertEquals(1, taps.retries)
    }

    // TC-45
    @Test
    fun issue121_tc45_theChooseStepForFilesOnlyListsEachFileWithItsLabelAndDetail() {
        val manifest = deleteManifest(
            files = listOf(
                deleteFile(1, label = "Read copy", format = "epub", sizeBytes = 2048),
                deleteFile(2, label = null, format = "pdf", filename = "b.pdf", sizeBytes = 0),
            ),
        )
        val ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Choosing(manifest))
        rule.setSheet({ ui }, { fileBook() }, DeleteTaps())

        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(choosePickedTitle)
        rule.onNodeWithText(detailText(R.string.book_detail_delete_intro_files)).assertIsDisplayed()
        rule.onNodeWithText(deletePlural(R.plurals.book_detail_delete_summary_files_on_disk, 2)).assertIsDisplayed()
        rule.onNodeWithTag(DeleteTestTags.SELECT_ALL).assertTextEquals(detailText(R.string.book_detail_delete_select_all))

        rule.onNodeWithText("Read copy").assertIsDisplayed()
        rule.onNodeWithText("b.pdf").assertIsDisplayed()
        val size = Formatter.formatShortFileSize(
            InstrumentationRegistry.getInstrumentation().targetContext,
            2048,
        )
        rule.onNodeWithText(detailText(R.string.book_detail_delete_file_detail, "EPUB", size)).assertIsDisplayed()
        rule.onNodeWithText("PDF").assertIsDisplayed()

        rule.onNodeWithTag(DeleteTestTags.fileRow(1)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        rule.onNodeWithTag(DeleteTestTags.fileRow(1)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off),
        )
        rule.onNodeWithTag(DeleteTestTags.fileRow(2)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off),
        )

        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION)
            .assertTextEquals(detailText(R.string.book_detail_delete_choose_files_unpicked))
            .assertIsNotEnabled()
        rule.onNodeWithTag(DeleteTestTags.CANCEL).assertIsEnabled()
    }

    // TC-46
    @Test
    fun issue121_tc46_theChooseStepWithCopiesCountsItemsAndListsFilesBeforeCopies() {
        val ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Choosing(mixedManifest))
        rule.setSheet({ ui }, { fileBook() }, DeleteTaps())

        rule.onNodeWithText(detailText(R.string.book_detail_delete_intro_items)).assertIsDisplayed()
        rule.onNodeWithText(deletePlural(R.plurals.book_detail_delete_summary_items, 4)).assertIsDisplayed()

        rule.onNodeWithText(detailText(R.string.book_detail_delete_copy_title_isbn, "9780000000001")).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_delete_copy_title)).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_delete_copy_detail)).assertIsDisplayed()
        rule.onNodeWithText("shelf 3").assertIsDisplayed()

        val lastFile = rule.onNodeWithTag(DeleteTestTags.fileRow(2)).bounds()
        val firstCopy = rule.onNodeWithTag(DeleteTestTags.copyRow(5)).bounds()
        assertTrue("files are listed before copies", lastFile.bottom <= firstCopy.top + 1f)

        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION)
            .assertTextEquals(detailText(R.string.book_detail_delete_choose_items_unpicked))
            .assertIsNotEnabled()
    }

    // TC-47
    @Test
    fun issue121_tc47_pickingFilesUpdatesTheCountAndSelectAllToggles() {
        var ui by mutableStateOf(DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Choosing(filesOnlyManifest)))
        val taps = DeleteTaps()
        rule.setSheet(
            { ui },
            { fileBook() },
            taps,
            taps.callbacks(
                toggleFile = { id -> ui = ui.copy(pickedFiles = ui.pickedFiles.flipped(id)) },
                toggleAll = {
                    val everything = ui.pickedFiles.size == filesOnlyManifest.files.size
                    ui = ui.copy(pickedFiles = if (everything) emptySet() else filesOnlyManifest.files.map { it.id }.toSet())
                },
            ),
        )

        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION).performClick()
        rule.waitForIdle()
        assertEquals(0, taps.continues)

        rule.onNodeWithTag(DeleteTestTags.fileRow(1)).performClick()
        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION)
            .assertTextEquals(deletePlural(R.plurals.book_detail_delete_choose_files, 1))
            .assertIsEnabled()

        rule.onNodeWithTag(DeleteTestTags.fileRow(2)).performClick()
        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION)
            .assertTextEquals(deletePlural(R.plurals.book_detail_delete_choose_files, 2))
        rule.onNodeWithTag(DeleteTestTags.SELECT_ALL).assertTextEquals(detailText(R.string.book_detail_delete_clear_selection))

        rule.onNodeWithTag(DeleteTestTags.SELECT_ALL).performClick()
        rule.onNodeWithTag(DeleteTestTags.SELECT_ALL).assertTextEquals(detailText(R.string.book_detail_delete_select_all))
        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION).assertIsNotEnabled()

        rule.onNodeWithTag(DeleteTestTags.SELECT_ALL).performClick()
        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION)
            .assertTextEquals(deletePlural(R.plurals.book_detail_delete_choose_files, 2))
            .performClick()
        rule.waitForIdle()
        assertEquals(1, taps.continues)
    }

    // TC-47
    @Test
    fun issue121_tc47_pickingItemsWithCopiesReadsDeleteItems() {
        var ui by mutableStateOf(DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Choosing(mixedManifest)))
        val taps = DeleteTaps()
        rule.setSheet(
            { ui },
            { fileBook() },
            taps,
            taps.callbacks(toggleCopy = { id -> ui = ui.copy(pickedCopies = ui.pickedCopies.flipped(id)) }),
        )

        rule.onNodeWithTag(DeleteTestTags.copyRow(6)).performClick()
        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION)
            .assertTextEquals(deletePlural(R.plurals.book_detail_delete_choose_items, 1))
            .assertIsEnabled()
        rule.onNodeWithTag(DeleteTestTags.copyRow(5)).performClick()
        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION)
            .assertTextEquals(deletePlural(R.plurals.book_detail_delete_choose_items, 2))
    }

    // TC-48
    @Test
    fun issue121_tc48_theConfirmStepWordsAPartialAndATotalDelete() {
        val partial = DeleteBookUIState(
            isAdmin = true,
            sheet = DeleteSheet.Confirming(filesOnlyManifest),
            pickedFiles = setOf(1L),
        )
        var ui by mutableStateOf(partial)
        val taps = DeleteTaps()
        rule.setSheet({ ui }, { fileBook() }, taps)

        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(deletePlural(R.plurals.book_detail_delete_heading_some_files, 1))
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_BODY).assertTextEquals(
            detailText(
                R.string.book_detail_delete_body_partial_files,
                detailText(R.string.book_detail_delete_picked_named, "file-1.epub"),
                bookTitle,
                deletePlural(R.plurals.book_detail_delete_remaining_files, 1),
            ),
        )
        rule.onAllNodesWithTag(DeleteTestTags.LOSSES).assertCountEquals(0)
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION).assertTextEquals(detailText(R.string.book_detail_delete_action_file))
        rule.onNodeWithTag(DeleteTestTags.BACK).performClick()
        rule.waitForIdle()
        assertEquals(1, taps.backs)

        val impact = DeletionImpact(highlights = 4, bookmarks = 2, reviews = 1)
        val total = filesOnlyManifest.copy(impact = impact)
        rule.runOnIdle {
            ui = DeleteBookUIState(
                isAdmin = true,
                sheet = DeleteSheet.Confirming(total),
                pickedFiles = setOf(1L, 2L),
            )
        }
        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(deletePlural(R.plurals.book_detail_delete_heading_all_files, 2))
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_BODY).assertTextEquals(
            detailText(R.string.book_detail_delete_body_total_files, bookTitle),
        )
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION).assertTextEquals(detailText(R.string.book_detail_delete_action_book))
        rule.onNodeWithTag(DeleteTestTags.LOSSES).assertTextEquals(
            detailText(R.string.book_detail_delete_losses, "4 highlights, 2 bookmarks and 1 review"),
        )
        rule.onNodeWithTag(DeleteTestTags.BACK).assertIsDisplayed()

        rule.runOnIdle { ui = ui.copy(sheet = DeleteSheet.Confirming(filesOnlyManifest)) }
        rule.onAllNodesWithTag(DeleteTestTags.LOSSES).assertCountEquals(0)
    }

    // TC-49
    @Test
    fun issue121_tc49_theConfirmStepWordsASingleItemTotalCopiesOnlyAndMixed() {
        val singleFile = deleteManifest(files = listOf(deleteFile(1)))
        var ui by mutableStateOf(
            DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Confirming(singleFile), pickedFiles = setOf(1L)),
        )
        rule.setSheet({ ui }, { fileBook() }, DeleteTaps())

        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(detailText(R.string.book_detail_delete_heading_name, bookTitle))
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_BODY).assertTextEquals(
            detailText(R.string.book_detail_delete_body_total_files, bookTitle),
        )
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION).assertTextEquals(detailText(R.string.book_detail_delete_action_book))

        val twoCopies = deleteManifest(copies = listOf(deleteCopy(5), deleteCopy(6)))
        rule.runOnIdle {
            ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Confirming(twoCopies), pickedCopies = setOf(5L, 6L))
        }
        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(deletePlural(R.plurals.book_detail_delete_heading_all_items, 2))
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_BODY).assertTextEquals(
            detailText(R.string.book_detail_delete_body_total_copies, bookTitle),
        )
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION).assertTextEquals(detailText(R.string.book_detail_delete_action_book))

        rule.runOnIdle {
            ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Confirming(mixedManifest), pickedFiles = setOf(1L), pickedCopies = setOf(5L))
        }
        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(deletePlural(R.plurals.book_detail_delete_heading_some_items, 2))
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_BODY).assertTextEquals(
            detailText(
                R.string.book_detail_delete_body_partial_mixed,
                detailText(
                    R.string.book_detail_delete_picked_files_and_copies,
                    deletePlural(R.plurals.book_detail_delete_picked_files, 1),
                    deletePlural(R.plurals.book_detail_delete_picked_copies, 1),
                ),
                bookTitle,
                deletePlural(R.plurals.book_detail_delete_remaining_items, 2),
            ),
        )
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION).assertTextEquals(detailText(R.string.book_detail_delete_action_items))
    }

    // TC-50
    @Test
    fun issue121_tc50_theRecordOnlyConfirmHasCancelAndNoBack() {
        val ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Confirming(deleteManifest()))
        val taps = DeleteTaps()
        rule.setSheet({ ui }, { recordBook() }, taps)

        rule.onNodeWithTag(DeleteTestTags.TITLE).assertTextEquals(detailText(R.string.book_detail_delete_heading_name, bookTitle))
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_BODY).assertTextEquals(detailText(R.string.book_detail_delete_body_record))
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION).assertTextEquals(detailText(R.string.book_detail_delete_action_record))
        rule.onAllNodesWithTag(DeleteTestTags.BACK).assertCountEquals(0)

        rule.onNodeWithTag(DeleteTestTags.CANCEL).assertTextEquals(detailText(R.string.book_detail_delete_cancel))
        rule.onNodeWithTag(DeleteTestTags.CANCEL).performClick()
        rule.waitForIdle()
        assertEquals(1, taps.closes)
    }

    // TC-51
    @Test
    fun issue121_tc51_theDeletingStateDisablesEveryControlAndSaysDeleting() {
        val ui = DeleteBookUIState(
            isAdmin = true,
            sheet = DeleteSheet.Confirming(filesOnlyManifest),
            pickedFiles = setOf(1L),
            deleting = true,
        )
        val taps = DeleteTaps()
        rule.setSheet({ ui }, { fileBook() }, taps)

        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION)
            .assertTextEquals(detailText(R.string.book_detail_delete_deleting))
            .assertIsNotEnabled()
        rule.onNodeWithTag(DeleteTestTags.BACK).assertIsNotEnabled()
        rule.onNodeWithTag(DeleteTestTags.CLOSE).assertIsNotEnabled()

        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION).performClick()
        rule.onNodeWithTag(DeleteTestTags.BACK).performClick()
        rule.onNodeWithTag(DeleteTestTags.CLOSE).performClick()
        rule.waitForIdle()
        assertEquals(0, taps.confirms)
        assertEquals(0, taps.backs)
        assertEquals(0, taps.closes)
    }

    // TC-52
    @Test
    fun issue121_tc52_aFailedDeleteShowsItsInlineErrorAndReenablesThePills() {
        var ui by mutableStateOf(
            DeleteBookUIState(
                isAdmin = true,
                sheet = DeleteSheet.Confirming(filesOnlyManifest),
                pickedFiles = setOf(1L),
                deleteError = DeleteFailure.Unreachable,
            ),
        )
        rule.setSheet({ ui }, { fileBook() }, DeleteTaps())

        rule.onNodeWithTag(DeleteTestTags.INLINE_ERROR).assertTextEquals(detailText(R.string.book_detail_delete_error_unreachable))
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION).assertIsEnabled()
        rule.onNodeWithTag(DeleteTestTags.BACK).assertIsEnabled()

        rule.runOnIdle { ui = ui.copy(deleteError = DeleteFailure.Other) }
        rule.onNodeWithTag(DeleteTestTags.INLINE_ERROR).assertTextEquals(detailText(R.string.book_detail_delete_error_other))

        rule.runOnIdle { ui = ui.copy(deleteError = null) }
        rule.onAllNodesWithTag(DeleteTestTags.INLINE_ERROR).assertCountEquals(0)
    }

    // TC-53
    @Test
    fun issue121_tc53_theSheetAndItsControlsCarryTheirSemantics() {
        var ui by mutableStateOf(
            DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Choosing(mixedManifest), pickedFiles = setOf(1L)),
        )
        rule.setSheet({ ui }, { fileBook() }, DeleteTaps())

        val title = rule.onNodeWithTag(DeleteTestTags.TITLE)
        title.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        title.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        val choose = rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION)
        choose.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        choose.assert(clickLabel(detailText(R.string.book_detail_delete_a11y_destructive)))
        choose.assert(SemanticsMatcher.expectValue(DeleteSemantics.Destructive, true))

        rule.onAllNodesWithTag(DeleteTestTags.fileRow(1)).assertCountEquals(1)
        rule.onNodeWithTag(DeleteTestTags.fileRow(1)).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        rule.onNodeWithTag(DeleteTestTags.fileRow(1)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On),
        )
        rule.onNodeWithTag(DeleteTestTags.fileRow(2)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off),
        )

        rule.onNodeWithTag(DeleteTestTags.CLOSE).assert(
            hasContentDescription(detailText(R.string.book_detail_delete_close)),
        )

        rule.runOnIdle { ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Confirming(mixedManifest), pickedFiles = setOf(1L)) }
        val confirm = rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION)
        confirm.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        confirm.assert(clickLabel(detailText(R.string.book_detail_delete_a11y_destructive)))
        confirm.assert(SemanticsMatcher.expectValue(DeleteSemantics.Destructive, true))
    }

    // TC-54
    @Test
    fun issue121_tc54_eachControlReportsItsTapOnce() {
        var ui by mutableStateOf(DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Choosing(mixedManifest), pickedFiles = setOf(1L)))
        val taps = DeleteTaps()
        rule.setSheet({ ui }, { fileBook() }, taps)

        rule.onNodeWithTag(DeleteTestTags.fileRow(2)).performClick()
        rule.onNodeWithTag(DeleteTestTags.copyRow(5)).performClick()
        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION).performClick()
        rule.onNodeWithTag(DeleteTestTags.CANCEL).performClick()
        rule.waitForIdle()
        assertEquals(listOf(2L), taps.toggledFiles)
        assertEquals(listOf(5L), taps.toggledCopies)
        assertEquals(1, taps.continues)
        assertEquals(1, taps.closes)

        rule.runOnIdle { ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Confirming(mixedManifest), pickedFiles = setOf(1L)) }
        rule.onNodeWithTag(DeleteTestTags.CONFIRM_ACTION).performClick()
        rule.onNodeWithTag(DeleteTestTags.BACK).performClick()
        rule.onNodeWithTag(DeleteTestTags.CLOSE).performClick()
        rule.waitForIdle()
        assertEquals(1, taps.confirms)
        assertEquals(1, taps.backs)
        assertEquals(2, taps.closes)
    }
}

/** A matcher for a node whose click action is labelled [label]. */
private fun clickLabel(label: String) = SemanticsMatcher("click label is $label") {
    it.config.getOrNull(SemanticsActions.OnClick)?.label == label
}
