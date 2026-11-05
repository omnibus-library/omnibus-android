package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.center
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.feature.library.ContinueCard
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.feature.library.PositionLabel
import com.omnibus.omnibus.feature.library.RelativeAge
import com.omnibus.omnibus.feature.library.ResumeTarget
import com.omnibus.omnibus.feature.library.UnavailableBook
import com.omnibus.omnibus.ui.components.OfflinePillTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Callbacks, state changes, semantics and copy. LibraryScreenshotTest covers how the screen looks. */
class LibraryScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc64_rendersOneViewPerState() {
        val uiState = mutableStateOf(LibraryUIState(isInitialLoading = true))
        var retries = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
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
            uiState.value = LibraryUIState(error = CONNECT_UI_ERROR, hasLoaded = true)
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
    fun tc65_mastheadIsAHeadingAndAddButtonFires() {
        var adds = 0
        setLibrary(
            LibraryUIState(books = listOf(book(1L), book(2L))),
            onAddBooks = { adds++ },
        )

        rule.onNode(isHeading()).assertTextContains(LIBRARY_TITLE, substring = true)

        rule.onNodeWithContentDescription(ADD_LABEL).performClick()
        assertEquals(1, adds)
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
                Box(Modifier.fillMaxSize()) {
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
        // Scroll the footer (after the header and books) into view, whatever the screen size.
        rule.onNodeWithTag(LibraryTestTags.GRID).performScrollToIndex(FOOTER_INDEX)
        rule.waitForIdle()
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
    fun httpErrorShowsServerMessageOrFallsBackToStatusCode() {
        val uiState = mutableStateOf(
            LibraryUIState(error = LibraryUIError.Failed(LibraryError.Http(503, "Down for maintenance")), hasLoaded = true),
        )
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
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

        rule.onNodeWithText("Down for maintenance").assertIsDisplayed()

        rule.runOnIdle {
            uiState.value = LibraryUIState(error = LibraryUIError.Failed(LibraryError.Http(503, null)), hasLoaded = true)
        }
        rule.onNodeWithText("Server error (503).").assertIsDisplayed()
    }

    @Test
    fun tc100_snackbarShowsTransientErrorOnceAndIgnoresRecomposition() {
        val uiState = mutableStateOf(
            LibraryUIState(books = listOf(book(1L)), transientError = CONNECT_UI_ERROR),
        )
        val unrelated = mutableStateOf(0)
        var shown = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
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

    @Test
    fun tc144_headingNamesActiveCategory() {
        val uiState = mutableStateOf(LibraryUIState(books = listOf(book(1L))))
        setLibraryState(uiState)

        heading().assertTextEquals(ALL_BOOKS)

        rule.runOnIdle { uiState.value = uiState.value.copy(category = LibraryCategory.Ebooks) }
        heading().assertTextEquals("Ebooks").assertIsDisplayed()

        rule.runOnIdle { uiState.value = uiState.value.copy(category = LibraryCategory.Audiobooks) }
        heading().assertTextEquals("Audiobooks").assertIsDisplayed()
    }

    @Test
    fun tc146_filterButtonSemantics() {
        setLibrary(LibraryUIState(books = listOf(book(1L))))

        val button = rule.onNodeWithContentDescription(FILTER_LABEL)
        button.assertHasClickAction()
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun tc147_tapOpensMenu() {
        setLibrary(LibraryUIState(books = listOf(book(1L))))

        openMenu()

        LibraryCategory.entries.forEach { rule.onNodeWithTag(LibraryTestTags.menuCategory(it)).assertIsDisplayed() }
        LibrarySort.entries.forEach { rule.onNodeWithTag(LibraryTestTags.menuSort(it)).assertIsDisplayed() }
        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION).assertIsDisplayed()
    }

    @Test
    fun tc151_checkOnCurrentShowAndSortOnly() {
        val uiState = mutableStateOf(LibraryUIState(books = listOf(book(1L))))
        setLibraryState(uiState)
        openMenu()

        rule.onAllNodes(isSelected()).assertCountEquals(2)
        rule.onNodeWithTag(LibraryTestTags.menuCategory(LibraryCategory.All)).assertIsSelected()
        rule.onNodeWithTag(LibraryTestTags.menuSort(LibrarySort.NewestAdded)).assertIsSelected()
        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))

        rule.runOnIdle {
            uiState.value = uiState.value.copy(
                category = LibraryCategory.Audiobooks,
                sort = LibrarySort.Title,
                direction = SortDirection.Asc,
            )
        }

        rule.onAllNodes(isSelected()).assertCountEquals(2)
        rule.onNodeWithTag(LibraryTestTags.menuCategory(LibraryCategory.Audiobooks)).assertIsSelected()
        rule.onNodeWithTag(LibraryTestTags.menuSort(LibrarySort.Title)).assertIsSelected()
        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }

    @Test
    fun tc152_selectingItemInvokesCallbackAndClosesMenu() {
        val categories = mutableListOf<LibraryCategory>()
        val sorts = mutableListOf<LibrarySort>()
        var toggles = 0
        setLibrary(
            LibraryUIState(books = listOf(book(1L))),
            onSelectCategory = { categories += it },
            onSelectSort = { sorts += it },
            onToggleDirection = { toggles++ },
        )

        openMenu()
        rule.onNodeWithTag(LibraryTestTags.menuCategory(LibraryCategory.Ebooks)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(LibraryCategory.Ebooks), categories)
        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION).assertDoesNotExist()

        openMenu()
        rule.onNodeWithTag(LibraryTestTags.menuSort(LibrarySort.Author)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(LibrarySort.Author), sorts)
        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION).assertDoesNotExist()

