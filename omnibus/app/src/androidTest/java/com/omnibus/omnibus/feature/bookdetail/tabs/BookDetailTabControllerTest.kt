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
import androidx.compose.ui.test.assertIsDisplayed
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
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
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

    private fun ComposeContentTestRule.dp(value: androidx.compose.ui.unit.Dp): Float = with(density) { value.toPx() }

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

    // TC-63
    @Test
    fun issue94_tc63_jumpToOverviewScrollsUpToThePinPointToo() {
        val scroll = ScrollState(0)
        val toOverview = BookDetailSection { ctx, modifier ->
            Column(modifier) {
                Text(
                    "jump-overview",
                    modifier = Modifier.testTag("jump-overview").clickable { ctx.tabs.jumpTo(BookDetailTab.Overview) },
                )
                Box(Modifier.fillMaxWidth().aspectRatio(0.25f))
            }
        }
        rule.screen(
            scroll,
            BookDetailSlots(
                overviewSections = listOf(fakeSection("fake-overview")),
                filesSections = listOf(toOverview),
            ),
        )
        val naturalTop = rule.stripTop()
        val barBottom = rule.barBottom()
        val pinPoint = (naturalTop - barBottom).roundToInt()

        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()
        rule.scrollTo(scroll)
        assertTrue(scroll.value > pinPoint)

        rule.onNodeWithTag("jump-overview").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
        assertEquals(barBottom, rule.stripTop(), 1f)
        assertTrue("page not at 0", scroll.value > 0)
    }

    // TC-64
    @Test
    fun issue94_tc64_scrollToLandsTheAnchorBelowThePinnedStrip() {
        val scroll = ScrollState(0)
        val anchored = BookDetailSection { _, modifier ->
            Column(modifier) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.25f))
                Box(Modifier.fillMaxWidth().height(40.dp).bookDetailAnchor(BookDetailAnchor.Series).testTag("anchor"))
                Box(Modifier.fillMaxWidth().aspectRatio(0.5f))
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
        rule.screen(scroll, BookDetailSlots(overviewSections = listOf(anchored), filesSections = listOf(files)))
        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag("scroll-series").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
        val anchorTop = rule.onNodeWithTag("anchor").bounds().top
        val target = rule.onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds().bottom + rule.dp(16.dp)
        if (scroll.value == scroll.maxValue) {
            assertTrue("anchor at $anchorTop, expected at or below $target", anchorTop >= target - 1f)
        } else {
            assertEquals(target, anchorTop, 1f)
            rule.onNodeWithTag("anchor").assertIsDisplayed()
        }
    }

    // TC-65
    @Test
    fun issue94_tc65_scrollToWithoutAnAnchorFallsBackToJumpAfterASecond() {
        val scroll = ScrollState(0)
        val files = BookDetailSection { ctx, modifier ->
            Column(modifier) {
                Text(
                    "scroll-series",
                    modifier = Modifier.testTag("scroll-series").clickable { ctx.tabs.scrollTo(BookDetailAnchor.Series) },
                )
                Box(Modifier.fillMaxWidth().aspectRatio(0.25f))
            }
        }
        rule.screen(scroll, BookDetailSlots(filesSections = listOf(files)))
        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()
        val pinPoint = (rule.stripTop() - rule.barBottom()).roundToInt()

        rule.onNodeWithTag("scroll-series").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitUntil(timeoutMillis = 5_000) { scroll.value == min(pinPoint, scroll.maxValue) && scroll.value > 0 }
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
        assertEquals(rule.barBottom(), rule.stripTop(), 1f)
    }

    // TC-66 (motion on)
    @Test
    fun issue94_tc66_jumpToAnimatesWithMotionOn() {
        rule.mainClock.autoAdvance = false
        val scroll = ScrollState(0)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailScreenContent(
                    uiState = BookDetailUIState(book = testDetailBook(), isLoading = false),
                    onNavigate = {}, onClose = {}, onRetry = {}, onRefresh = {}, onReloadBook = {},
                    onBookChanged = {}, onTransientErrorShown = {},
                    modifier = Modifier.fillMaxSize(),
                    slots = jumpSlots(),
                    scrollState = scroll,
                    reducedMotion = false,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("jump-reviews").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
        val mid = scroll.value.toFloat()
        assertTrue("mid $mid should be between 0 and the pin point", mid > 0f)

        rule.mainClock.advanceTimeBy(3000)
        // The strip ends pinned under the bar, which is what the jump is for.
        assertEquals(rule.barBottom(), rule.stripTop(), 1f)
        assertTrue("scroll ${scroll.value} should have moved past mid $mid", scroll.value.toFloat() > mid)
    }

    // TC-66 (reduced motion)
    @Test
    fun issue94_tc66_jumpToIsInstantWithReducedMotion() {
        rule.mainClock.autoAdvance = false
        val scroll = ScrollState(0)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailScreenContent(
                    uiState = BookDetailUIState(book = testDetailBook(), isLoading = false),
                    onNavigate = {}, onClose = {}, onRetry = {}, onRefresh = {}, onReloadBook = {},
                    onBookChanged = {}, onTransientErrorShown = {},
                    modifier = Modifier.fillMaxSize(),
                    slots = jumpSlots(),
                    scrollState = scroll,
                    reducedMotion = true,
                )
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("jump-reviews").performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        assertEquals(rule.barBottom(), rule.stripTop(), 1f)
        assertTrue(scroll.value > 0)
    }

    private fun jumpSlots(): BookDetailSlots {
        val jump = BookDetailSection { ctx, modifier ->
            Column(modifier) {
                Text(
                    "jump-reviews",
                    modifier = Modifier.testTag("jump-reviews").clickable { ctx.tabs.jumpTo(BookDetailTab.Reviews) },
                )
                Box(Modifier.fillMaxWidth().aspectRatio(0.25f))
            }
        }
        return BookDetailSlots(overviewSections = listOf(jump), reviewsSections = listOf(fakeSection("fake-reviews")))
    }

    // TC-67
    @Test
    fun issue94_tc67_anchorModifierIsInertWithoutAHost() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailSection { _, modifier ->
                    Box(modifier.fillMaxWidth().height(40.dp).bookDetailAnchor(BookDetailAnchor.Series).testTag("inert"))
                }.Content(testSectionContext(), Modifier.fillMaxSize())
            }
        }
        rule.onNodeWithTag("inert").assertIsDisplayed()
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
}
