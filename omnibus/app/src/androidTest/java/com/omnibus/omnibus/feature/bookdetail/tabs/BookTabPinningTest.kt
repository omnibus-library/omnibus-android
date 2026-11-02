package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.overview.description.BookDescriptionSection
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.roundToInt

/** The strip pinned under the top bar on the full page, with a real scroll. */
class BookTabPinningTest {

    @get:Rule
    val rule = createComposeRule()

    private val linkedBook: Book = testDetailBook(creators = listOf(Creator("Ann Lee", "author", null, null)))

    private fun ComposeContentTestRule.dp(value: Dp): Float = with(density) { value.toPx() }

    private fun ComposeContentTestRule.barBottom(): Float = onNodeWithTag(BookDetailTestTags.TOP_BAR).bounds().bottom

    private fun ComposeContentTestRule.stripTop(): Float = onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds().top

    private fun ComposeContentTestRule.screen(scroll: ScrollState, slots: BookDetailSlots, book: Book = linkedBook) {
        setDetailScreen(BookDetailUIState(book = book, isLoading = false), slots = slots, scrollState = scroll)
        waitForIdle()
    }

    private fun overviewTall(): BookDetailSlots = BookDetailSlots(overviewSections = listOf(tallFakeSection("fake-overview")))

    private fun allFakes(): BookDetailSlots = BookDetailSlots(
        overviewSections = listOf(tallFakeSection("fake-overview")),
        reviewsSections = listOf(fakeSection("fake-reviews")),
        highlightsSections = listOf(fakeSection("fake-highlights")),
        filesSections = listOf(tallFakeSection("fake-files")),
    )

    // TC-42
    @Test
    fun issue94_tc42_stripStaysPinnedUnderTheTopBarAfterScrolling() {
        val scroll = ScrollState(0)
        rule.screen(scroll, overviewTall())
        val barBefore = rule.barBottom()
        val naturalTop = rule.stripTop()
        rule.scrollTo(scroll)
        assertTrue("scrolled ${scroll.value}px", scroll.value > 0)
        assertTrue(scroll.value > naturalTop - barBefore)
        assertEquals(rule.barBottom(), rule.stripTop(), 1f)
        assertEquals(barBefore, rule.barBottom(), 1f)
        rule.onNodeWithTag(BookDetailTabsTestTags.STRIP).assertIsDisplayed()
    }

