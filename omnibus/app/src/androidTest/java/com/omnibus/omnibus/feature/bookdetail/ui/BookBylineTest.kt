package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Author navigation and which byline lines are clickable. BookBylineScreenshotTest covers how the byline looks. */
class BookBylineTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-86
    @Test
    fun issue93_tc86_linkedAuthorNavigates() {
        val author = "Ann Lee"
        val navigations = mutableListOf<BookDetailDestination>()
        rule.setDetailScreen(
            BookDetailUIState(
                book = testDetailBook(creators = listOf(Creator(author, "author", null, 7L))),
                isLoading = false,
            ),
            onNavigate = { navigations += it },
        )

        val node = rule.onNodeWithTag(BookDetailTestTags.AUTHOR)
        node.assertHasClickAction().assertHeightIsAtLeast(40.dp)
        assertEquals(
            detailText(R.string.book_detail_author_click_label),
            node.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )
        node.performClick()
        rule.waitForIdle()
        assertEquals(listOf(BookDetailDestination.Author(7L, author)), navigations)
    }

    // TC-87
    @Test
    fun issue93_tc87_unlinkedAuthorIsNotClickable() {
        val author = "Ann Lee"
        rule.setDetailScreen(
            BookDetailUIState(
                book = testDetailBook(creators = listOf(Creator(author, "author", null, null))),
                isLoading = false,
            ),
        )

        rule.onNodeWithTag(BookDetailTestTags.AUTHOR).assertHasNoClickAction()
    }

    // TC-92
    @Test
    fun issue93_tc92_seriesLineWithoutASeriesSectionIsNotClickable() {
        rule.setDetailScreen(
            BookDetailUIState(
                book = testDetailBook(
                    creators = listOf(Creator("Frank Herbert", "author", null, null)),
                    series = "Dune",
                    seriesIndex = "3",
                    publishedDate = "1965",
                ),
                isLoading = false,
            ),
        )

        rule.onNodeWithText("Dune, book 3", useUnmergedTree = true).assertHasNoClickAction()
    }
}
