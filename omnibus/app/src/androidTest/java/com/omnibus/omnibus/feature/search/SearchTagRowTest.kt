package com.omnibus.omnibus.feature.search

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.library.FacetKind
import com.omnibus.omnibus.data.search.TagMatch
import com.omnibus.omnibus.feature.search.facet.FacetBooksRoute
import com.omnibus.omnibus.feature.search.rows.SearchTagRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchTagRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setRow(match: TagMatch, onOpen: (SearchOpen) -> Unit = {}) {
        rule.setContent {
            OmnibusTheme {
                SearchTagRow(match = match, query = "wood", onOpen = onOpen, modifier = Modifier)
            }
        }
    }

    private fun setSectionBlock(total: Int, shown: Int) {
        rule.setContent {
            OmnibusTheme {
                SearchSectionBlock(
                    kind = SearchSectionKind.Tags,
                    total = total,
                    shown = shown,
                    onSeeAll = {},
                ) {}
            }
        }
    }

    // TC-13
    @Test
    fun issue352_tc13_offersSeeAllTagsWhenMoreThanTheRowsShownExist() {
        setSectionBlock(total = 9, shown = 5)

        rule.onNodeWithContentDescription("See all 9 tags").assertIsDisplayed()
    }

    // TC-13
    @Test
    fun issue352_tc13_hasNoSeeAllTagsWhenEveryTagIsShown() {
        setSectionBlock(total = 5, shown = 5)

        rule.onAllNodesWithContentDescription("See all 5 tags").assertCountEquals(0)
    }

    // TC-13
    @Test
    fun issue352_tc13_announcesTheSingularSeeAllTag() {
        setSectionBlock(total = 1, shown = 0)

        rule.onNodeWithContentDescription("See all 1 tag").assertIsDisplayed()
    }

    // TC-15
    @Test
    fun issue352_tc15_announcesTheRowAsOneTagWithItsBookCount() {
        setRow(TagMatch("Woodland", 14))

        rule.onNodeWithContentDescription("Tag, Woodland, 14 books").assertIsDisplayed()
    }

    // TC-15
    @Test
    fun issue352_tc15_announcesTheSingularBookCount() {
        setRow(TagMatch("Woodland", 1))

        rule.onNodeWithContentDescription("Tag, Woodland, 1 book").assertIsDisplayed()
    }

    // TC-17
    @Test
    fun issue352_tc17_clickOpensTheTagsBookList() {
        val opened = mutableListOf<SearchOpen>()
        setRow(TagMatch("Woodland", 14), onOpen = { opened += it })

        rule.onNodeWithContentDescription("Tag, Woodland, 14 books").performClick()

        assertEquals(listOf<SearchOpen>(SearchOpen.Route(FacetBooksRoute(FacetKind.Tag, "Woodland"))), opened)
    }
}