    // TC-43
    @Test
    fun issue94_tc43_pinnedStripIsStillTappable() {
        val scroll = ScrollState(0)
        rule.screen(scroll, allFakes())
        rule.scrollTo(scroll)
        val pinnedTop = rule.stripTop()

        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Reviews).assertIsSelected()
        rule.onNodeWithTag("fake-reviews").assertExists()
        assertEquals(pinnedTop, rule.stripTop(), 1f)

        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Files).assertIsSelected()
        rule.onNodeWithTag("fake-files").assertExists()
        assertEquals(pinnedTop, rule.stripTop(), 1f)
    }

    // TC-44
    @Test
    fun issue94_tc44_stripScrollsPinsAndUnpinsAtTheSamePoint() {
        val scroll = ScrollState(0)
        rule.screen(scroll, overviewTall())
        rule.scrollTo(scroll, 0)
        val naturalTop = rule.stripTop()
        val barBottom = rule.barBottom()
        val pinPoint = (naturalTop - barBottom).roundToInt()

        val half = pinPoint / 2
        rule.scrollTo(scroll, half)
        assertEquals(naturalTop - half, rule.stripTop(), 1f)

        rule.scrollTo(scroll, pinPoint + 300)
        assertEquals(barBottom, rule.stripTop(), 1f)

        rule.scrollTo(scroll, half)
        assertEquals(naturalTop - half, rule.stripTop(), 1f)

        rule.scrollTo(scroll, 0)
        assertEquals(naturalTop, rule.stripTop(), 1f)
    }

    // TC-45
    @Test
    fun issue94_tc45_pinnedStripDrawsItsGroundOverTheContent() {
        val scroll = ScrollState(0)
        rule.screen(scroll, BookDetailSlots(overviewSections = listOf(tallFakeSection("fake-overview", Color.Red))))
        val naturalTop = rule.stripTop()
        val barBottom = rule.barBottom()
        val pinPoint = (naturalTop - barBottom).roundToInt()
        // The red box starts 10 dp below the pin, so it sits under the strip.
        rule.scrollTo(scroll, pinPoint + rule.dp(86.dp).roundToInt())

        val strip = rule.onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds()
        val inset = rule.dp(16.dp)
        val cell = (strip.width - 2 * inset) / 4
        val x = (strip.left + inset + cell).toInt()
        val y = (strip.top + strip.height / 2).toInt()
        val pixel = rule.onRoot().captureToImage().toPixelMap()[x, y]

        val bg0 = OmnibusPalette.Atrium.bg0
        val red = Color.Red
        val alpha = 0.92f
        val tolerance = 6f / 255f
        assertEquals(bg0.red * alpha + red.red * (1 - alpha), pixel.red, tolerance)
        assertEquals(bg0.green * alpha + red.green * (1 - alpha), pixel.green, tolerance)
        assertEquals(bg0.blue * alpha + red.blue * (1 - alpha), pixel.blue, tolerance)
    }

    // TC-46
    @Test
    fun issue94_tc46_tabChangeAtThePinPointLeavesTheScrollValueAlone() {
        val scroll = ScrollState(0)
        rule.screen(
            scroll,
            BookDetailSlots(
                overviewSections = listOf(tallFakeSection("fake-overview")),
                highlightsSections = listOf(fakeSection("fake-highlights")),
                filesSections = listOf(tallFakeSection("fake-files")),
            ),
        )
        val naturalTop = rule.stripTop()
        val barBottom = rule.barBottom()
        rule.scrollTo(scroll, (naturalTop - barBottom).roundToInt())
        val before = scroll.value

        listOf(BookDetailTab.Reviews, BookDetailTab.Highlights, BookDetailTab.Files, BookDetailTab.Overview).forEach { tab ->
            rule.tabNode(tab).performClick()
            rule.waitForIdle()
            assertEquals("after tapping $tab", before, scroll.value)
            assertEquals(barBottom, rule.stripTop(), 1f)
        }
    }

    // TC-47
    @Test
    fun issue94_tc47_emptyPanelsStillAllowScrollingTheStripToThePin() {
        val scroll = ScrollState(0)
        rule.screen(scroll, BookDetailSlots())
        rule.onNodeWithTag(BookDetailTestTags.STAGE).assertExists()
        rule.onNodeWithTag(BookDetailTabsTestTags.STRIP).assertIsDisplayed()
        rule.onNodeWithTag(BookDetailTabsTestTags.PANEL, useUnmergedTree = true).assertExists()

        val naturalTop = rule.stripTop()
        val barBottom = rule.barBottom()
        assertTrue(
            "maxValue ${scroll.maxValue} must reach the pin point ${naturalTop - barBottom}",
            scroll.maxValue >= naturalTop - barBottom,
        )
        rule.scrollTo(scroll)
        assertEquals(barBottom, rule.stripTop(), 1f)
    }

    // TC-48
    @Test
    fun issue94_tc48_switchingToAShorterPanelPastThePinKeepsTheStripPinned() {
        val scroll = ScrollState(0)
        rule.screen(scroll, overviewTall())
        val naturalTop = rule.stripTop()
        val barBottom = rule.barBottom()
        rule.scrollTo(scroll)
        assertTrue(scroll.value > (naturalTop - barBottom).roundToInt())

        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Files).assertIsSelected()
        assertTrue(scroll.maxValue >= naturalTop - barBottom)
        assertTrue(scroll.value <= scroll.maxValue)
        assertEquals(barBottom, rule.stripTop(), 1f)
    }

    // TC-84
    @Test
    fun issue94_tc84_contextStaysOneObjectAcrossScrollAndTabChanges() {
        val seenStart = mutableListOf<BookDetailSectionContext>()
        val seenPanel = mutableListOf<BookDetailSectionContext>()
        val slots = BookDetailSlots(
            stageStatusStart = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seenStart += ctx }
                Text("recorder", modifier = modifier.testTag("recorder"))
            }),
            overviewSections = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seenPanel += ctx }
                Box(modifier.fillMaxWidth().aspectRatio(0.25f).testTag("tall"))
            }),
            reviewsSections = listOf(BookDetailSection { ctx, modifier ->
                SideEffect { seenPanel += ctx }
                Text("reviews", modifier = modifier.testTag("reviews"))
            }),
        )
        val reloadKey = mutableIntStateOf(0)
        val scroll = ScrollState(0)
        val book = testDetailBook()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailScreenContent(
                    uiState = BookDetailUIState(book = book, isLoading = false, reloadKey = reloadKey.intValue),
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
                )
            }
        }
        rule.waitForIdle()
        val first = seenStart.first()

        rule.scrollTo(scroll)
        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.waitForIdle()
        assertTrue(seenStart.all { it === first })
        assertTrue(seenPanel.isNotEmpty() && seenPanel.all { it === first })

        rule.runOnIdle { reloadKey.intValue = 1 }
        rule.waitForIdle()
        assertTrue(seenStart.last() !== first)
        assertEquals(1, seenStart.last().reloadKey)
    }

    // TC-90
    @Test
    fun issue94_fullScreenExposesFourTabsAndNoRibbonNode() {
        val book = testDetailBook().copy(description = "word ".repeat(120))
        rule.setDetailScreen(
            BookDetailUIState(book = book, isLoading = false),
            slots = BookDetailSlots(overviewSections = listOf(BookDescriptionSection)),
        )
        rule.waitForIdle()
        rule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(4)
        rule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true),
        ).assertCountEquals(1)
        rule.onAllNodesWithTag(BookDetailTabsTestTags.RIBBON, useUnmergedTree = true).assertCountEquals(1)
        val ribbonConfig = rule.onNodeWithTag(BookDetailTabsTestTags.RIBBON, useUnmergedTree = true).fetchSemanticsNode().config
        assertTrue(ribbonConfig.contains(SemanticsProperties.HideFromAccessibility))
        val toggle = rule.onNodeWithTag(com.omnibus.omnibus.feature.bookdetail.overview.description.BookDescriptionTestTags.DESCRIPTION_TOGGLE, useUnmergedTree = true)
            .fetchSemanticsNode()
        assertEquals(Role.Button, toggle.config[SemanticsProperties.Role])
    }
}
