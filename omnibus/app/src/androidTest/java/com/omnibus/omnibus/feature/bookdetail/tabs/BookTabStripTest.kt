package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import com.omnibus.omnibus.feature.bookdetail.textLayout
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Tab strip, ribbon and panel with the host rendered alone. */
class BookTabStripTest {

    @get:Rule
    val rule = createComposeRule()

    private val labels = mapOf(
        BookDetailTab.Overview to R.string.book_detail_tabs_overview,
        BookDetailTab.Reviews to R.string.book_detail_tabs_reviews,
        BookDetailTab.Highlights to R.string.book_detail_tabs_highlights,
        BookDetailTab.Files to R.string.book_detail_tabs_files,
    )

    private fun ComposeContentTestRule.dp(value: androidx.compose.ui.unit.Dp): Float = with(density) { value.toPx() }

    private fun fourFakes() = BookDetailSlots(
        overviewSections = listOf(fakeSection("fake-overview")),
        reviewsSections = listOf(fakeSection("fake-reviews")),
        highlightsSections = listOf(fakeSection("fake-highlights")),
        filesSections = listOf(fakeSection("fake-files")),
    )

    // TC-30
    @Test
    fun issue94_tc30_fourTabsShowInOrder() {
        rule.setTabHost()
        val lefts = BookDetailTab.entries.map { rule.tabNode(it).bounds().left }
        assertTrue(lefts.zipWithNext().all { (a, b) -> a < b })
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
        BookDetailTab.entries.drop(1).forEach { rule.tabNode(it).assertIsNotSelected() }
    }

    // TC-31
    @Test
    fun issue94_tc31_tappingATabSwapsThePanel() {
        rule.setTabHost(fourFakes())
        listOf(BookDetailTab.Reviews, BookDetailTab.Files, BookDetailTab.Overview).forEach { target ->
            rule.tapTab(target)
            BookDetailTab.entries.forEach { tab ->
                val tag = "fake-${tab.name.lowercase()}"
                rule.onAllNodesWithTag(tag).assertCountEquals(if (tab == target) 1 else 0)
            }
            rule.tabNode(target).assertIsSelected()
        }
    }

    // TC-32
    @Test
    fun issue94_tc32_tappingTheSelectedTabDoesNothing() {
        rule.setTabHost(fourFakes())
        val ribbonBefore = rule.ribbonNode().bounds().center.x
        val panelBefore = rule.onNodeWithTag(BookDetailTabsTestTags.PANEL, useUnmergedTree = true).bounds()
        rule.tapTab(BookDetailTab.Overview)
        assertEquals(ribbonBefore, rule.ribbonNode().bounds().center.x, 1f)
        assertEquals(panelBefore.top, rule.onNodeWithTag(BookDetailTabsTestTags.PANEL, useUnmergedTree = true).bounds().top, 1f)
        rule.onAllNodesWithTag("fake-overview").assertCountEquals(1)
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
    }

    // TC-33
    @Test
    fun issue94_tc33_tabsAreFourEqualColumnsInASixteenDpInset() {
        rule.setTabHost()
        val tabs = BookDetailTab.entries.map { rule.tabNode(it).bounds() }
        val strip = rule.onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds()
        tabs.forEach { assertEquals(tabs[0].width, it.width, 1f) }
        assertEquals(strip.left + rule.dp(16.dp), tabs.first().left, 1f)
        assertEquals(strip.right - rule.dp(16.dp), tabs.last().right, 1f)
        tabs.forEach {
            assertEquals(tabs[0].top, it.top, 1f)
            assertEquals(tabs[0].bottom, it.bottom, 1f)
        }
        assertEquals(rule.dp(50.dp), strip.height, 1f)
    }

    // TC-34
    @Test
    fun issue94_tc34_selectedLabelIsSemiBoldInk0OthersMediumInk1() {
        rule.setTabHost()
        fun check(selected: BookDetailTab) {
            BookDetailTab.entries.forEach { tab ->
                val layout = rule.labelNode(tab).textLayout()
                val style = layout.layoutInput.style
                if (tab == selected) {
                    assertEquals(FontWeight.SemiBold, style.fontWeight)
                    assertEquals(OmnibusPalette.Atrium.ink0, style.color)
                } else {
                    assertEquals(FontWeight.Medium, style.fontWeight)
                    assertEquals(OmnibusPalette.Atrium.ink1, style.color)
                }
                assertEquals(1, layout.lineCount)
            }
        }
        check(BookDetailTab.Overview)
        rule.tapTab(BookDetailTab.Reviews)
        check(BookDetailTab.Reviews)
    }

    // TC-35
    @Test
    fun issue94_tc35_highlightsFitsItsCellWithoutEllipsis() {
        rule.setTabHost()
        BookDetailTab.entries.forEach { selected ->
            rule.tapTab(selected)
            BookDetailTab.entries.forEach { tab ->
                val layout = rule.labelNode(tab).textLayout()
                assertEquals("label of $tab with $selected selected", 1, layout.lineCount)
                assertFalse("label of $tab with $selected selected", layout.hasVisualOverflow)
            }
        }
        val highlightsSize = rule.labelNode(BookDetailTab.Highlights).textLayout().layoutInput.style.fontSize.value
        assertTrue("Highlights shrank below 80% of 16 sp: $highlightsSize", highlightsSize >= 12.8f - 0.01f)
    }

