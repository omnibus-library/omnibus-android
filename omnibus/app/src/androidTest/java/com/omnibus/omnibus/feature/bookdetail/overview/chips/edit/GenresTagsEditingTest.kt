package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.overview.chips.GenresTagsSection
import com.omnibus.omnibus.feature.bookdetail.overview.chips.GenresTagsTestTags
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The Genres and Tags rows for an editor: tapping the "+" chips, saves shown on the rows, and failures.
 * The editor is a [TestChipEditor] through [LocalChipEditorSource], so these tests never reach Hilt.
 */
class GenresTagsEditingTest {

    @get:Rule
    val rule = createComposeRule()

    private val tags = GenresTagsTestTags
    private val chipEdit = ChipEditTestTags
    private val recorder = SectionContextRecorder()

    private fun bookWith(genres: List<String>, subjects: List<String>): Book =
        testDetailBook().copy(genres = genres, subjects = subjects)

    private fun ComposeContentTestRule.setEditor(
        editor: ChipEditor?,
        book: Book = bookWith(emptyList(), emptyList()),
    ) {
        setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalChipEditorSource provides ChipEditorSource { _ -> editor }) {
                    GenresTagsSection.Content(testSectionContext(book = book, recorder = recorder), Modifier.fillMaxWidth())
                }
            }
        }
        waitForIdle()
    }

    private fun chipIn(row: String, text: String): SemanticsNodeInteraction =
        rule.onNode(hasTestTag(tags.CHIP) and hasText(text) and hasAnyAncestor(hasTestTag(row)))

    private fun addGenres() = detailText(R.string.book_detail_chipedit_add_genres)
    private fun addTags() = detailText(R.string.book_detail_chipedit_add_tags)

    // TC-70
    @Test
    fun issue101_ac2_plusIsDisabledWhileOffline() {
        val editor = TestChipEditor(editorState(isOnline = false))
        rule.setEditor(editor)

        rule.onNodeWithTag(chipEdit.ADD_GENRES).assertIsNotEnabled()
        rule.onNodeWithTag(chipEdit.ADD_GENRES).performClick()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), editor.calls.filter { it.startsWith("open:") })
    }

    // TC-71
    @Test
    fun issue101_r5_tappingPlusOpensTheSheetForThatKind() {
        val editor = TestChipEditor(editorState())
        rule.setEditor(editor)

        rule.onNodeWithContentDescription(addGenres()).performClick()
        rule.onNodeWithContentDescription(addTags()).performClick()
        rule.waitForIdle()

        assertEquals(listOf("open:Genres", "open:Tags"), editor.calls.filterNot { it == "bind" })
    }

    // TC-72
    @Test
    fun issue101_ac3_removingInTheSheetRemovesItFromTheRowBehind() {
        val editor = TestChipEditor(
            editorState(genres = listOf("Noir", "Epic"), openKind = MetadataListKind.Genres),
            applyEdits = true,
        )
        rule.setEditor(editor, bookWith(listOf("Noir", "Epic"), emptyList()))

        rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_remove, "Noir")).performClick()
        rule.waitForIdle()

        rule.onAllNodes(hasTestTag(tags.CHIP) and hasText("Noir") and hasAnyAncestor(hasTestTag(tags.GENRES_ROW)))
            .assertCountEquals(0)
        chipIn(tags.GENRES_ROW, "Epic").assertExists()
    }

    // TC-73
    @Test
    fun issue101_ac3_overlayForwardsTheMergedBookOnce() {
        val merged = bookWith(listOf("Noir", "Epic"), listOf("Cozy"))
        val editor = TestChipEditor(editorState(genres = listOf("Noir", "Epic"), bookUpdate = ChipEditBookUpdate(7, merged)))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ChipEditOverlay(editor, testSectionContext(book = merged, recorder = recorder), autoFocus = false)
            }
        }
        rule.waitForIdle()

        assertEquals(listOf(merged), recorder.changedBooks)
        assertEquals(1, editor.calls.count { it == "bookUpdateApplied:7" })

        rule.runOnIdle { editor.current = editor.current.copy(entry = "x") }
        rule.waitForIdle()
        assertEquals(listOf(merged), recorder.changedBooks)
        assertEquals(1, editor.calls.count { it == "bookUpdateApplied:7" })
    }

    // TC-74
    @Test
    fun issue101_ac6_failureIsShownInsideTheOpenSheet() {
        val editor = TestChipEditor(
            editorState(genres = listOf("Noir"), openKind = MetadataListKind.Genres, failure = ChipEditFailure(3, MetadataListKind.Genres)),
        )
        val message = detailText(R.string.book_detail_chipedit_failed_genres)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ChipEditOverlay(editor, testSectionContext(recorder = recorder), autoFocus = false)
            }
        }
        rule.waitForIdle()

        rule.onNode(hasText(message) and hasAnyAncestor(hasTestTag(chipEdit.SHEET))).assertExists()
        assertEquals(emptyList<BookDetailMessage>(), recorder.messages)
        assertEquals(1, editor.calls.count { it == "failureShown:3" })
    }

    // TC-75
    @Test
    fun issue101_ac6_failureGoesToThePageWhenTheSheetIsClosed() {
        val editor = TestChipEditor(editorState(failure = ChipEditFailure(4, MetadataListKind.Tags)))
        val message = detailText(R.string.book_detail_chipedit_failed_tags)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ChipEditOverlay(editor, testSectionContext(recorder = recorder), autoFocus = false)
            }
        }
        rule.waitForIdle()

        assertEquals(listOf(BookDetailMessage(message)), recorder.messages)
        rule.onAllNodesWithTag(chipEdit.ERROR).assertCountEquals(0)
        assertEquals(1, editor.calls.count { it == "failureShown:4" })
    }
}
