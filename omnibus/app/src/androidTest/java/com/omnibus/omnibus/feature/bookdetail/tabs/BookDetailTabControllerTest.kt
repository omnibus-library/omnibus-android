package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.forcedTwoPanes
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.min
import kotlin.math.roundToInt

/** The controller as sections use it: select, jumpTo, scrollTo, anchors and state survival. */
class BookDetailTabControllerTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ComposeContentTestRule.barBottom(): Float = onNodeWithTag(BookDetailTestTags.TOP_BAR).bounds().bottom

    private fun ComposeContentTestRule.stripTop(): Float = onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds().top

    private fun ComposeContentTestRule.screen(scroll: ScrollState, slots: BookDetailSlots) {
        setDetailScreen(BookDetailUIState(book = testDetailBook(), isLoading = false), slots = slots, scrollState = scroll)
        waitForIdle()
    }

    // TC-61
    @Test
    fun issue94_tc61_contextTabsReflectTheStripAndSelectBehavesLikeATap() {
        val controllerFake = BookDetailSection { ctx, modifier ->
            Column(modifier) {
                Text("selected:${ctx.tabs.selected.name}", modifier = Modifier.testTag("selected-text"))
                Text(
                    "select-files",
                    modifier = Modifier.testTag("select-files").clickable { ctx.tabs.select(BookDetailTab.Files) },
                )
            }
        }
        val slots = BookDetailSlots(
            overviewSections = listOf(controllerFake),
            reviewsSections = listOf(controllerFake),
            highlightsSections = listOf(controllerFake),
            filesSections = listOf(controllerFake),
        )
        rule.screen(ScrollState(0), slots)
        rule.onNodeWithTag("selected-text").assertTextEquals("selected:Overview")

        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("selected-text").assertTextEquals("selected:Reviews")

        rule.onNodeWithTag("select-files").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.onNodeWithTag("selected-text").assertTextEquals("selected:Files")
        rule.tabNode(BookDetailTab.Files).assertIsSelected()
    }

    // TC-62
    @Test
    fun issue94_tc62_jumpToBringsTheStripToTheTopUnderTheBar() {
        val scroll = ScrollState(0)
        val jump = BookDetailSection { ctx, modifier ->
            Column(modifier) {
                Text(
                    "jump-reviews",
                    modifier = Modifier.testTag("jump-reviews").clickable { ctx.tabs.jumpTo(BookDetailTab.Reviews) },
                )
                Box(Modifier.fillMaxWidth().aspectRatio(0.25f))
            }
        }
        rule.screen(scroll, BookDetailSlots(overviewSections = listOf(jump), reviewsSections = listOf(fakeSection("fake-reviews"))))
        val naturalTop = rule.stripTop()
        val barBottom = rule.barBottom()
        val pinPoint = (naturalTop - barBottom).roundToInt()

        rule.onNodeWithTag("jump-reviews").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Reviews).assertIsSelected()
        assertEquals(barBottom, rule.stripTop(), 1f)
        assertEquals(min(pinPoint, scroll.maxValue).toFloat(), scroll.value.toFloat(), 1f)
        rule.onNodeWithTag("fake-reviews").assertExists()
    }

    // TC-68
    @Test
    fun issue94_tc68_removedAnchorsAreUnregisteredAndScrollToFallsBack() {
        val scroll = ScrollState(0)
        val showAnchor = mutableStateOf(true)
        val overview = BookDetailSection { _, modifier ->
            Column(modifier) {
                if (showAnchor.value) {
                    Box(Modifier.fillMaxWidth().height(40.dp).bookDetailAnchor(BookDetailAnchor.Series).testTag("anchor"))
                }
                Box(Modifier.fillMaxWidth().aspectRatio(0.25f))
            }
        }
        val files = BookDetailSection { ctx, modifier ->
            Column(modifier) {
                Text(
                    "scroll-series",
                    modifier = Modifier.testTag("scroll-series").clickable { ctx.tabs.scrollTo(BookDetailAnchor.Series) },
                )
                Box(Modifier.fillMaxWidth().aspectRatio(0.25f))
            }
        }
        rule.screen(scroll, BookDetailSlots(overviewSections = listOf(overview), filesSections = listOf(files)))
        rule.onNodeWithTag("anchor").assertExists()
        val pinPoint = (rule.stripTop() - rule.barBottom()).roundToInt()

        rule.runOnIdle { showAnchor.value = false }
        rule.waitForIdle()
        rule.onAllNodesWithTag("anchor").assertCountEquals(0)

        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("scroll-series").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(timeoutMillis = 5_000) { scroll.value == min(pinPoint, scroll.maxValue) && scroll.value > 0 }
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
    }

    // TC-70
    @Test
    fun issue94_tc70_tabSurvivesReloadAndADifferentBookOpensOnOverview() {
        val book = mutableStateOf(testDetailBook())
        val reloadKey = mutableIntStateOf(0)
        val slots = BookDetailSlots(
            overviewSections = listOf(fakeSection("fake-overview")),
            filesSections = listOf(fakeSection("fake-files")),
        )
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                key(book.value.uuid) {
                    BookDetailScreenContent(
                        uiState = BookDetailUIState(book = book.value, isLoading = false, reloadKey = reloadKey.intValue),
                        onNavigate = {}, onClose = {}, onRetry = {}, onRefresh = {}, onReloadBook = {},
                        onBookChanged = {}, onTransientErrorShown = {},
                        modifier = Modifier.fillMaxSize(),
                        slots = slots,
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()

        rule.runOnIdle { reloadKey.intValue = 1 }
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Files).assertIsSelected()

        rule.runOnIdle { book.value = testDetailBook(id = 2) }
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
    }

    // TC-45
    @Test
    fun issue211_tc45_twoPaneReviewsShortcutSelectsReviewsAndScrollsThePanelToItsTop() {
        val stage = ScrollState(0)
        val panel = ScrollState(0)
        rule.twoPaneScreen(
            BookDetailSlots(
                stageStatusEnd = listOf(reviewsShortcut()),
                overviewSections = listOf(tallFakeSection("fake-overview")),
                reviewsSections = listOf(fakeSection("fake-reviews")),
            ),
            stage,
            panel,
        )
        rule.scrollTo(panel)
        assertTrue("panel needs range", panel.value > 0)

        rule.onNodeWithTag("open-reviews").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Reviews).assertIsSelected()
        assertEquals(0, panel.value)
    }

    // TC-46
    @Test
    fun issue211_tc46_twoPaneSeriesShortcutSelectsOverviewAndLandsTheAnchorBelowTheStrip() {
        val stage = ScrollState(0)
        val panel = ScrollState(0)
        val anchor = BookDetailSection { _, modifier ->
            Box(modifier.fillMaxWidth().height(40.dp).bookDetailAnchor(BookDetailAnchor.Series).testTag("anchor"))
        }
        rule.twoPaneScreen(
            BookDetailSlots(
                stageStatusEnd = listOf(seriesShortcut()),
                overviewSections = listOf(tallFakeSection("filler"), anchor, tallFakeSection("tail")),
                filesSections = listOf(fakeSection("fake-files")),
            ),
            stage,
            panel,
        )
        rule.tapTab(BookDetailTab.Files)
        rule.onNodeWithTag("open-series").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
        // The tail keeps the target inside the scroll range, so the anchor is not clamped.
        val clearance = with(rule.density) { AnchorClearance.toPx() }
        val stripBottom = rule.onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds().bottom
        assertEquals(stripBottom + clearance, rule.onNodeWithTag("anchor").bounds().top, 1f)
    }

    // TC-47
    @Test
    fun issue211_tc47_twoPaneSeriesShortcutFallsBackToThePanelTopWhenTheAnchorNeverLaysOut() {
        val stage = ScrollState(0)
        val panel = ScrollState(0)
        rule.twoPaneScreen(
            BookDetailSlots(
                stageStatusEnd = listOf(seriesShortcut()),
                overviewSections = listOf(tallFakeSection("fake-overview")),
            ),
            stage,
            panel,
        )
        rule.scrollTo(panel)
        assertTrue("panel needs range", panel.value > 0)

        rule.onNodeWithTag("open-series").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(timeoutMillis = 5_000) { panel.value == 0 }
    }

    // TC-48
    @Test
    fun issue211_tc48_twoPaneStageShortcutLeavesTheStageScrollAlone() {
        val stage = ScrollState(0)
        val panel = ScrollState(0)
        rule.twoPaneScreen(
            BookDetailSlots(
                stageProgress = listOf(tallFakeSection("fake-stage")),
                stageStatusEnd = listOf(reviewsShortcut()),
                overviewSections = listOf(tallFakeSection("fake-overview")),
                reviewsSections = listOf(fakeSection("fake-reviews")),
            ),
            stage,
            panel,
        )
        rule.scrollTo(stage)
        assertTrue("stage needs range", stage.value > 0)
        val before = stage.value

        rule.onNodeWithTag("open-reviews").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals(before, stage.value)
    }

    private fun ComposeContentTestRule.twoPaneScreen(slots: BookDetailSlots, stage: ScrollState, panel: ScrollState) {
        setDetailScreen(
            BookDetailUIState(book = testDetailBook(), isLoading = false),
            slots = slots,
            stageScrollState = stage,
            panelScrollState = panel,
            paneSplit = forcedTwoPanes,
        )
        waitForIdle()
    }

    /** A stage shortcut that jumps the trailing pane to Reviews, as `HouseholdReviews` does. */
    private fun reviewsShortcut(): BookDetailSection = BookDetailSection { ctx, modifier ->
        Text("open-reviews", modifier = modifier.testTag("open-reviews").clickable { ctx.tabs.jumpTo(BookDetailTab.Reviews) })
    }

    /** A stage shortcut that scrolls the trailing pane to the Series anchor. */
    private fun seriesShortcut(): BookDetailSection = BookDetailSection { ctx, modifier ->
        Text(
            "open-series",
            modifier = modifier.testTag("open-series").clickable { ctx.tabs.scrollTo(BookDetailAnchor.Series) },
        )
    }
}
