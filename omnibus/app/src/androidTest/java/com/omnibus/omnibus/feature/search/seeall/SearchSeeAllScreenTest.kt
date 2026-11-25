package com.omnibus.omnibus.feature.search.seeall

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.SearchOpen
import com.omnibus.omnibus.feature.search.SearchSectionKind
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchSeeAllScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private val route = SearchSeeAllRoute(SearchSectionKind.Books, "wood")

    private fun hit(index: Int) = LibraryHit(
        uuid = "book-$index",
        title = "Library book $index",
        authorDisplay = "Author $index",
        year = null,
        formats = listOf("EPUB"),
        coverUrl = null,
        accent = null,
    )

    private fun setSeeAll(
        onBack: () -> Unit = {},
        onOpen: (SearchOpen) -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme {
                SearchSeeAllScreenContent(
                    route = route,
                    uiState = SearchSeeAllUIState(SeeAllBody.Loaded(SearchMatches(List(3) { hit(it) }, total = 3))),
                    onBack = onBack,
                    onRetry = {},
                    onOpen = onOpen,
                    modifier = Modifier,
                )
            }
        }
    }

    // TC-43
    @Test
    fun issue351_tc43_titleNamesTheQuery() {
        setSeeAll()

        rule.onNodeWithText(string(R.string.search_see_all_books_title, "wood")).assertIsDisplayed()
    }

    // TC-43
    @Test
    fun issue351_tc43_backGoesBack() {
        var backs = 0
        setSeeAll(onBack = { backs++ })

        rule.onNodeWithContentDescription(string(R.string.navigation_back)).performClick()

        assertEquals(1, backs)
    }

    // TC-43
    @Test
    fun issue351_tc43_rowOpensTheBook() {
        val opened = mutableListOf<SearchOpen>()
        setSeeAll(onOpen = { opened += it })

        rule.onNodeWithContentDescription("Book, Library book 1, Author 1, ebook").performClick()

        assertEquals(listOf<SearchOpen>(SearchOpen.Destination(BookDetailDestination.Book("book-1"))), opened)
    }
}
