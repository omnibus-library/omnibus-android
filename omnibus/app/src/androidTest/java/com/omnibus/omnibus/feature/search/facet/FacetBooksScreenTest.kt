package com.omnibus.omnibus.feature.search.facet

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.FacetKind
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.SearchFailure
import com.omnibus.omnibus.feature.search.SearchOpen
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FacetBooksScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val route = FacetBooksRoute(FacetKind.Tag, "Woodland")

    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private fun book(index: Int) = Book(
        id = index.toLong(),
        uniqueIdentifier = "book-$index",
        filename = "f$index",
        title = "Title $index",
        creators = listOf(Creator("Author $index", "author", null, null)),
        coverUrl = null,
        accent = null,
        formats = listOf("epub"),
        series = null,
        seriesIndex = null,
        hasPhysical = false,
        addedAt = null,
        lastInteractedAt = null,
        bookFiles = emptyList(),
        publishedDate = "1984",
    )

    private fun setFacet(
        uiState: FacetBooksUIState,
        onRetry: () -> Unit = {},
        onLoadMore: (itemsAfterLastVisible: Int) -> Unit = {},
        onRetryNextPage: () -> Unit = {},
        onOpen: (SearchOpen) -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme {
                FacetBooksScreenContent(
                    route = route,
                    uiState = uiState,
                    onBack = {},
                    onRetry = onRetry,
                    onLoadMore = onLoadMore,
                    onRetryNextPage = onRetryNextPage,
                    onOpen = onOpen,
                    modifier = Modifier,
                )
            }
        }
    }

    // TC-35
    @Test
    fun issue352_tc35_countsTheBooksTaggedWithTheName() {
        setFacet(
            FacetBooksUIState(
                FacetBody.Loaded(List(3) { book(it + 1) }, total = 14, nextCursor = null, nextPage = NextPageState.Idle),
            ),
        )

        rule.onNodeWithText("Woodland").assertIsDisplayed()
        rule.onNodeWithText("14 books tagged “Woodland”").assertIsDisplayed()
    }

    // TC-35
    @Test
    fun issue352_tc35_hasNoCountLineWithoutATotal() {
        setFacet(
            FacetBooksUIState(
                FacetBody.Loaded(List(3) { book(it + 1) }, total = null, nextCursor = null, nextPage = NextPageState.Idle),
            ),
        )

        rule.onAllNodesWithText("tagged", substring = true).assertCountEquals(0)
    }

    // TC-36
    @Test
    fun issue352_tc36_announcesEachBookWithItsAuthorYearAndBadges() {
        setFacet(
            FacetBooksUIState(
                FacetBody.Loaded(List(3) { book(it + 1) }, total = 3, nextCursor = null, nextPage = NextPageState.Idle),
            ),
        )

        rule.onNodeWithContentDescription("Book, Title 1, Author 1, 1984, ebook").assertIsDisplayed()
    }

    // TC-36
    @Test
    fun issue352_tc36_tappingABookOpensItsDetails() {
        val opened = mutableListOf<SearchOpen>()
        setFacet(
            FacetBooksUIState(
                FacetBody.Loaded(List(3) { book(it + 1) }, total = 3, nextCursor = null, nextPage = NextPageState.Idle),
            ),
            onOpen = { opened += it },
        )

        rule.onNodeWithContentDescription("Book, Title 1, Author 1, 1984, ebook").performClick()

        assertEquals(listOf<SearchOpen>(SearchOpen.Destination(BookDetailDestination.Book("book-1"))), opened)
    }

    // TC-37
    @Test
    fun issue352_tc37_tryAgainOnAFailedFirstPageCallsOnRetry() {
        var retries = 0
        setFacet(FacetBooksUIState(FacetBody.Failed(SearchFailure.Unreachable)), onRetry = { retries++ })

        rule.onNodeWithText(string(R.string.search_try_again)).performClick()

        assertEquals(1, retries)
    }

    // TC-38
    @Test
    fun issue352_tc38_tryAgainOnAFailedNextPageCallsOnRetryNextPage() {
        var retries = 0
        setFacet(
            FacetBooksUIState(
                FacetBody.Loaded(List(3) { book(it + 1) }, total = 9, nextCursor = "c", nextPage = NextPageState.Failed),
            ),
            onRetryNextPage = { retries++ },
        )

        rule.onNodeWithText(string(R.string.search_try_again)).performScrollTo().performClick()

        assertEquals(1, retries)
    }

    // TC-39
    @Test
    fun issue352_tc39_asksForTheNextPageWhenTheListIsShort() {
        val reports = mutableListOf<Int>()
        setFacet(
            FacetBooksUIState(
                FacetBody.Loaded(List(2) { book(it + 1) }, total = 2, nextCursor = "c", nextPage = NextPageState.Idle),
            ),
            onLoadMore = { reports += it },
        )

        rule.waitUntil { reports.isNotEmpty() }
    }
}
