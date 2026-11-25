package com.omnibus.omnibus.feature.series.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.series.SeriesDetailsUIState
import com.omnibus.omnibus.feature.series.SeriesLoadFailure
import com.omnibus.omnibus.feature.series.SeriesRefreshFailure
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Callbacks, semantics and the refresh snackbar of the Series Details page, on hand-built state. The page's look is
 * covered by SeriesDetailsScreenshotTest. Nothing here fixes a window size: the window is filled, and the lists are
 * short enough to compose on any phone.
 */
class SeriesDetailsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue349_tc20_showsTheSeriesNameAsAHeadingWhileLoading() {
        setScreen(uiState = SeriesDetailsUIState(seriesId = 1L, name = "Lavondyss"))

        rule.onNodeWithTag(SeriesDetailsTestTags.TITLE).assert(isHeading()).assertTextEquals("Lavondyss")
    }

    @Test
    fun issue349_tc20_showsTheSeriesNameAsAHeadingWhenLoaded() {
        setScreen(uiState = loaded(name = "Lavondyss", books = listOf(book("1", index = "1"))))

        rule.onNodeWithTag(SeriesDetailsTestTags.TITLE).assert(isHeading()).assertTextEquals("Lavondyss")
    }

    @Test
    fun issue349_tc20_backInvokesOnBack() {
        var backed = false
        setScreen(
            uiState = SeriesDetailsUIState(seriesId = 1L, name = "Lavondyss"),
            onBack = { backed = true },
        )

        rule.onNodeWithTag(SeriesDetailsTestTags.BACK).performClick()

        assertTrue(backed)
    }

    @Test
    fun issue349_tc20_backIsAtLeast48dpTall() {
        setScreen(uiState = SeriesDetailsUIState(seriesId = 1L, name = "Lavondyss"))

        rule.onNodeWithTag(SeriesDetailsTestTags.BACK).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun issue349_tc21_countsTheBooksWithTheAuthorFirst() {
        val books = List(3) { book("${it + 1}", index = "${it + 1}") }
        setScreen(uiState = loaded(books = books))

        rule.onNodeWithTag(SeriesDetailsTestTags.COUNT_LINE).assertTextEquals("Robert Holdstock · 3 books")
    }

    @Test
    fun issue349_tc21_saysBookInTheSingular() {
        setScreen(uiState = loaded(books = listOf(book("1", index = "1"))))

        rule.onNodeWithTag(SeriesDetailsTestTags.COUNT_LINE).assertTextEquals("Robert Holdstock · 1 book")
    }

    @Test
    fun issue349_tc21_dropsTheAuthorWhenThereIsNone() {
        setScreen(uiState = loaded(books = listOf(book("1", index = "1", creators = emptyList()))))

        rule.onNodeWithTag(SeriesDetailsTestTags.COUNT_LINE).assertTextEquals("1 book")
    }

    @Test
    fun issue349_tc21_hasNoCountLineWhileLoading() {
        setScreen(uiState = SeriesDetailsUIState(seriesId = 1L, name = "Lavondyss"))

        rule.onAllNodesWithTag(SeriesDetailsTestTags.COUNT_LINE).assertCountEquals(0)
    }

    @Test
    fun issue349_tc22_aBookRowOpensThatBook() {
        val books = listOf(book("first", index = "1"), book("second", index = "2"))
        var opened: String? = null
        setScreen(uiState = loaded(books = books), onOpenBook = { opened = it })

        rule.onNodeWithTag(SeriesDetailsTestTags.book("second")).performClick()

        rule.waitUntil { opened != null }
        assertEquals("second", opened)
    }

    @Test
    fun issue349_tc23_aPlaceholderRowHasNoClickAction() {
        setScreen(uiState = loaded(books = listOf(book("1", index = "1"), book("3", index = "3"))))

        rule.onNodeWithTag(SeriesDetailsTestTags.missing(2)).assertHasNoClickAction()
        rule.onNodeWithTag(SeriesDetailsTestTags.missing(2))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }

    @Test
    fun issue349_tc23_aRangeRowHasNoClickAction() {
        setScreen(uiState = loaded(books = listOf(book("1", index = "1"), book("10", index = "10"))))

        rule.onNodeWithTag(SeriesDetailsTestTags.missing(2)).assertHasNoClickAction()
        rule.onNodeWithTag(SeriesDetailsTestTags.missing(2))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick))
    }

    @Test
    fun issue349_tc24_announcesABookWithNumberTitleAuthorsAndFormats() {
        setScreen(uiState = loaded(books = listOf(book("2", index = "2", title = "Lavondyss", formats = listOf("epub", "m4b")))))

        rule.onNodeWithTag(SeriesDetailsTestTags.book("2")).assert(
            hasContentDescriptionExactly("Book 2, Lavondyss, Robert Holdstock, ebook, audio"),
        )
    }

    @Test
    fun issue349_tc24_announcesABookWithoutANumberWithoutTheNumber() {
        setScreen(uiState = loaded(books = listOf(book("2", index = null, title = "Lavondyss", formats = emptyList()))))

        rule.onNodeWithTag(SeriesDetailsTestTags.book("2")).assert(
            hasContentDescriptionExactly("Lavondyss, Robert Holdstock"),
        )
    }

    @Test
    fun issue349_tc24_announcesASingleMissingNumber() {
        setScreen(uiState = loaded(books = listOf(book("1", index = "1"), book("3", index = "3"))))

        rule.onNodeWithTag(SeriesDetailsTestTags.missing(2)).assert(
            hasContentDescriptionExactly("Book 2, missing from this series"),
        )
    }

    @Test
    fun issue349_tc24_announcesARunOfMissingNumbers() {
        setScreen(uiState = loaded(books = listOf(book("1", index = "1"), book("10", index = "10"))))

        rule.onNodeWithTag(SeriesDetailsTestTags.missing(2)).assert(
            hasContentDescriptionExactly("Books 2 to 9, missing from this series"),
        )
    }

    @Test
    fun issue349_tc24_bookRowsAreAtLeast48dpTall() {
        setScreen(uiState = loaded(books = listOf(book("1", index = "1"))))

        rule.onNodeWithTag(SeriesDetailsTestTags.book("1")).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun issue349_tc25_loadingShowsItsLabelAndNoAction() {
        setScreen(uiState = SeriesDetailsUIState(seriesId = 1L, name = "Lavondyss"))

        rule.onNodeWithText("Loading").assertIsDisplayed()
        rule.onAllNodesWithText("Try again").assertCountEquals(0)
    }

    @Test
    fun issue349_tc25_emptyShowsTheSeriesNameInItsBody() {
        setScreen(uiState = SeriesDetailsUIState(seriesId = 1L, name = "Lavondyss", books = emptyList()))

        rule.onNodeWithText("No books here").assertIsDisplayed()
        rule.onNodeWithText("No books in your library are in \"Lavondyss\".").assertIsDisplayed()
        rule.onAllNodesWithText("Try again").assertCountEquals(0)
    }

    @Test
    fun issue349_tc25_notFoundShowsItsCopyAndNoAction() {
        setScreen(uiState = SeriesDetailsUIState(seriesId = 1L, name = "Lavondyss", notFound = true))

        rule.onNodeWithText("Series not found").assertIsDisplayed()
        rule.onNodeWithText("This series isn't in your library any more.").assertIsDisplayed()
        rule.onAllNodesWithText("Try again").assertCountEquals(0)
    }

    @Test
    fun issue349_tc25_unreachableShowsItsCopy() {
        setScreen(uiState = failed(SeriesLoadFailure.Unreachable))

        rule.onNodeWithText("Can't reach your server").assertIsDisplayed()
        rule.onNodeWithText("Check your connection, then try again.").assertIsDisplayed()
        rule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun issue349_tc25_wentWrongShowsItsCopy() {
        setScreen(uiState = failed(SeriesLoadFailure.WentWrong))

        rule.onNodeWithText("Series isn't loading").assertIsDisplayed()
        rule.onNodeWithText("Your server couldn't load this series. Try again in a moment.").assertIsDisplayed()
        rule.onNodeWithText("Try again").assertIsDisplayed()
    }

    @Test
    fun issue349_tc25_tryAgainInvokesOnRetry() {
        var retried = false
        setScreen(uiState = failed(SeriesLoadFailure.Unreachable), onRetry = { retried = true })

        rule.onNodeWithText("Try again").performClick()

        assertTrue(retried)
    }

    @Test
    fun issue349_tc25_tryAgainIsAtLeast48dpTall() {
        setScreen(uiState = failed(SeriesLoadFailure.WentWrong))

        val height = rule.onNodeWithText("Try again").fetchSemanticsNode().touchBoundsInRoot.height
        assertTrue(height >= with(rule.density) { 48.dp.toPx() })
    }

    @Test
    fun issue349_tc26_unreachableRefreshShowsASnackbarWithRetry() {
        var shown = false
        setScreen(
            uiState = loaded(books = listOf(book("1", index = "1")), refreshFailure = SeriesRefreshFailure.Unreachable),
            onRefreshFailureShown = { shown = true },
        )

        rule.waitUntil { shown }
        rule.onNodeWithText("Can't reach your server").assertIsDisplayed()
        rule.onNodeWithText("Retry").assertIsDisplayed()
        rule.onNodeWithTag(SeriesDetailsTestTags.book("1")).assertIsDisplayed()
    }

    @Test
    fun issue349_tc26_otherRefreshFailureShowsItsSnackbar() {
        setScreen(
            uiState = loaded(books = listOf(book("1", index = "1")), refreshFailure = SeriesRefreshFailure.WentWrong),
        )

        waitForText("Series isn't loading")
        rule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun issue349_tc26_retryInTheSnackbarInvokesOnRefresh() {
        var refreshed = false
        setScreen(
            uiState = loaded(books = listOf(book("1", index = "1")), refreshFailure = SeriesRefreshFailure.Unreachable),
            onRefresh = { refreshed = true },
        )

        waitForText("Retry")
        rule.onNodeWithText("Retry").performClick()

        rule.waitUntil { refreshed }
        assertTrue(refreshed)
    }

    @Test
    fun issue349_tc27_swipingDownRefreshesWhenLoaded() {
        var refreshed = false
        setScreen(uiState = loaded(books = listOf(book("1", index = "1"))), onRefresh = { refreshed = true })

        rule.onNodeWithTag(SeriesDetailsTestTags.LIST).performTouchInput { swipeDown(startY = top + 1f, endY = bottom) }

        rule.waitUntil(timeoutMillis = 5_000) { refreshed }
        assertTrue(refreshed)
    }

    @Test
    fun issue349_tc27_swipingDownRefreshesWhenEmpty() {
        var refreshed = false
        setScreen(
            uiState = SeriesDetailsUIState(seriesId = 1L, name = "Lavondyss", books = emptyList()),
            onRefresh = { refreshed = true },
        )

        rule.onNodeWithTag(SeriesDetailsTestTags.LIST).performTouchInput { swipeDown(startY = top + 1f, endY = bottom) }

        rule.waitUntil(timeoutMillis = 5_000) { refreshed }
        assertTrue(refreshed)
    }

    /** Waits until a node with [text] is in the tree, e.g. a snackbar that appears after composition. */
    private fun waitForText(text: String) {
        rule.waitUntil { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun setScreen(
        uiState: SeriesDetailsUIState,
        onBack: () -> Unit = {},
        onOpenBook: (String) -> Unit = {},
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
        onRefreshFailureShown: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    SeriesDetailsContent(
                        uiState = uiState,
                        onBack = onBack,
                        onOpenBook = onOpenBook,
                        onRefresh = onRefresh,
                        onRetry = onRetry,
                        onRefreshFailureShown = onRefreshFailureShown,
                        windowWidth = 390.dp,
                    )
                }
            }
        }
    }

    private fun loaded(
        name: String = "Lavondyss",
        books: List<Book>,
        refreshFailure: SeriesRefreshFailure? = null,
    ) = SeriesDetailsUIState(seriesId = 1L, name = name, books = books, refreshFailure = refreshFailure)

    private fun failed(failure: SeriesLoadFailure) =
        SeriesDetailsUIState(seriesId = 1L, name = "Lavondyss", loadFailure = failure)

    private fun book(
        uuid: String,
        index: String?,
        title: String = "Book $uuid",
        formats: List<String> = listOf("epub"),
        creators: List<Creator> = listOf(Creator(name = "Robert Holdstock", role = "aut", fileAs = null, id = null)),
    ): Book = Book(
        id = uuid.hashCode().toLong(),
        uniqueIdentifier = uuid,
        filename = "$uuid.epub",
        title = title,
        creators = creators,
        coverUrl = null,
        accent = null,
        formats = formats,
        series = "Lavondyss",
        seriesIndex = index,
        hasPhysical = false,
        addedAt = null,
        lastInteractedAt = null,
        bookFiles = emptyList(),
    )
}
