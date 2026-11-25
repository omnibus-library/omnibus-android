package com.omnibus.omnibus.feature.search

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.search.SeriesMatch
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.rows.SearchSeriesRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchSeriesRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setRow(match: SeriesMatch, onOpen: (SearchOpen) -> Unit = {}) {
        rule.setContent {
            OmnibusTheme {
                SearchSeriesRow(match = match, query = "dun", onOpen = onOpen, modifier = Modifier)
            }
        }
    }

    private fun setSectionBlock(total: Int, shown: Int) {
        rule.setContent {
            OmnibusTheme {
                SearchSectionBlock(
                    kind = SearchSectionKind.Series,
                    total = total,
                    shown = shown,
                    onSeeAll = {},
                ) {}
            }
        }
    }

    // TC-10
    @Test
    fun issue355_tc10_offersSeeAllSeriesWhenMoreThanTheRowsShownExist() {
        setSectionBlock(total = 9, shown = 5)

        rule.onNodeWithContentDescription("See all 9 series").assertIsDisplayed()
    }

    // TC-10
    @Test
    fun issue355_tc10_hasNoSeeAllSeriesWhenEverySeriesIsShown() {
        setSectionBlock(total = 5, shown = 5)

        rule.onAllNodesWithContentDescription("See all 5 series").assertCountEquals(0)
    }

    // TC-11
    @Test
    fun issue355_tc11_announcesTheRowAsOneSeriesWithItsAuthorAndBookCount() {
        setRow(SeriesMatch(7, "Dune Chronicles", 9, "Frank Herbert"))

        rule.onNodeWithContentDescription("Series, Dune Chronicles, Frank Herbert, 9 books").assertIsDisplayed()
    }

    // TC-11
    @Test
    fun issue355_tc11_clickOpensTheSeriesDestination() {
        val opened = mutableListOf<SearchOpen>()
        setRow(SeriesMatch(7, "Dune Chronicles", 9, "Frank Herbert"), onOpen = { opened += it })

        rule.onNodeWithContentDescription("Series, Dune Chronicles, Frank Herbert, 9 books").performClick()

        assertEquals(
            listOf<SearchOpen>(SearchOpen.Destination(BookDetailDestination.Series(7, "Dune Chronicles"))),
            opened,
        )
    }

    // TC-12
    @Test
    fun issue355_tc12_dropsTheAuthorWhenTheSeriesHasNone() {
        setRow(SeriesMatch(8, "Dungeon Crawl", 1, null))

        rule.onNodeWithContentDescription("Series, Dungeon Crawl, 1 book").assertIsDisplayed()
    }
}
