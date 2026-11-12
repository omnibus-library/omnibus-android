package com.omnibus.omnibus.feature.shelves.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertTouchHeightIsEqualTo
import androidx.compose.ui.test.assertTouchWidthIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.library.LibraryUIError
import com.omnibus.omnibus.feature.shelves.ShelvesUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Callbacks, semantics and layout of the Shelves screen. The screen's appearance is covered by
 * ShelvesScreenScreenshotTest.
 */
class ShelvesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue257_tc20_headerAndGroupHeadings() {
        setShelves(
            mutableStateOf(
                ShelvesUIState(
                    previews = listOf(
                        shelf(1L),
                        shelf(2L),
                        shelf(3L, ownerId = MAYA_ID, ownerName = "maya", visibility = ShelfVisibility.Private),
                    ),
                    viewerId = VIEWER_ID,
                ),
            ),
        )

        rule.onNodeWithTag(ShelvesScreenTestTags.TITLE).assertTextEquals("Shelves")
        rule.onNodeWithTag(ShelvesScreenTestTags.CENSUS).assertTextEquals("3 shelves · 2 yours · 1 from 1 other reader")
        val title = rule.onNodeWithTag(ShelvesScreenTestTags.TITLE).bounds()
        val census = rule.onNodeWithTag(ShelvesScreenTestTags.CENSUS).bounds()
        assertTrue("the census starts at ${census.top}, above the title ending at ${title.bottom}", census.top >= title.bottom - 1f)
        rule.onNodeWithText(NOTICE).assertIsDisplayed()
        rule.onNodeWithTag(ShelvesScreenTestTags.group(VIEWER_ID)).assert(isHeading())
        rule.onNodeWithTag(ShelvesScreenTestTags.group(VIEWER_ID)).assertTextEquals("Your shelves")
        // The grid is lazy, so on a small screen the second group is composed only once scrolled to.
        rule.onNodeWithTag(ShelvesScreenTestTags.GRID)
            .performScrollToNode(hasTestTag(ShelvesScreenTestTags.group(MAYA_ID)))
        rule.onNodeWithTag(ShelvesScreenTestTags.group(MAYA_ID)).assert(isHeading())
        rule.onNodeWithTag(ShelvesScreenTestTags.group(MAYA_ID)).assertTextEquals("maya’s shelves")
        // The count is merged into its heading row and read as nothing of its own.
        rule.onAllNodesWithText("2").assertCountEquals(0)
        rule.onAllNodesWithText("1").assertCountEquals(0)
    }

    @Test
    fun issue257_tc20_noticeAbsentForNonAdmin() {
        setShelves(
            mutableStateOf(ShelvesUIState(previews = listOf(shelf(1L), shelf(2L)), viewerId = VIEWER_ID)),
        )

        rule.onNodeWithTag(ShelvesScreenTestTags.TITLE).assertExists()
        rule.onNodeWithTag(ShelvesScreenTestTags.NOTICE).assertDoesNotExist()
    }

    @Test
    fun issue257_tc20_backButtonCallsOnBack() {
        var backs = 0
        setShelves(
            mutableStateOf(ShelvesUIState(previews = listOf(shelf(1L)), viewerId = VIEWER_ID)),
            onBack = { backs++ },
        )

        rule.onNodeWithTag(ShelvesScreenTestTags.BACK).performClick()
        rule.waitForIdle()

        assertEquals(1, backs)
    }

    @Test
    fun issue232_tc15_tappingATileOpensItsShelf() {
        val opened = mutableListOf<Long>()
        setShelves(
            mutableStateOf(ShelvesUIState(previews = listOf(shelf(1L)), viewerId = VIEWER_ID)),
            onOpenShelf = { opened += it },
        )

        rule.onNodeWithTag(ShelfTileTestTags.tile(1L)).performClick()

        rule.waitUntil { opened.isNotEmpty() }
        assertEquals(listOf(1L), opened)
    }

    @Test
    fun issue257_tc21_censusWordingPluralsAndDroppedParts() {
        val uiState = mutableStateOf(ShelvesUIState(viewerId = VIEWER_ID))
        setShelves(uiState)

        rule.runOnIdle { uiState.value = ShelvesUIState(previews = listOf(shelf(1L)), viewerId = VIEWER_ID) }
        rule.waitForIdle()
        rule.onNodeWithTag(ShelvesScreenTestTags.CENSUS).assertTextEquals("1 shelf · 1 yours")

        rule.runOnIdle {
            uiState.value = ShelvesUIState(previews = List(4) { shelf(it + 1L) }, viewerId = VIEWER_ID)
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ShelvesScreenTestTags.CENSUS).assertTextEquals("4 shelves · 4 yours")

        rule.runOnIdle {
            uiState.value = ShelvesUIState(
                previews = List(3) { shelf(it + 1L, ownerId = MAYA_ID, ownerName = "maya") },
                viewerId = VIEWER_ID,
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ShelvesScreenTestTags.CENSUS).assertTextEquals("3 shelves · 3 from 1 other reader")

        rule.runOnIdle {
            uiState.value = ShelvesUIState(
                previews = listOf(
                    shelf(1L),
                    shelf(2L),
                    shelf(3L, ownerId = MAYA_ID, ownerName = "maya"),
                    shelf(4L, ownerId = BART_ID, ownerName = "bart"),
                    shelf(5L, ownerId = BART_ID, ownerName = "bart"),
                ),
                viewerId = VIEWER_ID,
            )
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ShelvesScreenTestTags.CENSUS).assertTextEquals("5 shelves · 2 yours · 3 from 2 other readers")
    }

    @Test
    fun issue257_tc22_tilesFitTheWidthInEqualRows() {
        setShelves(
            mutableStateOf(
                ShelvesUIState(
                    previews = listOf(
                        shelf(1L, name = "Short"),
                        shelf(2L, name = "A somewhat longer shelf name"),
                        shelf(3L, kind = ShelfKind.Smart, visibility = ShelfVisibility.Public),
                        shelf(4L, kind = ShelfKind.Wishlist, bookCount = 0L),
                        shelf(5L, name = "Fifth"),
                    ),
                    viewerId = VIEWER_ID,
                ),
            ),
        )

        // The lazy grid composes only the tiles on screen, so the checks read whichever tiles are composed.
        val tiles = rule.onAllNodes(isShelfTile()).fetchSemanticsNodes().map { it.boundsInRoot }
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("at least one tile is on screen", tiles.isNotEmpty())
        tiles.forEach { tile ->
            assertTrue("a tile stays inside the window's width", tile.left >= root.left - 1f && tile.right <= root.right + 1f)
        }
        tiles.forEach { a ->
            tiles.filter { abs(it.top - a.top) < 1f }.forEach { sameRow ->
                assertTrue("tiles in one row share a height", abs(sameRow.height - a.height) < 1f)
            }
        }
    }

    @Test
    fun issue257_tc23_loadingShowsTheLoadingView() {
        setShelves(mutableStateOf(ShelvesUIState(isInitialLoading = true)))

        rule.onNodeWithText("Loading shelves").assertIsDisplayed()
    }

    @Test
    fun issue257_tc23_errorShowsItsMessageAndRetries() {
        var retries = 0
        setShelves(
            mutableStateOf(ShelvesUIState(error = CONNECT_UI_ERROR)),
            onRetry = { retries++ },
        )

        rule.onNodeWithText(CONNECT_ERROR).assertIsDisplayed()
        rule.onNodeWithText(TRY_AGAIN).performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }

    @Test
    fun issue257_tc23_emptyShowsNoShelvesYetWithoutAnAction() {
        setShelves(mutableStateOf(ShelvesUIState()))

        rule.onNodeWithText("No shelves yet").assertIsDisplayed()
        // The back button is the screen's only action, so the empty view offers none.
        rule.onAllNodes(hasClickAction()).assertCountEquals(1)
        rule.onNodeWithTag(ShelvesScreenTestTags.BACK).assertExists()
    }

    @Test
    fun issue257_tc23_emptySwipeDownRequestsRefresh() {
        var refreshes = 0
        setShelves(
            mutableStateOf(ShelvesUIState()),
            onRefresh = { refreshes++ },
        )

        rule.onNodeWithTag(ShelvesScreenTestTags.EMPTY).performTouchInput { swipeDown() }
        rule.waitForIdle()

        assertEquals(1, refreshes)
    }

    @Test
    fun issue257_tc24_swipeDownRequestsRefresh() {
        var refreshes = 0
        setShelves(
            mutableStateOf(ShelvesUIState(previews = listOf(shelf(1L), shelf(2L)), viewerId = VIEWER_ID)),
            onRefresh = { refreshes++ },
        )

        rule.onNodeWithTag(ShelvesScreenTestTags.GRID).performTouchInput { swipeDown() }
        rule.waitForIdle()

        assertEquals(1, refreshes)
    }

    @Test
    fun issue257_tc25_snackbarShowsTransientErrorKeepingTheList() {
        val uiState = mutableStateOf(
            ShelvesUIState(previews = listOf(shelf(1L)), viewerId = VIEWER_ID, transientError = CONNECT_UI_ERROR),
        )
        val unrelated = mutableStateOf(0)
        var shown = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    ShelvesScreenContent(
                        uiState = uiState.value,
                        onBack = {},
                        onRefresh = {},
                        onRetry = {},
                        onTransientErrorShown = {
                            shown++
                            uiState.value = uiState.value.copy(transientError = null)
                        },
                    )
                    Text("ticks ${unrelated.value}")
                }
            }
        }

        rule.onNodeWithText(CONNECT_ERROR).assertIsDisplayed()
        rule.onNodeWithTag(ShelfTileTestTags.tile(1L)).assertExists()
        assertEquals(1, shown)

        rule.runOnIdle { unrelated.value++ }
        rule.waitForIdle()

        rule.onAllNodesWithText(CONNECT_ERROR).assertCountEquals(1)
        assertEquals(1, shown)
    }

    // TC-19
    @Test
    fun issue270_tc19_barCollapsesOnScrollAndExpandsBack() {
        setShelves(mutableStateOf(ShelvesUIState(previews = List(24) { shelf(it + 1L) }, viewerId = VIEWER_ID)))
        val expandedHeight = rule.onNodeWithTag(ShelvesScreenTestTags.TOP_BAR).bounds().height
        assertTitleOnItsOwnRow()

        rule.onNodeWithTag(ShelvesScreenTestTags.GRID).performTouchInput { swipeUp() }
        rule.waitForIdle()
        val collapsedHeight = rule.onNodeWithTag(ShelvesScreenTestTags.TOP_BAR).bounds().height
        assertTrue("the bar did not collapse from $expandedHeight, it is $collapsedHeight", collapsedHeight < expandedHeight - 1f)
        assertTitleSharesBackRow()

        rule.onNodeWithTag(ShelvesScreenTestTags.GRID).performScrollToIndex(0)
        rule.onNodeWithTag(ShelvesScreenTestTags.GRID).performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(expandedHeight, rule.onNodeWithTag(ShelvesScreenTestTags.TOP_BAR).bounds().height, 1f)
        assertTitleOnItsOwnRow()
    }

    // TC-20
    @Test
    fun issue270_tc20_loadingShowsTheExpandedBar() {
        setShelves(mutableStateOf(ShelvesUIState(isInitialLoading = true)))

        assertExpandedBarShowsTitleAndBack()
    }

    // TC-20
    @Test
    fun issue270_tc20_errorShowsTheExpandedBar() {
        setShelves(mutableStateOf(ShelvesUIState(error = CONNECT_UI_ERROR)))

        assertExpandedBarShowsTitleAndBack()
    }

    // TC-20
    @Test
    fun issue270_tc20_emptyShowsTheExpandedBar() {
        setShelves(mutableStateOf(ShelvesUIState()))

        assertExpandedBarShowsTitleAndBack()
    }

    // TC-22
    @Test
    fun issue270_tc22_titleIsOneHeading() {
        setShelves(mutableStateOf(ShelvesUIState(previews = List(24) { shelf(it + 1L) }, viewerId = VIEWER_ID)))
        assertTitleIsOneHeading()

        rule.onNodeWithTag(ShelvesScreenTestTags.GRID).performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTitleIsOneHeading()
    }

    // TC-23
    @Test
    fun issue270_tc23_backIsOneButtonAnnouncedAsBack() {
        setShelves(mutableStateOf(ShelvesUIState(previews = listOf(shelf(1L)), viewerId = VIEWER_ID)))

        rule.onAllNodesWithContentDescription(backDescription()).assertCountEquals(1)
        rule.onNodeWithContentDescription(backDescription())
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertHasClickAction()
    }

    // TC-23
    @Test
    fun issue270_tc23_backIsAtLeast48dp() {
        setShelves(mutableStateOf(ShelvesUIState(previews = listOf(shelf(1L)), viewerId = VIEWER_ID)))

        rule.onNodeWithContentDescription(backDescription())
            .assertTouchWidthIsEqualTo(48.dp)
            .assertTouchHeightIsEqualTo(48.dp)
    }

    private fun assertExpandedBarShowsTitleAndBack() {
        rule.onNodeWithTag(ShelvesScreenTestTags.TITLE).assertIsDisplayed()
        rule.onNodeWithTag(ShelvesScreenTestTags.BACK).assertIsDisplayed()
        assertTitleOnItsOwnRow()
    }

    /** The expanded bar: the title sits in its own row, below the back arrow. */
    private fun assertTitleOnItsOwnRow() {
        val title = rule.onNodeWithTag(ShelvesScreenTestTags.TITLE).bounds()
        val back = rule.onNodeWithTag(ShelvesScreenTestTags.BACK).bounds()
        assertTrue("the title starts at ${title.top}, above the back row ending at ${back.bottom}", title.top >= back.bottom - 1f)
    }

    /** The collapsed bar: the title shares the back arrow's row. */
    private fun assertTitleSharesBackRow() {
        val title = rule.onNodeWithTag(ShelvesScreenTestTags.TITLE).bounds()
        val back = rule.onNodeWithTag(ShelvesScreenTestTags.BACK).bounds()
        assertTrue("the title starts at ${title.top}, below the back row ending at ${back.bottom}", title.top < back.bottom)
    }

    private fun assertTitleIsOneHeading() {
        rule.onAllNodesWithTag(ShelvesScreenTestTags.TITLE).assertCountEquals(1)
        rule.onNodeWithTag(ShelvesScreenTestTags.TITLE)
            .assert(isHeading())
            .assertTextEquals("Shelves")
    }

    private fun backDescription() = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.navigation_back)

    private fun setShelves(
        uiState: State<ShelvesUIState>,
        onBack: () -> Unit = {},
        onRefresh: () -> Unit = {},
        onRetry: () -> Unit = {},
        onTransientErrorShown: () -> Unit = {},
        onOpenShelf: (Long) -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    ShelvesScreenContent(
                        uiState = uiState.value,
                        onBack = onBack,
                        onRefresh = onRefresh,
                        onRetry = onRetry,
                        onTransientErrorShown = onTransientErrorShown,
                        onOpenShelf = onOpenShelf,
                    )
                }
            }
        }
    }

    /** Matches a tile by its test tag, which every tile carries on its one merged node. */
    private fun isShelfTile() = SemanticsMatcher("is a shelf tile") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("shelf-tile-") == true
    }

    /** A shelf of the viewer's, unless [ownerId] names someone else. Every shelf has a cover-less ledge. */
    private fun shelf(
        id: Long,
        name: String = "Shelf $id",
        ownerId: Long = VIEWER_ID,
        ownerName: String = VIEWER_NAME,
        kind: ShelfKind = ShelfKind.Manual,
        visibility: ShelfVisibility = ShelfVisibility.Private,
        bookCount: Long = 3L,
    ) = ShelfPreview(
        shelf = Shelf(
            id = id,
            ownerUserId = ownerId,
            ownerUsername = ownerName,
            kind = kind,
            name = name,
            visibility = visibility,
            accent = null,
            bookCount = bookCount,
        ),
        coverUuids = emptyList(),
    )

    private companion object {
        const val VIEWER_ID = 7L
        const val VIEWER_NAME = "hunter"
        const val MAYA_ID = 8L
        const val BART_ID = 9L
        const val NOTICE = "Includes other readers’ private shelves — you can see them as an admin."
        const val CONNECT_ERROR = "Could not connect to the server."
        const val TRY_AGAIN = "Try again"
        val CONNECT_UI_ERROR = LibraryUIError.Failed(LibraryError.Unreachable)
    }
}
