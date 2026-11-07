package com.omnibus.omnibus.feature.bookdetail.more.delete

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
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
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

/** A book with one epub file. */
private fun fileBook(): Book = testDetailBook(formats = listOf("epub"))

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
    fun issue121_tc44_retryInTheLoadFailedStateRetries() {
        val ui = DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.LoadFailed)
        val taps = DeleteTaps()
        rule.setSheet({ ui }, { fileBook() }, taps)

        rule.onNodeWithText(detailText(R.string.book_detail_delete_retry)).performClick()
        rule.waitForIdle()

        assertEquals(1, taps.retries)
    }

    // TC-47
    @Test
    fun issue121_tc47_theChooseActionContinuesOnlyOnceSomethingIsPicked() {
        var ui by mutableStateOf(DeleteBookUIState(isAdmin = true, sheet = DeleteSheet.Choosing(filesOnlyManifest)))
        val taps = DeleteTaps()
        rule.setSheet(
            { ui },
            { fileBook() },
            taps,
            taps.callbacks(toggleFile = { id -> ui = ui.copy(pickedFiles = ui.pickedFiles.flipped(id)) }),
        )

        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION).performClick()
        rule.waitForIdle()
        assertEquals(0, taps.continues)

        rule.onNodeWithTag(DeleteTestTags.fileRow(1)).performClick()
        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION).performClick()
        rule.waitForIdle()

        assertEquals(1, taps.continues)
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

        rule.onNodeWithTag(DeleteTestTags.SELECT_ALL).performClick()
        rule.onNodeWithTag(DeleteTestTags.fileRow(2)).performClick()
        rule.onNodeWithTag(DeleteTestTags.copyRow(5)).performClick()
        rule.onNodeWithTag(DeleteTestTags.CHOOSE_ACTION).performClick()
        rule.onNodeWithTag(DeleteTestTags.CANCEL).performClick()
        rule.waitForIdle()
        assertEquals(1, taps.toggledAll)
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
