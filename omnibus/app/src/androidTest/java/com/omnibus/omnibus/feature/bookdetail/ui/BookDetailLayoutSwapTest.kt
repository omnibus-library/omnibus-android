package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.scrollValue
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.switchablePanes
import com.omnibus.omnibus.feature.bookdetail.tabs.BookDetailTabsTestTags
import com.omnibus.omnibus.feature.bookdetail.tabs.fakeSection
import com.omnibus.omnibus.feature.bookdetail.tabs.tabNode
import com.omnibus.omnibus.feature.bookdetail.tabs.tallFakeSection
import com.omnibus.omnibus.feature.bookdetail.tabs.tapTab
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A layout swap keeps the page's state. In-process swaps flip a test-held state, and recreations go through
 * [StateRestorationTester]. Every assertion is relative to the layout it reads, so no window size is assumed.
 */
class BookDetailLayoutSwapTest {

    @get:Rule
    val rule = createComposeRule()

    /** Tall sections in the panels and the stage, so each scroll has range, and small ones for the rest. */
    private fun swapSlots(): BookDetailSlots = BookDetailSlots(
        stageProgress = listOf(tallFakeSection("fake-stage")),
        overviewSections = listOf(tallFakeSection("fake-overview")),
        reviewsSections = listOf(fakeSection("fake-reviews")),
        highlightsSections = listOf(fakeSection("fake-highlights")),
        filesSections = listOf(tallFakeSection("fake-files")),
        actionBar = listOf(fakeSection("fake-action")),
    )

    /**
     * The page with [twoPane] choosing the layout. Scroll states passed in are the test's; when omitted they are
     * `rememberScrollState()`, so a recreation restores them natively.
     */
    @Composable
    private fun SwapPageContent(
        twoPane: State<Boolean>,
        slots: BookDetailSlots,
        scroll: ScrollState = rememberScrollState(),
        stage: ScrollState = rememberScrollState(),
        panel: ScrollState = rememberScrollState(),
    ) {
        OmnibusTheme(darkTheme = true) {
            BookDetailScreenContent(
                uiState = BookDetailUIState(book = testDetailBook(), isLoading = false),
                onNavigate = {},
                onClose = {},
                onRetry = {},
                onRefresh = {},
                onReloadBook = {},
                onBookChanged = {},
                onTransientErrorShown = {},
                modifier = Modifier.fillMaxSize(),
                slots = slots,
                scrollState = scroll,
                stageScrollState = stage,
                panelScrollState = panel,
                paneSplit = switchablePanes(twoPane),
                reducedMotion = true,
            )
        }
    }

    private fun ComposeContentTestRule.swapPage(
        twoPane: State<Boolean>,
        slots: BookDetailSlots = swapSlots(),
        scroll: ScrollState = ScrollState(0),
        stage: ScrollState = ScrollState(0),
        panel: ScrollState = ScrollState(0),
    ) {
        setContent { SwapPageContent(twoPane, slots, scroll, stage, panel) }
        waitForIdle()
    }

    private fun StateRestorationTester.recreatablePage(twoPane: State<Boolean>, slots: BookDetailSlots) {
        setContent { SwapPageContent(twoPane, slots) }
    }

    /** Flips the layout from the test thread, then waits for the new layout to settle. */
    private fun ComposeContentTestRule.flip(twoPane: MutableState<Boolean>, value: Boolean) {
        runOnIdle { twoPane.value = value }
        waitForIdle()
    }

    /** Two panes when the strip sits to the right of the stage, the column when it sits under it. */
    private fun ComposeContentTestRule.isTwoPane(): Boolean =
        onNodeWithTag(BookDetailTestTags.STAGE).bounds().right <= onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds().left + 1f

    private fun ComposeContentTestRule.barBottom(): Float = onNodeWithTag(BookDetailTestTags.TOP_BAR).bounds().bottom

    /** The page's content top in the column, where the strip sits when the page is not scrolled. */
    private fun ComposeContentTestRule.columnStripTop(): Int = onNodeWithTag(BookDetailTestTags.TAB_AREA).bounds().top.roundToInt()

