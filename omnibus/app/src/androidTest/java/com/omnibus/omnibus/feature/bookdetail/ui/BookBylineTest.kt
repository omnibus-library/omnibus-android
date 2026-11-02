package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.textLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

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
    fun issue93_tc87_unlinkedAuthorIsPlainText() {
        val author = "Ann Lee"
        rule.setDetailScreen(
            BookDetailUIState(
                book = testDetailBook(creators = listOf(Creator(author, "author", null, null))),
                isLoading = false,
            ),
        )

        rule.onNodeWithText(author, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(BookDetailTestTags.AUTHOR).assertHasNoClickAction()
    }

    // TC-89
    @Test
    fun issue93_tc89_yearSitsBesideAShortAuthor() {
        val author = "Ann Lee"
        rule.setDetailScreen(
            BookDetailUIState(
                book = testDetailBook(
                    creators = listOf(Creator(author, "author", null, null)),
                    publishedDate = "1925",
                ),
                isLoading = false,
            ),
        )

        val authorBounds = rule.onNodeWithText(author, useUnmergedTree = true).bounds()
        val year = rule.onNodeWithText(detailText(R.string.book_detail_year_beside, 1925), useUnmergedTree = true)
        year.assertIsDisplayed()
        val yearBounds = year.bounds()
        assertTrue(yearBounds.left >= authorBounds.right)
        assertEquals(authorBounds.center.y, yearBounds.center.y, 2f)
    }

    // TC-90
    @Test
    fun issue93_tc90_yearDropsUnderALongAuthor() {
        val author = List(8) { "Constance" }.joinToString(" ")
        rule.setDetailScreen(
            BookDetailUIState(
                book = testDetailBook(
                    creators = listOf(Creator(author, "author", null, null)),
                    publishedDate = "1925",
                ),
                isLoading = false,
            ),
        )

        val authorNode = rule.onNodeWithText(author, useUnmergedTree = true)
        val layout = authorNode.textLayout()
        assertEquals(1, layout.lineCount)
        assertTrue(layout.isLineEllipsized(0))

        val yearUnder = rule.onNodeWithText("1925", useUnmergedTree = true)
        yearUnder.assertIsDisplayed()
        assertTrue(yearUnder.bounds().top >= authorNode.bounds().bottom)
        rule.onAllNodesWithText(detailText(R.string.book_detail_year_beside, 1925), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    // TC-92
    @Test
    fun issue93_tc92_seriesLineReplacesTheYear() {
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

        val author = rule.onNodeWithText("Frank Herbert", useUnmergedTree = true).bounds()
        val series = rule.onNodeWithText("Dune, book 3", useUnmergedTree = true)
        series.assertIsDisplayed().assertHasNoClickAction()
        assertTrue(series.bounds().top >= author.bottom)
        val decoration = series.textLayout().layoutInput.style.textDecoration
        assertTrue(decoration == null || decoration == TextDecoration.None)
        rule.onAllNodesWithText("1965", substring = true, useUnmergedTree = true).assertCountEquals(0)
    }
}