    // TC-36
    @Test
    fun issue94_tc36_tabsExposeRoleTabWithSelectedState() {
        rule.setTabHost(fourFakes())
        BookDetailTab.entries.forEach { tab ->
            val node = rule.tabNode(tab).fetchSemanticsNode()
            assertEquals(Role.Tab, node.config[SemanticsProperties.Role])
            assertEquals(detailText(labels.getValue(tab)), rule.tabNode(tab).fetchSemanticsNode().config[SemanticsProperties.Text].single().text)
        }
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
        rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(1)

        rule.tabNode(BookDetailTab.Reviews).performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Reviews).assertIsSelected()
        rule.onAllNodesWithTag("fake-reviews").assertCountEquals(1)
    }

    // TC-37
    @Test
    fun issue94_tc37_ribbonIsHiddenFromAccessibilityAndTakesNoTouches() {
        rule.setTabHost(fourFakes())
        val ribbon = rule.ribbonNode()
        val config = ribbon.fetchSemanticsNode().config
        assertTrue(config.contains(SemanticsProperties.HideFromAccessibility))
        assertFalse(config.contains(SemanticsActions.OnClick))

        val centre = ribbon.bounds().center
        rule.onRoot().performTouchInput { click(centre) }
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Overview).assertIsSelected()
    }

    // TC-38
    @Test
    fun issue94_tc38_ribbonSnapsToTheSelectedTabUnderReducedMotion() {
        rule.mainClock.autoAdvance = false
        rule.setTabHost(fourFakes(), reducedMotion = true)
        val stripBottom = rule.onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds().bottom
        listOf(BookDetailTab.Files, BookDetailTab.Reviews, BookDetailTab.Highlights, BookDetailTab.Overview).forEach { tab ->
            rule.tabNode(tab).performClick()
            // The snap lands on the frame after the selection's layout invalidation, so two frames, no intermediate step.
            rule.mainClock.advanceTimeByFrame()
            rule.mainClock.advanceTimeByFrame()
            rule.waitForIdle()
            val ribbon = rule.ribbonNode().bounds()
            assertEquals("centre under $tab", rule.tabNode(tab).bounds().center.x, ribbon.center.x, 1f)
            assertEquals(rule.dp(1.dp), ribbon.top - stripBottom, 1f)
            assertEquals(rule.dp(20.dp), ribbon.width, 1f)
            assertEquals(rule.dp(38.dp), ribbon.height, 1f)
        }
    }

    // TC-39
    @Test
    fun issue94_tc39_ribbonIsFilledWithTheBookAccentTint() {
        val tint = Color(0xFF3366CC)
        rule.setTabHost(accent = BookAccent(tint = tint, onTint = Color.White, tintSoft = tint.copy(alpha = 0.16f)))
        val pixels = rule.ribbonNode().captureToImage().toPixelMap()
        val centre = pixels[pixels.width / 2, pixels.height / 2]
        val tolerance = 3f / 255f
        assertEquals(tint.red, centre.red, tolerance)
        assertEquals(tint.green, centre.green, tolerance)
        assertEquals(tint.blue, centre.blue, tolerance)
    }

    // TC-40
    @Test
    fun issue94_tc40_panelRendersItsSlotListInOrderWithTheSharedContext() {
        val seen = mutableListOf<BookDetailSectionContext>()
        fun recorder(tag: String) = com.omnibus.omnibus.feature.bookdetail.BookDetailSection { ctx, modifier ->
            SideEffect { seen += ctx }
            androidx.compose.material3.Text(tag, modifier = modifier.testTag(tag))
        }
        val handle = rule.setTabHost(BookDetailSlots(overviewSections = listOf(recorder("first"), recorder("second"))))
        val first = rule.onNodeWithTag("first").bounds()
        val second = rule.onNodeWithTag("second").bounds()
        assertTrue(first.top < second.top)
        assertTrue(seen.isNotEmpty() && seen.all { it === handle.context })

        val host = rule.onNodeWithTag(com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags.TAB_AREA).bounds()
        val strip = rule.onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds()
        assertEquals(rule.dp(16.dp), first.left - host.left, 1f)
        assertEquals(rule.dp(46.dp), first.top - strip.bottom, 1f)
    }

    // TC-41
    @Test
    fun issue94_tc41_emptySlotListsRenderAnEmptyPanel() {
        rule.setTabHost()
        BookDetailTab.entries.forEach { tab ->
            rule.tapTab(tab)
            rule.onNodeWithTag(BookDetailTabsTestTags.PANEL, useUnmergedTree = true).assertExists()
            val panel = rule.onNodeWithTag(BookDetailTabsTestTags.PANEL, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue("panel of $tab has children", panel.children.isEmpty())
            rule.tabNode(tab).assertIsSelected()
        }
    }
}
