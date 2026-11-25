package com.omnibus.omnibus.feature.search

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.search.GenreMatch
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.rows.SearchGenreRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchGenreRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setRow(match: GenreMatch, onOpen: (SearchOpen) -> Unit = {}) {
        rule.setContent {
            OmnibusTheme {
                SearchGenreRow(match = match, query = "fan", onOpen = onOpen, modifier = Modifier)
            }
        }
    }

    private fun setSectionBlock(total: Int, shown: Int) {
        rule.setContent {
            OmnibusTheme {
                SearchSectionBlock(
                    kind = SearchSectionKind.Genres,
                    total = total,
                    shown = shown,
                    onSeeAll = {},
                ) {}
            }
        }
    }

    // TC-10
    @Test
    fun issue353_tc10_offersSeeAllGenresWhenMoreThanTheRowsShownExist() {
        setSectionBlock(total = 9, shown = 5)

        rule.onNodeWithContentDescription("See all 9 genres").assertIsDisplayed()
    }

    // TC-10
    @Test
    fun issue353_tc10_hasNoSeeAllGenresWhenEveryGenreIsShown() {
        setSectionBlock(total = 5, shown = 5)

        rule.onAllNodesWithContentDescription("See all 5 genres").assertCountEquals(0)
    }

    // TC-11
    @Test
    fun issue353_tc11_announcesTheRowAsOneGenreWithItsBookCount() {
        setRow(GenreMatch("Fantasy", 40))

        rule.onNodeWithContentDescription("Genre, Fantasy, 40 books").assertIsDisplayed()
    }

    // TC-11
    @Test
    fun issue353_tc11_clickOpensTheGenreDestination() {
        val opened = mutableListOf<SearchOpen>()
        setRow(GenreMatch("Fantasy", 40), onOpen = { opened += it })

        rule.onNodeWithContentDescription("Genre, Fantasy, 40 books").performClick()

        assertEquals(
            listOf<SearchOpen>(SearchOpen.Destination(BookDetailDestination.Genre("Fantasy"))),
            opened,
        )
    }
}
