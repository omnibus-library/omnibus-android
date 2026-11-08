package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailError
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.stage.StageCoverWidth
import com.omnibus.omnibus.feature.bookdetail.stage.BookStage
import com.omnibus.omnibus.feature.bookdetail.tabs.BookDetailTabsTestTags
import com.omnibus.omnibus.feature.bookdetail.tabs.assertExists
import com.omnibus.omnibus.feature.bookdetail.tabs.fakeSection
import com.omnibus.omnibus.feature.bookdetail.tabs.ribbonNode
import com.omnibus.omnibus.feature.bookdetail.tabs.tabNode
import com.omnibus.omnibus.feature.bookdetail.tabs.tallFakeSection
import com.omnibus.omnibus.feature.bookdetail.tabs.tapTab
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.bookdetail.forcedColumn
import com.omnibus.omnibus.feature.bookdetail.forcedTwoPanes
import com.omnibus.omnibus.feature.bookdetail.ui.TopBarRowHeight
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.min

/**
 * The two-pane layout and the column it replaces. Two panes are forced through the resolver, never by the window's
 * size, and every assertion is relative: order, overlap, containment and scroll values. Sizes come from the window.
 */
class BookDetailTwoPaneTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * Tall stage and panels, so every scroll has range. A column test passes `tallStage = false`: a stage taller than
     * the window pushes the strip off screen, where its bounds read as empty.
     */
    private fun allFakes(tallStage: Boolean = true): BookDetailSlots = BookDetailSlots(
        stageProgress = listOf(if (tallStage) tallFakeSection("fake-stage") else fakeSection("fake-stage")),
        overviewSections = listOf(tallFakeSection("fake-overview")),
        reviewsSections = listOf(fakeSection("fake-reviews")),
        highlightsSections = listOf(fakeSection("fake-highlights")),
        filesSections = listOf(tallFakeSection("fake-files")),
        actionBar = listOf(fakeSection("fake-action")),
    )

    private fun ComposeContentTestRule.page(
        slots: BookDetailSlots = allFakes(),
        scroll: ScrollState = ScrollState(0),
        stage: ScrollState = ScrollState(0),
        panel: ScrollState = ScrollState(0),
        split: (Dp, Dp) -> com.omnibus.omnibus.feature.bookdetail.ui.PaneSplit? = forcedTwoPanes,
        direction: LayoutDirection = LayoutDirection.Ltr,
        uiState: BookDetailUIState = BookDetailUIState(book = testDetailBook(), isLoading = false),
        screen: Modifier = Modifier.fillMaxSize(),
    ) {
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                OmnibusTheme(darkTheme = true) {
                    BookDetailScreenContent(
                        uiState = uiState,
                        onNavigate = {},
                        onClose = {},
                        onRetry = {},
                        onRefresh = {},
                        onReloadBook = {},
                        onBookChanged = {},
                        onTransientErrorShown = {},
                        modifier = screen,
                        slots = slots,
                        scrollState = scroll,
                        stageScrollState = stage,
                        panelScrollState = panel,
                        paneSplit = split,
                        reducedMotion = true,
                    )
                }
            }
        }
        waitForIdle()
    }

    private fun ComposeContentTestRule.stage() = onNodeWithTag(BookDetailTestTags.STAGE).bounds()

    private fun ComposeContentTestRule.strip() = onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds()

    private fun ComposeContentTestRule.tabArea() = onNodeWithTag(BookDetailTestTags.TAB_AREA).bounds()

    private fun ComposeContentTestRule.actionArea() = onNodeWithTag(BookDetailTestTags.ACTION_AREA).bounds()

    private fun ComposeContentTestRule.barBottom() = onNodeWithTag(BookDetailTestTags.TOP_BAR).bounds().bottom

    // TC-23
    @Test
    fun issue211_tc23_twoPanesPlaceTheStageBesideTheStrip() {
        rule.page()

        val stage = rule.stage()
        val strip = rule.strip()
        assertTrue("stage ends at ${stage.right}, strip starts at ${strip.left}", stage.right <= strip.left + 1f)
        assertTrue(stage.top < strip.bottom && strip.top < stage.bottom)
    }

    // TC-24
    @Test
    fun issue211_tc24_leadingPaneHoldsTheStageSlotsAndTheTrailingPaneHoldsTheTabs() {
        rule.page()

        val strip = rule.strip()
        val title = rule.onNodeWithTag(BookDetailTestTags.TITLE).bounds()
        val statusRow = rule.onNodeWithTag(BookDetailTestTags.STATUS_ROW).bounds()
        val panel = rule.onNodeWithTag(BookDetailTabsTestTags.PANEL).bounds()
        assertTrue(title.right <= strip.left + 1f)
        assertTrue(statusRow.right <= strip.left + 1f)
        assertTrue(panel.left >= rule.stage().right - 1f)
    }

    // TC-25
    @Test
    fun issue211_tc25_rightToLeftMirrorsThePanes() {
        rule.page(direction = LayoutDirection.Rtl)

        val strip = rule.strip()
        val stage = rule.stage()
        assertTrue(strip.left < stage.left)
        assertTrue(stage.left >= strip.right - 1f)
    }

    // TC-26
    @Test
    fun issue211_tc26_oneWashSpansBothPanes() {
        rule.page()

        rule.onAllNodesWithTag(BookDetailTestTags.WASH).assertCountEquals(1)
        val wash = rule.onNodeWithTag(BookDetailTestTags.WASH).bounds()
        val tabArea = rule.tabArea()
        val stage = rule.stage()
        assertTrue(wash.left <= stage.left + 1f && wash.right >= stage.right - 1f)
        assertTrue(wash.left <= tabArea.left + 1f && wash.right >= tabArea.right - 1f)
        assertTrue(wash.top <= tabArea.top + 1f && wash.bottom >= tabArea.bottom - 1f)
    }

    // TC-27
    @Test
    fun issue211_tc27_theStageHaloOverhangsIntoTheTabArea() {
        rule.page()

        val stagePane = rule.onNodeWithTag(BookDetailTestTags.STAGE_PANE).bounds()
        val tabArea = rule.tabArea()
        assertTrue("stage pane ends at ${stagePane.right}, tab area starts at ${tabArea.left}", stagePane.right > tabArea.left)

        rule.tapTab(BookDetailTab.Files)
        rule.tabNode(BookDetailTab.Overview).performTouchInput { click(Offset(2f, centerY)) }
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
    }

    // TC-28
    @Test
    fun issue211_tc28_columnStacksTheStageAboveTheStripAndScrollsThePage() {
        val scroll = ScrollState(0)
        rule.page(slots = allFakes(tallStage = false), scroll = scroll, split = forcedColumn)

        val stage = rule.stage()
        val strip = rule.strip()
        assertTrue(stage.bottom <= strip.top + 1f)
        assertTrue("page needs range", scroll.maxValue > 0)
        val restingTop = strip.top
        rule.scrollTo(scroll)
        assertTrue(rule.strip().top < restingTop)
    }

    // TC-30
    @Test
    fun issue211_tc30_columnBarTitleShowsAndRibbonRetractsWhenPinned() {
        val scroll = ScrollState(0)
        rule.page(slots = allFakes(tallStage = false), scroll = scroll, split = forcedColumn)

        val restingHeight = rule.ribbonNode().bounds().height
        rule.scrollTo(scroll)
        rule.onNodeWithTag(BookDetailTestTags.BAR_TITLE).assertExists()
        assertTrue(rule.ribbonNode().bounds().height < restingHeight)
    }

    // TC-31
    @Test
    fun issue211_tc31_scrollingThePanelLeavesTheStageAlone() {
        val stage = ScrollState(0)
        val panel = ScrollState(0)
        rule.page(stage = stage, panel = panel)

        val stageTop = rule.stage().top
        assertTrue("panel needs range", panel.maxValue > 0)
        rule.scrollTo(panel)
        assertTrue(panel.value > 0)
        assertEquals(stageTop, rule.stage().top, 1f)
        assertEquals(0, stage.value)
    }

    // TC-32
    @Test
    fun issue211_tc32_scrollingTheStageLeavesThePanelAndTheStripAlone() {
        val stage = ScrollState(0)
        val panel = ScrollState(0)
        rule.page(stage = stage, panel = panel)

        val stripTop = rule.strip().top
        assertTrue("stage needs range", stage.maxValue > 0)
        rule.scrollTo(stage)
        assertEquals(0, panel.value)
        assertEquals(stripTop, rule.strip().top, 1f)
    }

    // TC-33
    @Test
    fun issue211_tc33_stripStaysFixedWhilePanelScrolls() {
        val panel = ScrollState(0)
        rule.page(panel = panel)

        val restingTop = rule.strip().top
        assertTrue("panel needs range", panel.maxValue > 0)
        rule.scrollTo(panel)
        assertEquals(restingTop, rule.strip().top, 1f)
        assertTrue(rule.strip().top >= rule.barBottom() - 1f)
    }

    // TC-34
    @Test
    fun issue211_tc34_stripNeverPinsSoItsRibbonStaysOut() {
        val panel = ScrollState(0)
        rule.page(panel = panel)

        val restingRibbon = rule.ribbonNode().bounds()
        assertTrue("panel needs range", panel.maxValue > 0)
        rule.scrollTo(panel)
        val scrolledRibbon = rule.ribbonNode().bounds()
        assertEquals(restingRibbon.height, scrolledRibbon.height, 1f)
        assertEquals(restingRibbon.top, scrolledRibbon.top, 1f)
    }

    // TC-35
    @Test
    fun issue211_tc35_panelSitsUnderTheStripAndClipsToItsViewport() {
        val panel = ScrollState(0)
        rule.page(panel = panel)

        val strip = rule.strip()
        assertTrue(rule.onNodeWithTag(BookDetailTabsTestTags.PANEL).bounds().top >= strip.bottom - 1f)
        rule.scrollTo(panel)
        assertTrue(rule.onNodeWithTag(BookDetailTabsTestTags.PANEL_VIEWPORT).bounds().top >= strip.bottom - 1f)
    }

    // TC-36
    @Test
    fun issue211_tc36_tabSwitchKeepsTheStripStillAndClampsThePanelScroll() {
        val panel = ScrollState(0)
        rule.page(panel = panel)

        val restingTop = rule.strip().top
        rule.scrollTo(panel)
        val previous = panel.value
        rule.tapTab(BookDetailTab.Reviews)
        assertEquals(restingTop, rule.strip().top, 1f)
        assertEquals(min(previous, panel.maxValue), panel.value)
    }

    // TC-37
    @Test
    fun issue211_tc37_ribbonFollowsTheSelectedTab() {
        rule.page()

        rule.tapTab(BookDetailTab.Files)
        val ribbon = rule.ribbonNode().bounds()
        val files = rule.tabNode(BookDetailTab.Files).bounds()
        assertEquals((files.left + files.right) / 2f, (ribbon.left + ribbon.right) / 2f, 1f)
    }

    // TC-38
    @Test
    fun issue211_tc38_actionAreaSitsAtTheFootOfTheLeadingPane() {
        rule.page()

        val action = rule.actionArea()
        val root = rule.onRoot().bounds()
        assertTrue(action.right <= rule.tabArea().left + 1f)
        assertEquals(root.bottom, action.bottom, 1f)
        assertTrue(action.width < root.width)
    }

    // TC-39
    @Test
    fun issue211_tc39_stageScrollsBetweenTheBarAndTheUnbackedActionRow() {
        val stage = ScrollState(0)
        rule.page(stage = stage)

        val restingAction = rule.actionArea()
        assertTrue("stage needs range", stage.maxValue > 0)
        rule.scrollTo(stage)
        assertEquals(restingAction.top, rule.actionArea().top, 1f)
        assertTrue(rule.onNodeWithTag("fake-stage").bounds().bottom <= rule.actionArea().top)
    }

    // TC-40
    @Test
    fun issue211_tc40_columnActionAreaSpansTheFullWidth() {
        rule.page(split = forcedColumn)

        assertEquals(rule.onNodeWithTag(BookDetailTestTags.SCROLL).bounds().width, rule.actionArea().width, 1f)
    }

    // TC-41
    @Test
    fun issue211_tc41_underBarCoverRestsInTheBarRow() {
        rule.stageUnderTopBar(underBar = true)

        val cover = rule.onNodeWithTag(BookDetailTestTags.COVER).bounds()
        assertTrue(cover.top < rule.barBottom())
    }

    // TC-41
    @Test
    fun issue211_tc41_notUnderBarStageStartsBelowTheBarRow() {
        rule.stageUnderTopBar(underBar = false)

        val cover = rule.onNodeWithTag(BookDetailTestTags.COVER).bounds()
        assertTrue(cover.top >= rule.barBottom() - 1f)
    }

    // TC-42
    @Test
    fun issue211_tc42_coverScalesWithThePaneHeight() {
        // Half the window's height, so the scale is set by the pane's height: a narrow window would cap the cover by
        // width, and a tall one would leave it whole.
        rule.page(screen = Modifier.fillMaxWidth().fillMaxHeight(0.5f))

        val density = rule.density.density
        val screen = rule.onNodeWithTag(BookDetailTestTags.SCROLL).bounds()
        val screenWidthDp = screen.width / density
        val screenHeightDp = screen.height / density
        val statusTopDp = rule.barBottom() / density - TopBarRowHeight.value
        val underBar = stageUnderBar(splitAtShare(screenWidthDp.dp).leading)
        val expected = stageCoverScale((screenHeightDp - statusTopDp).dp, underBar)

        val coverWidthPx = rule.onNodeWithTag(BookDetailTestTags.COVER).bounds().width
        val actual = coverWidthPx / with(rule.density) { StageCoverWidth.toPx() }
        assertEquals(expected, actual, 0.01f)
    }

    // TC-65
    @Test
    fun issue211_tc65_snackbarStaysCentredAboveTheActionArea() {
        rule.page(
            uiState = BookDetailUIState(
                book = testDetailBook(),
                isLoading = false,
                transientError = BookDetailError.NotFound,
            ),
        )

        val snackbar = rule.onNodeWithText(detailText(R.string.book_detail_error_not_found)).bounds()
        val scroll = rule.onNodeWithTag(BookDetailTestTags.SCROLL).bounds()
        // The text sits off the snackbar's centre by its own padding; the leading pane's centre is far further off.
        val padding = with(rule.density) { 8.dp.toPx() }
        assertEquals((scroll.left + scroll.right) / 2f, (snackbar.left + snackbar.right) / 2f, padding)
        assertTrue(snackbar.bottom <= rule.actionArea().top + 1f)
    }

    /** Composes the stage and the top bar alone, over a plain box, as the screen does. */
    private fun ComposeContentTestRule.stageUnderTopBar(underBar: Boolean) {
        setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    BookStage(
                        context = testSectionContext(),
                        slots = BookDetailSlots(),
                        reducedMotion = true,
                        backdrop = false,
                        underBar = underBar,
                    )
                    BookDetailTopBar(title = null, filled = false, onBack = {}, reducedMotion = true)
                }
            }
        }
        waitForIdle()
    }
}
