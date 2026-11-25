package com.omnibus.omnibus.feature.search

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.search.AuthorMatch
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.rows.SearchAuthorRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchAuthorRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setRow(match: AuthorMatch, onOpen: (SearchOpen) -> Unit = {}) {
        rule.setContent {
            OmnibusTheme {
                SearchAuthorRow(match = match, query = "her", onOpen = onOpen, modifier = Modifier)
            }
        }
    }

    private fun setSectionBlock(total: Int, shown: Int) {
        rule.setContent {
            OmnibusTheme {
                SearchSectionBlock(
                    kind = SearchSectionKind.Authors,
                    total = total,
                    shown = shown,
                    onSeeAll = {},
                ) {}
            }
        }
    }

    // TC-10
    @Test
    fun issue354_tc10_offersSeeAllAuthorsWhenMoreThanTheRowsShownExist() {
        setSectionBlock(total = 9, shown = 5)

        rule.onNodeWithContentDescription("See all 9 authors").assertIsDisplayed()
    }

    // TC-10
    @Test
    fun issue354_tc10_hasNoSeeAllAuthorsWhenEveryAuthorIsShown() {
        setSectionBlock(total = 5, shown = 5)

        rule.onAllNodesWithContentDescription("See all 5 authors").assertCountEquals(0)
    }

    // TC-11
    @Test
    fun issue354_tc11_announcesTheRowAsOneAuthorWithItsBookCountAndLeadTitle() {
        setRow(AuthorMatch(7, "Frank Herbert", 9, "Dune"))

        rule.onNodeWithContentDescription("Author, Frank Herbert, 9 books, Dune").assertIsDisplayed()
    }

    // TC-11
    @Test
    fun issue354_tc11_clickOpensTheAuthorDestination() {
        val opened = mutableListOf<SearchOpen>()
        setRow(AuthorMatch(7, "Frank Herbert", 9, "Dune"), onOpen = { opened += it })

        rule.onNodeWithContentDescription("Author, Frank Herbert, 9 books, Dune").performClick()

        assertEquals(
            listOf<SearchOpen>(SearchOpen.Destination(BookDetailDestination.Author(7, "Frank Herbert"))),
            opened,
        )
    }

    // TC-12
    @Test
    fun issue354_tc12_dropsTheLeadTitleWhenTheAuthorHasNone() {
        setRow(AuthorMatch(8, "Herman Melville", 1, null))

        rule.onNodeWithContentDescription("Author, Herman Melville, 1 book").assertIsDisplayed()
    }
}