    private fun ComposeContentTestRule.tapCounter(tag: String) {
        onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        waitForIdle()
    }

    private fun ComposeContentTestRule.sheetOpenAndTyped(text: String) {
        onNodeWithTag("open-sheet").performClick()
        waitForIdle()
        onNodeWithTag("sheet-field").performTextReplacement(text)
        waitForIdle()
    }

    // TC-50
    @Test
    fun issue211_tc50_theLayoutFollowsTheResolverLiveInProcess() {
        val twoPane = mutableStateOf(false)
        rule.swapPage(twoPane)
        assertFalse(rule.isTwoPane())

        rule.flip(twoPane, true)
        assertTrue(rule.isTwoPane())

        rule.flip(twoPane, false)
        assertFalse(rule.isTwoPane())
    }

    // TC-51
    @Test
    fun issue211_tc51_selectedTabSurvivesAnInProcessSwap() {
        val twoPane = mutableStateOf(false)
        rule.swapPage(twoPane)
        rule.tapTab(BookDetailTab.Files)

        rule.flip(twoPane, true)
        rule.tabNode(BookDetailTab.Files).assertIsSelected()
    }

    // TC-52
    @Test
    fun issue211_tc52_selectedTabSurvivesARecreationAcrossASwap() {
        val twoPane = mutableStateOf(false)
        val tester = StateRestorationTester(rule)
        tester.recreatablePage(twoPane, swapSlots())
        rule.waitForIdle()
        rule.tapTab(BookDetailTab.Files)

        rule.flip(twoPane, true)
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Files).assertIsSelected()
    }

    // TC-53
    @Test
    fun issue211_tc53_columnContentOffsetCarriesToTheTrailingScroll() {
        val twoPane = mutableStateOf(false)
        val page = ScrollState(0)
        val panel = ScrollState(0)
        rule.swapPage(twoPane, scroll = page, panel = panel)
        val pinTop = rule.barBottom().roundToInt()
        rule.scrollTo(page)
        assertTrue("page needs range", page.maxValue > 0)
        // The host is not pinned: its screen top is its content top minus the scroll.
        val stripTop = rule.columnStripTop() + page.value
        val expected = columnContentOffsetPx(page.value, stripTop, pinTop)
        assertTrue("column offset must be positive", expected > 0)

        rule.flip(twoPane, true)
        rule.waitUntil(timeoutMillis = 5_000) { abs(panel.value - min(expected, panel.maxValue)) <= 2 }
    }

    // TC-54
    @Test
    fun issue211_tc54_trailingOffsetCarriesToThePageScroll() {
        val twoPane = mutableStateOf(false)
        val page = ScrollState(0)
        val panel = ScrollState(0)
        rule.swapPage(twoPane, scroll = page, panel = panel)
        val stripTop = rule.columnStripTop()
        val pinTop = rule.barBottom().roundToInt()

        rule.flip(twoPane, true)
        rule.scrollTo(panel)
        assertTrue("panel needs range", panel.maxValue > 0)
        val offset = panel.value

        rule.flip(twoPane, false)
        val expected = columnScrollForContentOffset(offset, stripTop, pinTop, page.maxValue)
        assertTrue("trailing offset must be positive", expected > 0)
        rule.waitUntil(timeoutMillis = 5_000) { abs(page.value - expected) <= 2 }
    }

    // TC-55
    @Test
    fun issue211_tc55_offsetIsClampedToTheNewScrollRange() {
        val twoPane = mutableStateOf(false)
        val page = ScrollState(0)
        val panel = ScrollState(0)
        rule.swapPage(twoPane, scroll = page, panel = panel)
        rule.scrollTo(page)
        assertTrue("page needs range", page.maxValue > 0)

        rule.flip(twoPane, true)
        rule.waitUntil(timeoutMillis = 5_000) { panel.maxValue > 0 && panel.value == panel.maxValue }
    }

    // TC-56
    @Test
    fun issue211_tc56_offsetSurvivesARecreationAcrossASwap() {
        val twoPane = mutableStateOf(false)
        val tester = StateRestorationTester(rule)
        tester.recreatablePage(twoPane, swapSlots())
        rule.waitForIdle()
        val stripTop = rule.columnStripTop()
        val pinTop = rule.barBottom().roundToInt()
        rule.onNodeWithTag(BookDetailTestTags.SCROLL).performTouchInput { swipeUp(startY = height * 0.6f, endY = height * 0.1f) }
        rule.waitForIdle()
        val pageValue = rule.onNodeWithTag(BookDetailTestTags.SCROLL).scrollValue().roundToInt()
        val expectedOffset = columnContentOffsetPx(pageValue, stripTop, pinTop)
        assertTrue("column offset must be positive", expectedOffset > 0)

        rule.flip(twoPane, true)
        tester.emulateSavedInstanceStateRestore()
        rule.waitUntil(timeoutMillis = 5_000) {
            val viewport = rule.onNodeWithTag(BookDetailTabsTestTags.PANEL_VIEWPORT)
            val target = min(expectedOffset.toFloat(), viewport.scrollMax())
            abs(viewport.scrollValue() - target) <= 2f
        }
    }

    // TC-57
    @Test
    fun issue211_tc57_sameLayoutRecreationRestoresEachScrollNatively() {
        val twoPane = mutableStateOf(true)
        val tester = StateRestorationTester(rule)
        tester.recreatablePage(twoPane, swapSlots())
        rule.waitForIdle()
        rule.onNodeWithTag(BookDetailTestTags.STAGE_PANE).performTouchInput { swipeUp(startY = height * 0.6f, endY = height * 0.1f) }
        rule.onNodeWithTag(BookDetailTabsTestTags.PANEL_VIEWPORT).performTouchInput { swipeUp(startY = height * 0.6f, endY = height * 0.1f) }
        rule.waitForIdle()
        val stageBefore = rule.onNodeWithTag(BookDetailTestTags.STAGE_PANE).scrollValue()
        val panelBefore = rule.onNodeWithTag(BookDetailTabsTestTags.PANEL_VIEWPORT).scrollValue()
        assertTrue("stage needs range", stageBefore > 0f)
        assertTrue("panel needs range", panelBefore > 0f)

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals(stageBefore, rule.onNodeWithTag(BookDetailTestTags.STAGE_PANE).scrollValue(), 1f)
        assertEquals(panelBefore, rule.onNodeWithTag(BookDetailTabsTestTags.PANEL_VIEWPORT).scrollValue(), 1f)
    }

    // TC-58
    @Test
    fun issue211_tc58_restoreWaitsForLateContentThenLands() {
        val twoPane = mutableStateOf(false)
        val overviewHeight = mutableStateOf(2_000.dp)
        val slots = swapSlots().copy(overviewSections = listOf(growingSection(overviewHeight)))
        val page = ScrollState(0)
        val panel = ScrollState(0)
        rule.swapPage(twoPane, slots = slots, scroll = page, panel = panel)
        val stripTop = rule.columnStripTop()
        val pinTop = rule.barBottom().roundToInt()

        rule.flip(twoPane, true)
        rule.scrollTo(panel)
        val offset = panel.value
        assertTrue("panel needs range", panel.maxValue > 0)

        // The column shrinks past the target as the layout flips, then the section grows back within the wait.
        rule.runOnIdle {
            twoPane.value = false
            overviewHeight.value = 200.dp
        }
        rule.waitForIdle()
        rule.runOnIdle { overviewHeight.value = 2_000.dp }
        rule.waitUntil(timeoutMillis = 5_000) {
            page.maxValue > 0 &&
                abs(page.value - columnScrollForContentOffset(offset, stripTop, pinTop, page.maxValue)) <= 2
        }
    }

    // TC-59
    @Test
    fun issue211_tc59_openSheetKeepsItsTextAcrossAnInProcessSwap() {
        val twoPane = mutableStateOf(false)
        rule.swapPage(twoPane, slots = BookDetailSlots(actionBar = listOf(sheetSection("open-sheet", "sheet-field"))))
        rule.sheetOpenAndTyped("typed")

        rule.flip(twoPane, true)
        rule.onNodeWithTag("sheet-field").assertTextEquals("typed")
    }

    // TC-60
    @Test
    fun issue211_tc60_openSheetKeepsItsTextAcrossARecreation() {
        val twoPane = mutableStateOf(false)
        val tester = StateRestorationTester(rule)
        tester.recreatablePage(twoPane, BookDetailSlots(actionBar = listOf(sheetSection("open-sheet", "sheet-field"))))
        rule.waitForIdle()
        rule.sheetOpenAndTyped("typed")

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("sheet-field").assertTextEquals("typed")
    }

    // TC-61
    @Test
    fun issue211_tc61_savedCountersSurviveAnInProcessSwap() {
        val twoPane = mutableStateOf(false)
        val slots = BookDetailSlots(
            stageProgress = listOf(saveableCounterSection("counter-stage")),
            overviewSections = listOf(saveableCounterSection("counter-overview")),
            actionBar = listOf(saveableCounterSection("counter-action")),
        )
        rule.swapPage(twoPane, slots = slots)
        rule.tapCounter("counter-stage")
        rule.tapCounter("counter-stage")
        rule.tapCounter("counter-overview")
        repeat(3) { rule.tapCounter("counter-action") }

        rule.flip(twoPane, true)
        rule.onNodeWithTag("counter-stage").assertTextEquals("2")
        rule.onNodeWithTag("counter-overview").assertTextEquals("1")
        rule.onNodeWithTag("counter-action").assertTextEquals("3")
        rule.flip(twoPane, false)
        rule.onNodeWithTag("counter-stage").assertTextEquals("2")
        rule.onNodeWithTag("counter-overview").assertTextEquals("1")
        rule.onNodeWithTag("counter-action").assertTextEquals("3")
    }

    // TC-62
    @Test
    fun issue211_tc62_savedCountersSurviveASwapPlusARecreation() {
        val twoPane = mutableStateOf(false)
        val tester = StateRestorationTester(rule)
        val slots = BookDetailSlots(
            stageProgress = listOf(saveableCounterSection("counter-stage")),
            overviewSections = listOf(saveableCounterSection("counter-overview")),
            actionBar = listOf(saveableCounterSection("counter-action")),
        )
        tester.recreatablePage(twoPane, slots)
        rule.waitForIdle()
        rule.tapCounter("counter-stage")
        rule.tapCounter("counter-overview")
        rule.tapCounter("counter-overview")
        rule.tapCounter("counter-action")

        rule.flip(twoPane, true)
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        rule.onNodeWithTag("counter-stage").assertTextEquals("1")
        rule.onNodeWithTag("counter-overview").assertTextEquals("2")
        rule.onNodeWithTag("counter-action").assertTextEquals("1")
    }

    // TC-63
    @Test
    fun issue211_tc63_rememberedObjectsAreNotRebuiltByASwap() {
        val twoPane = mutableStateOf(false)
        val probe = DisposeProbe()
        rule.swapPage(twoPane, slots = swapSlots().copy(overviewSections = listOf(probeSection("probe-overview", probe))))

        rule.flip(twoPane, true)
        rule.flip(twoPane, false)
        assertEquals(1, probe.instances.size)
        assertEquals(0, probe.disposals)
    }

    /** The node's maximum vertical scroll value. Like [scrollValue], for a node whose state the test cannot hold. */
    private fun SemanticsNodeInteraction.scrollMax(): Float =
        fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    /** A section whose height follows [height], so a test can make the content grow or shrink while it runs. */
    private fun growingSection(height: State<Dp>): BookDetailSection = BookDetailSection { _, modifier ->
        Box(modifier.fillMaxWidth().height(height.value).testTag("fake-overview"))
    }
}
