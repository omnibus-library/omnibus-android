package com.omnibus.omnibus.feature.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.search.seeall.SearchSeeAllRoute
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun string(id: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun hit(index: Int) = LibraryHit(
        uuid = "book-$index",
        title = "Library book $index",
        authorDisplay = "Author $index",
        year = null,
        formats = listOf("EPUB"),
        coverUrl = null,
        accent = null,
    )

    private fun results(query: String, hits: List<LibraryHit>) = SearchUIState(
        query = query,
        body = SearchBody.Results(
            query = query,
            sections = listOf(SearchSection(SearchSectionKind.Books, SearchMatches(hits, hits.size))),
        ),
    )

    private fun setSearch(
        uiState: SearchUIState,
        onQueryChange: (String) -> Unit = {},
        onClear: () -> Unit = {},
        onRetry: () -> Unit = {},
        onNoticeShown: () -> Unit = {},
        onOpen: (SearchOpen) -> Unit = {},
        listState: LazyListState = LazyListState(),
    ) {
        rule.setContent {
            OmnibusTheme {
                SearchScreenContent(
                    uiState = uiState,
                    onQueryChange = onQueryChange,
                    onClear = onClear,
                    onRetry = onRetry,
                    onNoticeShown = onNoticeShown,
                    onOpen = onOpen,
                    modifier = Modifier,
                    listState = listState,
                )
            }
        }
    }

    // TC-23
    @Test
    fun issue351_tc23_promptShowsMastheadFieldAndMessage() {
        setSearch(SearchUIState())

        rule.onNodeWithText(string(R.string.search_title)).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.search_field_label)).assertIsDisplayed()
        rule.onNodeWithText(string(R.string.search_prompt_message)).assertIsDisplayed()
    }

    // TC-23
    @Test
    fun issue351_tc23_fieldIsNotFocusedOnOpen() {
        setSearch(SearchUIState())

        rule.onNodeWithContentDescription(string(R.string.search_field_label)).assertIsNotFocused()
    }

    // TC-24
    @Test
    fun issue351_tc24_fieldIsAnnouncedWithItsText() {
        setSearch(SearchUIState(query = "wood"))

        rule.onNodeWithContentDescription(string(R.string.search_field_label))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("wood")))
    }

    // TC-25
    @Test
    fun issue351_tc25_keyboardActionIsSearch() {
        setSearch(SearchUIState())

        rule.onNodeWithContentDescription(string(R.string.search_field_label)).assert(hasImeAction(ImeAction.Search))
    }

    // TC-25
    @Test
    fun issue351_tc25_searchKeyOnlyHidesKeyboard() {
        var queryChanges = 0
        var retries = 0
        setSearch(SearchUIState(), onQueryChange = { queryChanges++ }, onRetry = { retries++ })

        rule.onNodeWithContentDescription(string(R.string.search_field_label)).performClick()
        rule.onNodeWithContentDescription(string(R.string.search_field_label)).performImeAction()

        rule.onNodeWithContentDescription(string(R.string.search_field_label)).assertIsNotFocused()
        assertEquals(0, queryChanges)
        assertEquals(0, retries)
    }

    // TC-26
    @Test
    fun issue351_tc26_clearButtonShowsOnlyWithText() {
        val query = mutableStateOf("")
        rule.setContent {
            OmnibusTheme {
                SearchScreenContent(
                    uiState = SearchUIState(query = query.value),
                    onQueryChange = {},
                    onClear = {},
                    onRetry = {},
                    onNoticeShown = {},
                    onOpen = {},
                )
            }
        }

        rule.onAllNodesWithContentDescription(string(R.string.search_clear)).assertCountEquals(0)

        rule.runOnIdle { query.value = "wood" }

        rule.onNodeWithContentDescription(string(R.string.search_clear)).assertIsDisplayed()
    }

    // TC-26
    @Test
    fun issue351_tc26_clearButtonIsAtLeast48dp() {
        setSearch(SearchUIState(query = "wood"))

        rule.onNodeWithContentDescription(string(R.string.search_clear))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    // TC-26
    @Test
    fun issue351_tc26_clearButtonClears() {
        var clears = 0
        setSearch(SearchUIState(query = "wood"), onClear = { clears++ })

        rule.onNodeWithContentDescription(string(R.string.search_clear)).performClick()

        assertEquals(1, clears)
    }

    // TC-27
    @Test
    fun issue351_tc27_sectionLabelIsAHeading() {
        setSearch(results("wood", listOf(hit(1))))

        rule.onNodeWithText("BOOKS").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // TC-28
    @Test
    fun issue351_tc28_rowIsOneAnnouncedElement() {
        val mythago = LibraryHit(
            uuid = "u-1",
            title = "Mythago Wood",
            authorDisplay = "Robert Holdstock",
            year = "1984",
            formats = listOf("epub"),
            coverUrl = null,
            accent = null,
            hasPhysical = true,
        )
        setSearch(results("wood", listOf(mythago)))

        rule.onNodeWithContentDescription("Book, Mythago Wood, Robert Holdstock, 1984, ebook, print")
            .assert(hasClickAction())
    }

    // TC-28
    @Test
    fun issue351_tc28_rowIsAtLeast48dpTall() {
        setSearch(results("wood", listOf(hit(1))))

        rule.onNodeWithContentDescription("Book, Library book 1, Author 1, ebook")
            .assertHeightIsAtLeast(48.dp)
    }

    // TC-28
    @Test
    fun issue351_tc28_tappingARowOpensTheBook() {
        val opened = mutableListOf<SearchOpen>()
        setSearch(results("wood", listOf(hit(1))), onOpen = { opened += it })

        rule.onNodeWithContentDescription("Book, Library book 1, Author 1, ebook").performClick()

        assertEquals(listOf<SearchOpen>(SearchOpen.Destination(BookDetailDestination.Book("book-1"))), opened)
    }

    // TC-29
    @Test
    fun issue351_tc29_failedStateShowsItsCopy() {
        setSearch(SearchUIState(query = "wood", body = SearchBody.Failed(SearchFailure.NotWorking)))

        rule.onNodeWithText(string(R.string.search_not_working_title)).assertIsDisplayed()
    }

    // TC-29
    @Test
    fun issue351_tc29_tryAgainRetries() {
        var retries = 0
        setSearch(
            SearchUIState(query = "wood", body = SearchBody.Failed(SearchFailure.NotWorking)),
            onRetry = { retries++ },
        )

        rule.onNodeWithText(string(R.string.search_try_again)).performClick()

        assertEquals(1, retries)
    }

    // TC-31
    @Test
    fun issue351_tc31_fieldStaysPinnedWhenResultsScroll() {
        val listState = LazyListState()
        setSearch(results("wood", List(40) { hit(it) }), listState = listState)

        rule.runOnIdle { runBlocking { listState.scrollToItem(2) } }

        rule.onAllNodesWithText(string(R.string.search_title)).assertCountEquals(0)
        rule.onNodeWithContentDescription(string(R.string.search_field_label)).assertIsDisplayed()
    }

    // TC-31
    @Test
    fun issue351_tc31_scrollingResultsClearsFieldFocus() {
        setSearch(results("wood", List(40) { hit(it) }))

        rule.onNodeWithContentDescription(string(R.string.search_field_label)).performClick()
        rule.onNodeWithContentDescription(string(R.string.search_field_label)).assertIsFocused()
        rule.onNode(hasScrollToIndexAction()).performTouchInput { swipeUp() }

        rule.onNodeWithContentDescription(string(R.string.search_field_label)).assertIsNotFocused()
    }

    /** Results for [query] whose Books section holds [hits] out of [total] matches. */
    private fun resultsOutOf(query: String, hits: List<LibraryHit>, total: Int) = SearchUIState(
        query = query,
        body = SearchBody.Results(
            query = query,
            sections = listOf(SearchSection(SearchSectionKind.Books, SearchMatches(hits, total))),
        ),
    )

    // TC-42
    @Test
    fun issue351_tc42_seeAllIsHiddenWhenEveryMatchIsShown() {
        setSearch(resultsOutOf("wood", List(5) { hit(it) }, total = 5))

        rule.onAllNodesWithContentDescription("See all", substring = true).assertCountEquals(0)
    }

    // TC-42
    @Test
    fun issue351_tc42_seeAllShowsWhenThereIsMore() {
        setSearch(resultsOutOf("wood", List(5) { hit(it) }, total = 9))

        rule.onNodeWithContentDescription("See all 9 books").assertIsDisplayed()
    }

    // TC-42
    @Test
    fun issue351_tc42_seeAllIsAnnouncedWithTheTotal() {
        setSearch(resultsOutOf("wood", List(5) { hit(it) }, total = 9))

        rule.onNodeWithContentDescription("See all 9 books").assert(hasClickAction())
    }

    // TC-42
    @Test
    fun issue351_tc42_seeAllIsAtLeast48dpTall() {
        setSearch(resultsOutOf("wood", List(5) { hit(it) }, total = 9))

        rule.onNodeWithContentDescription("See all 9 books").assertHeightIsAtLeast(48.dp)
    }

    // TC-42
    @Test
    fun issue351_tc42_seeAllOpensTheAnsweredQuery() {
        val opened = mutableListOf<SearchOpen>()
        setSearch(
            resultsOutOf("wood", List(5) { hit(it) }, total = 9).copy(query = "woods"),
            onOpen = { opened += it },
        )

        rule.onNodeWithContentDescription("See all 9 books").performClick()

        assertEquals(listOf<SearchOpen>(SearchOpen.Route(SearchSeeAllRoute(SearchSectionKind.Books, "wood"))), opened)
    }
}
