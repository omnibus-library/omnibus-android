package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.bottomCenter
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc64_rendersOneViewPerState() {
        val uiState = mutableStateOf(LibraryUIState(isInitialLoading = true))
        var retries = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(390.dp, 800.dp)) {
                    LibraryScreenContent(
                        uiState = uiState.value,
                        onRetry = { retries++ },
                        onRefresh = {},
                        onLoadMore = {},
                        onOpenBook = {},
                        onAddBooks = {},
                        onTransientErrorShown = {},
                        bottomInset = 0.dp,
                    )
                }
            }
        }

        rule.onNodeWithText(LOADING_LABEL).assertIsDisplayed()
        rule.onNodeWithTag(LibraryTestTags.GRID).assertDoesNotExist()

        rule.runOnIdle {
            uiState.value = LibraryUIState(error = CONNECT_ERROR, hasLoaded = true)
        }
        rule.onNodeWithText(ERROR_TITLE).assertIsDisplayed()
        rule.onNodeWithText(CONNECT_ERROR).assertIsDisplayed()
        rule.onNodeWithText(LOADING_LABEL).assertDoesNotExist()
        rule.onNodeWithText(TRY_AGAIN).performClick()
        assertEquals(1, retries)

        rule.runOnIdle {
            uiState.value = LibraryUIState(hasLoaded = true)
        }
        rule.onNodeWithText(EMPTY_TITLE).assertIsDisplayed()
        rule.onNodeWithText(EMPTY_KICKER).assertIsDisplayed()
        rule.onNodeWithText(ERROR_TITLE).assertDoesNotExist()

        rule.runOnIdle {
            uiState.value = LibraryUIState(books = listOf(book(1L), book(2L)), hasLoaded = true)
        }
        rule.onNodeWithTag(LibraryTestTags.GRID).assertIsDisplayed()
        rule.onNodeWithText(EMPTY_TITLE).assertDoesNotExist()
        rule.onNodeWithText(LOADING_LABEL).assertDoesNotExist()
    }

    @Test
    fun tc65_mastheadHeadingAndGridAppearInOrderAndAddButtonFires() {
        var adds = 0
        setLibrary(
            LibraryUIState(books = listOf(book(1L), book(2L))),
            onAddBooks = { adds++ },
        )

        rule.onNode(isHeading()).assertTextContains(LIBRARY_TITLE, substring = true)
        rule.onNodeWithText(ALL_BOOKS).assertIsDisplayed()
        rule.onNodeWithTag(LibraryTestTags.cover(1L), useUnmergedTree = true).assertIsDisplayed()

        val masthead = rule.onNode(isHeading()).getUnclippedBoundsInRoot()
        val heading = rule.onNodeWithTag(LibraryTestTags.HEADING, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val cover = rule.onNodeWithTag(LibraryTestTags.cover(1L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue("masthead sits above the heading", masthead.bottom <= heading.top + HALF_DP)
        assertTrue("heading sits above the covers", heading.bottom <= cover.top + HALF_DP)

        rule.onNodeWithContentDescription(ADD_LABEL).performClick()
        assertEquals(1, adds)
    }

    @Test
    fun tc66_headingIsInsetTwentyAndTwelveDpAboveTheGrid() {
        setLibrary(LibraryUIState(books = listOf(book(1L))))

        val grid = rule.onNodeWithTag(LibraryTestTags.GRID).getUnclippedBoundsInRoot()
        val heading = rule.onNodeWithTag(LibraryTestTags.HEADING, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val cover = rule.onNodeWithTag(LibraryTestTags.cover(1L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertNear(20.dp, heading.left - grid.left, "heading starts at the 20 dp inset")
        assertNear(12.dp, cover.top - heading.bottom, "12 dp between heading and grid")
    }

    @Test
    fun tc70_gridIsTwoColumnsOfAboutOneSixtySevenDpWithGapsAndTopAlignment() {
        setLibrary(LibraryUIState(books = (1L..5L).map { book(it) }))

        val grid = rule.onNodeWithTag(LibraryTestTags.GRID).getUnclippedBoundsInRoot()
        val cell1 = rule.onNodeWithTag(LibraryTestTags.cell(1L)).getUnclippedBoundsInRoot()
        val cell2 = rule.onNodeWithTag(LibraryTestTags.cell(2L)).getUnclippedBoundsInRoot()
        val cover1 = rule.onNodeWithTag(LibraryTestTags.cover(1L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val cover2 = rule.onNodeWithTag(LibraryTestTags.cover(2L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val cover3 = rule.onNodeWithTag(LibraryTestTags.cover(3L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertNear(167.dp, cell1.right - cell1.left, "cell width", tolerance = 1.0)
        assertNear(16.dp, cell2.left - cell1.right, "16 dp column spacing")
        assertNear(26.dp, cover3.top - cell1.bottom, "26 dp row gap")
        assertNear(20.dp, cell1.left - grid.left, "20 dp inset")
        assertNear(0.dp, cover2.top - cover1.top, "items are top-aligned")
    }

    @Test
    fun tc71_cellShowsTwoLineEllipsizedTitleAndOneLineAuthorWithGaps() {
        val title = "The Remarkable Adventures of an Extraordinarily Long Title That Keeps Going"
        val author = "A Long Author Name"
        setLibrary(LibraryUIState(books = listOf(book(7L, title = title, author = author))))

        rule.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText(author, useUnmergedTree = true).assertIsDisplayed()

        val titleLayout = rule.textLayoutOf(LibraryTestTags.title(7L))
        assertEquals(2, titleLayout.lineCount)
        assertTrue("title is ellipsized on its last line", titleLayout.isLineEllipsized(1))

        val cover = rule.onNodeWithTag(LibraryTestTags.cover(7L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val titleBounds = rule.onNodeWithTag(LibraryTestTags.title(7L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val authorBounds = rule.onNodeWithText(author, useUnmergedTree = true).getUnclippedBoundsInRoot()

        assertNear(8.dp, titleBounds.top - cover.bottom, "8 dp cover to title")
        assertNear(1.dp, authorBounds.top - titleBounds.bottom, "1 dp title to author")
    }

    @Test
    fun tc72_coversShareTopEdgeAndKeepTwoByThreeAcrossTitleLengths() {
        val twoLineTitle = "A Memory Called Empire and the Long Road Home Again"
        setLibrary(
            LibraryUIState(books = listOf(book(1L, title = "Piranesi"), book(2L, title = twoLineTitle))),
        )

        assertEquals(2, rule.textLayoutOf(LibraryTestTags.title(2L)).lineCount)

        val first = rule.onNodeWithTag(LibraryTestTags.cover(1L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val second = rule.onNodeWithTag(LibraryTestTags.cover(2L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertNear(0.dp, second.top - first.top, "covers share their top edge")
        assertEquals(2f / 3f, (first.right - first.left) / (first.bottom - first.top), 0.01f)
        assertEquals(2f / 3f, (second.right - second.left) / (second.bottom - second.top), 0.01f)
    }

    @Test
    fun tc73_blankTitleAndNoCreatorsFallBackToFilenameAndUnknownAuthor() {
        setLibrary(
            LibraryUIState(
                books = listOf(book(9L, title = "   ", author = null, filename = "fallback.epub")),
            ),
        )

        rule.onNodeWithText("fallback.epub", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Unknown author", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun tc74_captionGapAndCoverAllOpenTheBookAsOneTapTarget() {
        val opened = mutableListOf<String>()
        setLibrary(
            LibraryUIState(books = listOf(book(1L, title = "Title One", author = "Author One"))),
            onOpenBook = { opened += it },
        )

        val cellTag = LibraryTestTags.cell(1L)
        val cell = rule.onNodeWithTag(cellTag).getUnclippedBoundsInRoot()
        val cover = rule.onNodeWithTag(LibraryTestTags.cover(1L), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val gapY = with(rule.density) { (cover.bottom - cell.top + 4.dp).toPx() }

        rule.onNodeWithTag(cellTag).performTouchInput { click(bottomCenter) }
        rule.onNodeWithTag(cellTag).performTouchInput { click(Offset(centerX, gapY)) }
        rule.onNodeWithTag(LibraryTestTags.cover(1L), useUnmergedTree = true)
            .performTouchInput { click(center) }

        assertEquals(listOf("uuid-1", "uuid-1", "uuid-1"), opened)

        val mergedTexts = rule.onNodeWithTag(cellTag).fetchSemanticsNode()
            .config[SemanticsProperties.Text].map { it.text }
        assertTrue("merged cell includes the title", mergedTexts.any { "Title One" in it })
        assertTrue("merged cell includes the author", mergedTexts.any { "Author One" in it })
    }

    @Test
    fun tc83_reportsLastVisibleBookIndexNotGridIndex() {
        val reported = mutableListOf<Int>()
        setLibrary(
            LibraryUIState(books = (1L..40L).map { book(it) }),
            height = 600.dp,
            onLoadMore = { reported += it },
        )

        rule.onNodeWithTag(LibraryTestTags.GRID).performScrollToIndex(40)
        rule.waitForIdle()

        assertEquals(39, reported.last())
    }

    @Test
    fun tc84_footerSpinnerShowsOnlyWhileLoadingMoreBelowTheLastRow() {
        val uiState = mutableStateOf(LibraryUIState(books = (1L..6L).map { book(it) }))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(390.dp, 800.dp)) {
                    LibraryScreenContent(
                        uiState = uiState.value,
                        onRetry = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onOpenBook = {},
                        onAddBooks = {},
                        onTransientErrorShown = {},
                        bottomInset = 0.dp,
                    )
                }
            }
        }

        rule.onNodeWithTag(LibraryTestTags.FOOTER_SPINNER).assertDoesNotExist()

        rule.runOnIdle { uiState.value = uiState.value.copy(isLoadingMore = true) }
        val spinner = rule.onNodeWithTag(LibraryTestTags.FOOTER_SPINNER).getUnclippedBoundsInRoot()
        val lastCell = rule.onNodeWithTag(LibraryTestTags.cell(6L)).getUnclippedBoundsInRoot()
        assertTrue("spinner sits below the last row", spinner.top >= lastCell.bottom - HALF_DP)

        rule.runOnIdle { uiState.value = uiState.value.copy(isLoadingMore = false) }
        rule.onNodeWithTag(LibraryTestTags.FOOTER_SPINNER).assertDoesNotExist()
    }

    @Test
    fun tc88_swipeDownOnGridRequestsRefresh() {
        var refreshes = 0
        setLibrary(
            LibraryUIState(books = listOf(book(1L), book(2L))),
            onRefresh = { refreshes++ },
        )

        rule.onNodeWithTag(LibraryTestTags.GRID).performTouchInput { swipeDown() }
        rule.waitForIdle()

        assertEquals(1, refreshes)
        rule.onNodeWithTag(LibraryTestTags.GRID).assertIsDisplayed()
    }

    @Test
    fun tc94_bottomPaddingIsFortyPlusInsetAboveTheLastRow() {
        val inset = mutableStateOf(24.dp)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(390.dp, 600.dp)) {
                    LibraryScreenContent(
                        uiState = LibraryUIState(books = (1L..30L).map { book(it) }),
                        onRetry = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onOpenBook = {},
                        onAddBooks = {},
                        onTransientErrorShown = {},
                        bottomInset = inset.value,
                    )
                }
            }
        }

        rule.onNodeWithTag(LibraryTestTags.GRID).performScrollToIndex(30)
        rule.waitForIdle()
        assertNear(64.dp, bottomGap(LibraryTestTags.cell(30L)), "40 dp plus 24 dp inset", tolerance = 1.0)

        rule.runOnIdle { inset.value = 0.dp }
        rule.onNodeWithTag(LibraryTestTags.GRID).performScrollToIndex(30)
        rule.waitForIdle()
        assertNear(40.dp, bottomGap(LibraryTestTags.cell(30L)), "40 dp with no inset", tolerance = 1.0)
    }

    @Test
    fun tc100_snackbarShowsTransientErrorOnceAndIgnoresRecomposition() {
        val uiState = mutableStateOf(
            LibraryUIState(books = listOf(book(1L)), transientError = CONNECT_ERROR),
        )
        val unrelated = mutableStateOf(0)
        var shown = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(390.dp, 800.dp)) {
                    LibraryScreenContent(
                        uiState = uiState.value,
                        onRetry = {},
                        onRefresh = {},
                        onLoadMore = {},
                        onOpenBook = {},
                        onAddBooks = {},
                        onTransientErrorShown = {
                            shown++
                            uiState.value = uiState.value.copy(transientError = null)
                        },
                        bottomInset = 0.dp,
                    )
                    Text("ticks ${unrelated.value}")
                }
            }
        }

        rule.onNodeWithText(CONNECT_ERROR).assertIsDisplayed()
        assertEquals(1, shown)

        rule.runOnIdle { unrelated.value++ }
        rule.waitForIdle()

        rule.onAllNodesWithText(CONNECT_ERROR).assertCountEquals(1)
        assertEquals(1, shown)
    }

    private fun setLibrary(
        uiState: LibraryUIState,
        height: Dp = 800.dp,
        onOpenBook: (String) -> Unit = {},
        onAddBooks: () -> Unit = {},
        onLoadMore: (Int) -> Unit = {},
        onRefresh: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(390.dp, height)) {
                    LibraryScreenContent(
                        uiState = uiState,
                        onRetry = {},
                        onRefresh = onRefresh,
                        onLoadMore = onLoadMore,
                        onOpenBook = onOpenBook,
                        onAddBooks = onAddBooks,
                        onTransientErrorShown = {},
                        bottomInset = 0.dp,
                    )
                }
            }
        }
    }

    /** Distance from the grid's bottom edge to the bottom of [cellTag], in dp. */
    private fun bottomGap(cellTag: String): Dp {
        val grid = rule.onNodeWithTag(LibraryTestTags.GRID).getUnclippedBoundsInRoot()
        val cell = rule.onNodeWithTag(cellTag).getUnclippedBoundsInRoot()
        return grid.bottom - cell.bottom
    }

    private fun ComposeContentTestRule.textLayoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    private companion object {
        const val LOADING_LABEL = "Loading your library"
        const val ERROR_TITLE = "Couldn't load this"
        const val CONNECT_ERROR = "Could not connect to the server."
        const val TRY_AGAIN = "Try again"
        const val EMPTY_TITLE = "No books yet"
        const val EMPTY_KICKER = "YOUR LIBRARY"
        const val LIBRARY_TITLE = "Library"
        const val ALL_BOOKS = "All books"
        const val ADD_LABEL = "Add books by upload or barcode scan"

        /** Half a dp of slack, to absorb sub-pixel rounding in the measured bounds. */
        val HALF_DP = 0.5.dp

        fun assertNear(expected: Dp, actual: Dp, message: String, tolerance: Double = HALF_DP.value.toDouble()) {
            assertEquals(message, expected.value.toDouble(), actual.value.toDouble(), tolerance)
        }

        /** A book with a uuid of `uuid-<id>` and no cover, so no image loads in these tests. */
        fun book(
            id: Long,
            title: String? = "Book $id",
            author: String? = "Author $id",
            filename: String = "book-$id.epub",
        ) = Book(
            id = id,
            uniqueIdentifier = "uuid-$id",
            filename = filename,
            title = title,
            creators = author?.let { listOf(Creator(it, null, null, null)) }.orEmpty(),
            coverUrl = null,
            accent = null,
            formats = listOf("epub"),
            series = null,
            seriesIndex = null,
            hasPhysical = false,
            addedAt = null,
            lastInteractedAt = null,
            bookFiles = emptyList(),
        )
    }
}
