package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isSelected
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.library.LibraryUIState
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
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
                Box(Modifier.requiredWidth(390.dp).height(800.dp)) {
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

        // The generated cover plate draws the title too, so address the caption by its tag.
        rule.onNodeWithTag(LibraryTestTags.title(7L), useUnmergedTree = true).assertIsDisplayed()
            .assertTextContains(title)
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

        // The generated cover plate draws the title too, so address the caption by its tag.
        rule.onNodeWithTag(LibraryTestTags.title(9L), useUnmergedTree = true).assertIsDisplayed()
            .assertTextContains("fallback.epub")
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
                Box(Modifier.requiredWidth(390.dp).height(800.dp)) {
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
        // Six books fill more than the 800 dp viewport, so scroll the footer (after the header and books) into view.
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
    fun tc94_bottomPaddingIsFortyPlusInsetAboveTheLastRow() {
        val inset = mutableStateOf(24.dp)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.requiredWidth(390.dp).height(600.dp)) {
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
    fun httpErrorShowsServerMessageOrFallsBackToStatusCode() {
        val uiState = mutableStateOf(
            LibraryUIState(error = LibraryUIError.Failed(LibraryError.Http(503, "Down for maintenance")), hasLoaded = true),
        )
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.requiredWidth(390.dp).height(800.dp)) {
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
                Box(Modifier.requiredWidth(390.dp).height(800.dp)) {
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
    fun tc145_headingRowLayout() {
        val uiState = LibraryUIState(books = listOf(book(1L)))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                // Large font scale, so the heading has to share the row with the button.
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.requiredWidth(390.dp).height(800.dp)) {
                        LibraryScreenContent(
                            uiState = uiState,
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
        }

        val grid = rule.onNodeWithTag(LibraryTestTags.GRID).getUnclippedBoundsInRoot()
        val heading = heading().getUnclippedBoundsInRoot()
        val button = filterButton().getUnclippedBoundsInRoot()

        assertNear(20.dp, heading.left - grid.left, "heading starts at the 20 dp inset")
        assertNear(20.dp, grid.right - button.right, "button sits at the 20 dp trailing inset")
        assertTrue("at least 8 dp between the heading and the button", button.left - heading.right >= 8.dp - HALF_DP)
        val headingCentre = (heading.top + heading.bottom) / 2
        val buttonCentre = (button.top + button.bottom) / 2
        assertTrue("heading and button are vertically centred", abs((headingCentre - buttonCentre).value) <= 1f)
    }

    @Test
    fun tc146_filterButtonSemantics() {
        setLibrary(LibraryUIState(books = listOf(book(1L))))

        val button = rule.onNodeWithContentDescription(FILTER_LABEL)
        button.assertHasClickAction()
        button.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        val bounds = filterButton().getUnclippedBoundsInRoot()
        assertNear(32.dp, bounds.right - bounds.left, "the button is 32 dp wide")
        assertNear(32.dp, bounds.bottom - bounds.top, "the button is 32 dp tall")
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
    fun tc148_buttonColourFollowsFilteredState() {
        val uiState = mutableStateOf(LibraryUIState(books = listOf(book(1L))))
        setLibraryState(uiState)
        rule.waitForIdle()

        assertColourNear(OmnibusPalette.Atrium.bg2, filterButtonFill(), "default fill")

        rule.runOnIdle { uiState.value = uiState.value.copy(category = LibraryCategory.Ebooks) }
        rule.waitForIdle()

        assertColourNear(OmnibusPalette.Atrium.accent, filterButtonFill(), "filtered fill")
    }

    @Test
    fun tc149_buttonAccentForEachFilteredKind() {
        val base = LibraryUIState(books = listOf(book(1L)))
        val uiState = mutableStateOf(base)
        setLibraryState(uiState)
        rule.waitForIdle()
        assertColourNear(OmnibusPalette.Atrium.bg2, filterButtonFill(), "default view")

        listOf(
            "Ebooks" to base.copy(category = LibraryCategory.Ebooks),
            "Title sort" to base.copy(sort = LibrarySort.Title),
            "ascending" to base.copy(direction = SortDirection.Asc),
        ).forEach { (name, state) ->
            rule.runOnIdle { uiState.value = state }
            rule.waitForIdle()
            assertColourNear(OmnibusPalette.Atrium.accent, filterButtonFill(), name)
        }

        rule.runOnIdle { uiState.value = base }
        rule.waitForIdle()
        assertColourNear(OmnibusPalette.Atrium.bg2, filterButtonFill(), "back to the default view")
    }

    @Test
    fun tc150_menuGroupOrder() {
        setLibrary(LibraryUIState(books = listOf(book(1L))))
        openMenu()

        val lastCategory = rule.onNodeWithTag(LibraryTestTags.menuCategory(LibraryCategory.Audiobooks))
            .getUnclippedBoundsInRoot()
        val firstSort = rule.onNodeWithTag(LibraryTestTags.menuSort(LibrarySort.Title)).getUnclippedBoundsInRoot()
        val lastSort = rule.onNodeWithTag(LibraryTestTags.menuSort(LibrarySort.NewestAdded))
            .getUnclippedBoundsInRoot()
        val direction = rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION).getUnclippedBoundsInRoot()

        assertTrue("Show group sits above the Sort group", lastCategory.bottom <= firstSort.top)
        assertTrue("Sort group sits above the Direction row", lastSort.bottom <= direction.top)
        assertTrue("a divider separates Show from Sort", firstSort.top - lastCategory.bottom >= 8.dp - HALF_DP)
        assertTrue("a divider separates Sort from Direction", direction.top - lastSort.bottom >= 8.dp - HALF_DP)
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
    fun tc154_noVisibleGroupHeaders() {
        setLibrary(LibraryUIState(books = listOf(book(1L))))
        openMenu()

        rule.onNodeWithText("Show").assertDoesNotExist()
        rule.onNodeWithText("Sort").assertDoesNotExist()
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
    fun tc157_oldGridStaysDuringReload() {
        setLibrary(
            LibraryUIState(books = listOf(book(1L), book(2L)), isInitialLoading = true, category = LibraryCategory.Audiobooks),
        )

        rule.onNodeWithTag(LibraryTestTags.GRID).assertIsDisplayed()
        heading().assertTextEquals("Audiobooks").assertIsDisplayed()
        rule.onNodeWithText(LOADING_LABEL).assertDoesNotExist()
    }

    @Test
    fun tc158_filteredStateRendersInBothThemes() {
        val darkTheme = mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = darkTheme.value) {
                Box(Modifier.requiredWidth(390.dp).height(800.dp)) {
                    LibraryScreenContent(
                        uiState = LibraryUIState(
                            books = listOf(book(1L)),
                            category = LibraryCategory.Ebooks,
                            direction = SortDirection.Asc,
                        ),
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

        assertFilteredHeadingAndButton()
        rule.runOnIdle { darkTheme.value = false }
        assertFilteredHeadingAndButton()
    }

    @Test
    fun tc161_filteredEmptyKeepsHeadingAndFilterButton() {
        setLibrary(LibraryUIState(hasLoaded = true, category = LibraryCategory.Ebooks))

        heading().assertTextEquals("Ebooks").assertIsDisplayed()
        filterButton().assertIsDisplayed()
        rule.onNodeWithText(EMPTY_TITLE).assertDoesNotExist()

        openMenu()
        rule.onNodeWithTag(LibraryTestTags.MENU_DIRECTION).assertIsDisplayed()
    }

    private fun assertFilteredHeadingAndButton() {
        heading().assertTextEquals("Ebooks").assertIsDisplayed()
        filterButton().assertIsDisplayed()
    }

    private fun heading() = rule.onNodeWithTag(LibraryTestTags.HEADING, useUnmergedTree = true)

    private fun filterButton() = rule.onNodeWithTag(LibraryTestTags.FILTER_BUTTON)

    private fun openMenu() = filterButton().performClick()

    /** The button's fill, sampled inside the circle clear of the border and glyph. */
    private fun filterButtonFill(): Color {
        val pixels = filterButton().captureToImage().toPixelMap()
        val x = with(rule.density) { 16.dp.roundToPx() }
        val y = with(rule.density) { 6.dp.roundToPx() }
        return pixels[x, y]
    }

    private fun assertColourNear(expected: Color, actual: Color, message: String) {
        val tolerance = 0.03f
        assertTrue(
            "$message: expected $expected but was $actual",
            abs(expected.red - actual.red) <= tolerance &&
                abs(expected.green - actual.green) <= tolerance &&
                abs(expected.blue - actual.blue) <= tolerance,
        )
    }

    private fun setLibrary(
        uiState: LibraryUIState,
        height: Dp = 800.dp,
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
        height = height,
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
        height: Dp = 800.dp,
        onOpenBook: (String) -> Unit = {},
        onAddBooks: () -> Unit = {},
        onLoadMore: (Int) -> Unit = {},
        onRefresh: () -> Unit = {},
        onSelectCategory: (LibraryCategory) -> Unit = {},
        onSelectSort: (LibrarySort) -> Unit = {},
        onToggleDirection: () -> Unit = {},
        onScrolledToTop: () -> Unit = {},
        gridState: LazyGridState? = null,
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.requiredWidth(390.dp).height(height)) {
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

        /** Grid index of the footer in tc84: one header item, six books, then the footer. */
        const val FOOTER_INDEX = 7

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