        openMenu()
        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION).performClick()
        rule.waitForIdle()
        assertEquals(1, toggles)
        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION).assertDoesNotExist()
    }

    @Test
    fun tc153_directionRowShowsCurrentDirection() {
        val uiState = mutableStateOf(LibraryUIState(books = listOf(book(1L))))
        var toggles = 0
        setLibraryState(uiState, onToggleDirection = { toggles++ })

        openMenu()
        rule.onNodeWithText("Descending").assertIsDisplayed()
        rule.onNodeWithTag(LibraryTestTags.directionIcon(SortDirection.Desc), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(LibraryTestTags.directionIcon(SortDirection.Asc), useUnmergedTree = true)
            .assertDoesNotExist()

        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION).performClick()
        rule.waitForIdle()
        assertEquals(1, toggles)

        rule.runOnIdle { uiState.value = uiState.value.copy(direction = SortDirection.Asc) }
        openMenu()
        rule.onNodeWithText("Ascending").assertIsDisplayed()
        rule.onNodeWithTag(LibraryTestTags.directionIcon(SortDirection.Asc), useUnmergedTree = true).assertExists()
        rule.onNodeWithTag(LibraryTestTags.directionIcon(SortDirection.Desc), useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun tc155_scrollToTopFlagScrollsToIndexZero() {
        val uiState = mutableStateOf(LibraryUIState(books = (1L..20L).map { book(it) }))
        val gridState = LazyGridState()
        var scrolledToTop = 0
        setLibraryState(
            uiState,
            onScrolledToTop = {
                scrolledToTop++
                uiState.value = uiState.value.copy(scrollToTop = false)
            },
            gridState = gridState,
        )

        rule.onNodeWithTag(LibraryTestTags.GRID).performScrollToIndex(6)
        rule.waitForIdle()
        assertTrue("the grid scrolled away from the top", gridState.firstVisibleItemIndex > 0)

        rule.runOnIdle { uiState.value = uiState.value.copy(scrollToTop = true) }
        rule.waitForIdle()

        assertEquals(0, gridState.firstVisibleItemIndex)
        assertEquals(1, scrolledToTop)
    }

    @Test
    fun tc156_noScrollWithoutFlag() {
        val uiState = mutableStateOf(LibraryUIState(books = (1L..20L).map { book(it) }))
        val gridState = LazyGridState()
        var scrolledToTop = 0
        setLibraryState(uiState, onScrolledToTop = { scrolledToTop++ }, gridState = gridState)

        rule.onNodeWithTag(LibraryTestTags.GRID).performScrollToIndex(6)
        rule.waitForIdle()
        val scrolledIndex = gridState.firstVisibleItemIndex
        assertTrue("the grid scrolled away from the top", scrolledIndex > 0)

        // An unrelated change recomposes the screen, the way returning from a book does.
        rule.runOnIdle { uiState.value = uiState.value.copy(hasLoaded = true) }
        rule.waitForIdle()

        assertEquals(scrolledIndex, gridState.firstVisibleItemIndex)
        assertEquals(0, scrolledToTop)
    }

    @Test
    fun tc161_filteredEmptyStillOpensTheMenu() {
        setLibrary(LibraryUIState(hasLoaded = true, category = LibraryCategory.Ebooks))

        openMenu()
        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION).assertIsDisplayed()
    }

    @Test
    fun issue21_tc4_pillShowsOnlyWhileOffline() {
        val uiState = mutableStateOf(
            LibraryUIState(books = listOf(book(1L), book(2L)), hasLoaded = true, isOffline = true),
        )
        setLibraryState(uiState)

        rule.onNodeWithTag(OfflinePillTestTags.PILL, useUnmergedTree = true).assertIsDisplayed()

        rule.runOnIdle { uiState.value = uiState.value.copy(isOffline = false) }
        rule.waitForIdle()
        rule.onNodeWithTag(OfflinePillTestTags.PILL, useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription(ADD_LABEL, useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun issue22_tc33_carouselIsAbsentWithoutCards() {
        val cards = listOf(continueCard(1L), continueCard(2L), continueCard(3L))
        val uiState = mutableStateOf(
            LibraryUIState(books = listOf(book(1L), book(2L)), hasLoaded = true, continueCards = cards, continueSelectedId = cards.first().id),
        )
        setLibraryState(uiState)
        rule.onNodeWithTag(ContinueTestTags.CAROUSEL, useUnmergedTree = true).assertExists()

        rule.runOnIdle { uiState.value = uiState.value.copy(continueCards = emptyList(), continueSelectedId = null) }
        rule.waitForIdle()
        rule.onNodeWithTag(ContinueTestTags.CAROUSEL, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun issue22_tc35_readingAlertCopyIsExact() {
        setLibraryState(
            mutableStateOf(
                LibraryUIState(books = listOf(book(1L)), hasLoaded = true, unavailableBook = UnavailableBook("Annihilation", ProgressFormat.Reading)),
            ),
        )

        rule.onNodeWithText("Can't read offline").assertIsDisplayed()
        rule.onNodeWithText(READ_OFFLINE_MESSAGE).assertIsDisplayed()
        rule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun issue22_tc35_listeningAlertCopyIsExact() {
        setLibraryState(
            mutableStateOf(
                LibraryUIState(books = listOf(book(1L)), hasLoaded = true, unavailableBook = UnavailableBook("Annihilation", ProgressFormat.Listening)),
            ),
        )

        rule.onNodeWithText("Can't play offline").assertIsDisplayed()
        rule.onNodeWithText(PLAY_OFFLINE_MESSAGE).assertIsDisplayed()
        rule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun issue22_tc35_noAlertWithoutAnUnavailableBook() {
        setLibraryState(mutableStateOf(LibraryUIState(books = listOf(book(1L)), hasLoaded = true)))

        rule.onNodeWithText("OK").assertDoesNotExist()
    }

    @Test
    fun issue22_tc36_okAndBackDismissTheAlert() {
        var dismissals = 0
        val alert = UnavailableBook("Annihilation", ProgressFormat.Reading)
        val uiState = mutableStateOf(LibraryUIState(books = listOf(book(1L)), hasLoaded = true, unavailableBook = alert))
        setLibraryState(uiState, onUnavailableDismissed = { dismissals++ })

        rule.onNodeWithText("OK").performClick()
        rule.waitForIdle()
        assertEquals(1, dismissals)

        rule.runOnIdle { uiState.value = uiState.value.copy(unavailableBook = null) }
        rule.runOnIdle { uiState.value = uiState.value.copy(unavailableBook = alert) }
        rule.waitForIdle()
        rule.onNodeWithText("OK").assertIsDisplayed()
        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(2, dismissals)
    }

    @Test
    fun issue22_tc37_pendingResumeOpensTheRightScreenOnce() {
        val readers = mutableListOf<String>()
        val players = mutableListOf<Pair<String, Long?>>()
        var handled = 0
        val uiState = mutableStateOf(
            LibraryUIState(books = listOf(book(1L)), hasLoaded = true, pendingResume = ResumeTarget.Reader("u1")),
        )
        setLibraryState(
            uiState,
            onOpenReader = { readers += it },
            onOpenPlayer = { uuid, fileId -> players += uuid to fileId },
            onResumeHandled = { handled++ },
        )
        rule.waitForIdle()
        assertEquals(listOf("u1"), readers)
        assertEquals(1, handled)

        rule.runOnIdle { uiState.value = uiState.value.copy(pendingResume = ResumeTarget.Player("u2", 20L)) }
        rule.waitForIdle()
        assertEquals(listOf("u2" to 20L), players)

        rule.runOnIdle { uiState.value = uiState.value.copy(pendingResume = ResumeTarget.Player("u3", null)) }
        rule.waitForIdle()
        assertEquals(listOf("u2" to 20L, "u3" to null), players)
        assertEquals(listOf("u1"), readers)
    }

    private fun heading() = rule.onNodeWithTag(LibraryTestTags.HEADING, useUnmergedTree = true)

    private fun filterButton() = rule.onNodeWithTag(LibraryTestTags.FILTER_BUTTON)

    private fun openMenu() = filterButton().performClick()

    private fun setLibrary(
        uiState: LibraryUIState,
        onOpenBook: (String) -> Unit = {},
        onAddBooks: () -> Unit = {},
        onLoadMore: (Int) -> Unit = {},
        onRefresh: () -> Unit = {},
        onSelectCategory: (LibraryCategory) -> Unit = {},
        onSelectSort: (LibrarySort) -> Unit = {},
        onToggleDirection: () -> Unit = {},
        onScrolledToTop: () -> Unit = {},
        gridState: LazyGridState? = null,
    ) = setLibraryState(
        uiState = mutableStateOf(uiState),
        onOpenBook = onOpenBook,
        onAddBooks = onAddBooks,
        onLoadMore = onLoadMore,
        onRefresh = onRefresh,
        onSelectCategory = onSelectCategory,
        onSelectSort = onSelectSort,
        onToggleDirection = onToggleDirection,
        onScrolledToTop = onScrolledToTop,
        gridState = gridState,
    )

    /** Like [setLibrary], but reads the state each recomposition so a test can change it between steps. */
    private fun setLibraryState(
        uiState: State<LibraryUIState>,
        onOpenBook: (String) -> Unit = {},
        onAddBooks: () -> Unit = {},
        onLoadMore: (Int) -> Unit = {},
        onRefresh: () -> Unit = {},
        onSelectCategory: (LibraryCategory) -> Unit = {},
        onSelectSort: (LibrarySort) -> Unit = {},
        onToggleDirection: () -> Unit = {},
        onScrolledToTop: () -> Unit = {},
        gridState: LazyGridState? = null,
        onContinueSettled: (String) -> Unit = {},
        onOpenReader: (String) -> Unit = {},
        onOpenPlayer: (String, Long?) -> Unit = { _, _ -> },
        onResumeHandled: () -> Unit = {},
        onUnavailableDismissed: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    val continueState = rememberContinueCarouselState(pageCount = { uiState.value.continueCards.size })
                    LibraryScreenContent(
                        uiState = uiState.value,
                        onRetry = {},
                        onRefresh = onRefresh,
                        onLoadMore = onLoadMore,
                        onOpenBook = onOpenBook,
                        onAddBooks = onAddBooks,
                        onTransientErrorShown = {},
                        bottomInset = 0.dp,
                        onSelectCategory = onSelectCategory,
                        onSelectSort = onSelectSort,
                        onToggleDirection = onToggleDirection,
                        onScrolledToTop = onScrolledToTop,
                        gridState = gridState ?: rememberLazyGridState(),
                        continueState = continueState,
                        onContinueSettled = onContinueSettled,
                        onOpenReader = onOpenReader,
                        onOpenPlayer = onOpenPlayer,
                        onResumeHandled = onResumeHandled,
                        onUnavailableDismissed = onUnavailableDismissed,
                    )
                }
            }
        }
    }

    private companion object {
        const val LOADING_LABEL = "Loading your library"
        const val ERROR_TITLE = "Couldn't load this"
        val CONNECT_UI_ERROR = LibraryUIError.Failed(LibraryError.Unreachable)
        const val CONNECT_ERROR = "Could not connect to the server."
        const val TRY_AGAIN = "Try again"
        const val EMPTY_TITLE = "No books yet"
        const val EMPTY_KICKER = "YOUR LIBRARY"
        const val LIBRARY_TITLE = "Library"
        const val ALL_BOOKS = "All books"
        const val ADD_LABEL = "Add books by upload or barcode scan"
        const val FILTER_LABEL = "Filter and sort"

        /** Half a dp of slack, to absorb sub-pixel rounding in the measured bounds. */
        val HALF_DP = 0.5.dp

        const val READ_OFFLINE_MESSAGE = "“Annihilation” isn't downloaded to this device, and the server can't be reached. " +
            "Connect to the internet to stream it, or download the book while you're online to read it anywhere."
        const val PLAY_OFFLINE_MESSAGE = "“Annihilation” isn't downloaded to this device, and the server can't be reached. " +
            "Connect to the internet to stream it, or download the audiobook while you're online to read it anywhere."

        /** A reading card for book [id], with a half-read bar. */
        fun continueCard(id: Long) = ContinueCard(
            id = "c$id:epub",
            book = book(id),
            medium = ProgressFormat.Reading,
            fraction = 0.5,
            position = PositionLabel.PercentRead(50),
            lastOpened = RelativeAge.Hours(2),
            resumeFileId = null,
        )

        /** Grid index of the footer in tc84: one header item, six books, then the footer. */
        const val FOOTER_INDEX = 7

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
