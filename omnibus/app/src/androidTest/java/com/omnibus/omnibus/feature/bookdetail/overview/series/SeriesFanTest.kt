package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The stateless [SeriesFan]: the gathered hand, the spread row, the taps, the motion and the semantics.
 *
 * No case pins a screen size. Widths come from the section's own bounds and every expected distance is a density
 * conversion of a design dp.
 */
class SeriesFanTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ComposeContentTestRule.cardNode(uuid: String) = onNodeWithTag(SeriesFanTestTags.card(uuid))

    private fun ComposeContentTestRule.row() = onNodeWithTag(SeriesFanTestTags.ROW)

    private fun ComposeContentTestRule.headingRow() = onNodeWithTag(SeriesFanTestTags.HEADING_ROW)

    // TC-67
    @Test
    fun issue104_tc67_gatheredRaisesThisBook() {
        rule.setFan(FanState(fanModel(5, current = 2)))
        rule.waitForIdle()
        (1..5).forEach { rule.cardNode("m$it").assertIsDisplayed() }

        val raised = rule.cardNode("m3").layoutBounds().top
        val others = listOf(1, 2, 4, 5).map { rule.cardNode("m$it").layoutBounds().top }
        others.forEach { assertEquals(others.first(), it, 1f) }
        assertEquals(rule.px(10.dp), others.first() - raised, 1f)

        val lefts = (1..5).map { rule.cardNode("m$it").layoutBounds().left }
        assertTrue("left edges rise left to right: $lefts", lefts.zipWithNext().all { (a, b) -> a < b })
    }

    // TC-68
    @Test
    fun issue104_tc68_ribbonOnThisBookOnly() {
        rule.setFan(FanState(fanModel(5, current = 2)))
        rule.waitForIdle()
        rule.onAllNodesWithTag(SeriesFanTestTags.RIBBON, useUnmergedTree = true).assertCountEquals(1)

        val card = rule.cardNode("m3").layoutBounds()
        val ribbon = rule.onNodeWithTag(SeriesFanTestTags.RIBBON, useUnmergedTree = true).bounds()
        assertTrue("ribbon inside the card horizontally", ribbon.left >= card.left && ribbon.right <= card.right)
        assertTrue("ribbon right of centre", ribbon.center.x > card.center.x)
        assertTrue("ribbon top within 4 dp above the card", ribbon.top <= card.top && ribbon.top >= card.top - rule.px(4.dp))
        assertTrue(
            "ribbon bottom in the card's top quarter",
            ribbon.bottom > card.top && ribbon.bottom <= card.top + card.height / 4f,
        )
    }

    // TC-70 (amended for #153: the gathered step is capped)
    @Test
    fun issue104_tc70_gatheredFitsTheSectionAndDoesNotScroll() {
        val state = FanState(fanModel(2, 0))
        rule.setFan(state)
        rule.waitForIdle()
        for (n in listOf(2, 8, 40)) {
            rule.runOnIdle { state.model = fanModel(n, 0) }
            rule.waitForIdle()
            val section = rule.headingRow().bounds()
            val sectionDp = section.width / rule.px(1.dp)
            val handDp = minOf(sectionDp, SeriesFanLayout.CARD_WIDTH_DP + (n - 1) * SeriesFanLayout.GATHERED_MAX_STEP_DP)
            assertEquals("first card left edge, n=$n", section.left, rule.cardNode("m1").layoutBounds().left, 1f)
            assertEquals("last card right edge, n=$n", section.left + rule.px(handDp.dp), rule.cardNode("m$n").layoutBounds().right, 1f)
            (1..n).forEach { i ->
                val card = rule.cardNode("m$i").layoutBounds()
                assertTrue("card $i inside the section, n=$n", card.left >= section.left - 1f && card.right <= section.right + 1f)
            }
            assertTrue("neighbours overlap, n=$n", rule.cardNode("m2").layoutBounds().left < rule.cardNode("m1").layoutBounds().right)
            assertEquals("no scroll when gathered, n=$n", 0, state.scroll.maxValue)
        }
    }

    // TC-71
    @Test
    fun issue104_tc71_tappingAnyGatheredCoverSpreads() {
        val state = FanState(fanModel(5, current = 2))
        rule.setFan(state)
        rule.waitForIdle()
        (1..5).forEach { rule.cardNode("m$it").performSemanticsAction(SemanticsActions.OnClick) }
        rule.cardNode("m3").performClick()
        rule.waitForIdle()
        assertEquals(6, state.toggleCount)
        assertTrue(state.opened.isEmpty())
    }

    // TC-72
    @Test
    fun issue104_tc72_spreadCardsSitInARow() {
        rule.setFan(FanState(fanModel(6, current = 0), spread = true))
        rule.waitForIdle()
        val boxes = (1..6).map { rule.cardNode("m$it").layoutBounds() }
        val spacing = boxes[1].left - boxes[0].left
        boxes.zipWithNext().forEach { (a, b) ->
            assertTrue("no overlap", b.left >= a.right)
            assertEquals("equal spacing", spacing, b.left - a.left, 1f)
            assertEquals("120 over 104", 120f / 104f, spacing / a.width, 0.02f * (120f / 104f))
        }
    }

    // TC-73
    @Test
    fun issue104_tc73_spreadMetadataWording() {
        val list = members(5) { if (it == 5) "" else "$it" }
        rule.setFan(FanState(fanModel(5, current = 1, list = list), spread = true))
        rule.waitForIdle()
        assertEquals("Title 1, Book 1", rule.cardNode("m1").description())
        assertEquals("Title 2, Book 2 · This book", rule.cardNode("m2").description())
        assertEquals("Title 3, Book 3 · Up next", rule.cardNode("m3").description())
        assertEquals("Title 4, Book 4", rule.cardNode("m4").description())
        assertEquals("Title 5", rule.cardNode("m5").description())
    }

    // TC-74
    @Test
    fun issue104_tc74_rowBleedsPastThePadding() {
        rule.setFan(FanState(fanModel(4, current = 0), spread = true))
        rule.waitForIdle()
        val section = rule.headingRow().bounds()
        val row = rule.row().bounds()
        assertEquals("row starts 16 dp before the section", section.left - rule.px(16.dp), row.left, 1f)
        assertEquals("row ends 16 dp after the section", section.right + rule.px(16.dp), row.right, 1f)
        assertEquals("first card aligns with the section", section.left, rule.cardNode("m1").layoutBounds().left, 1f)
    }

    // TC-75
    @Test
    fun issue104_tc75_longSpreadScrollsSideways() {
        val state = FanState(fanModel(40, current = 0), spread = true)
        rule.setFan(state)
        rule.waitForIdle()
        assertTrue("the row can scroll", state.scroll.maxValue > 0)
        rule.cardNode("m40").assertIsNotDisplayed()
        rule.row().performScrollToNode(hasTestTag(SeriesFanTestTags.card("m40")))
        rule.cardNode("m40").assertIsDisplayed()
    }

    // TC-76
    @Test
    fun issue104_tc76_spreadCentresThisBook() {
        val state = FanState(fanModel(40, current = 0))
        rule.setFan(state)
        rule.waitForIdle()
        for (position in listOf(0, 20, 39)) {
            // A fresh subtree and scroll position for each case.
            rule.runOnIdle {
                state.epoch++
                state.scroll = ScrollState(0)
                state.model = fanModel(40, current = position)
                state.spread = false
            }
            rule.waitForIdle()
            rule.runOnIdle { state.spread = true }
            rule.waitForIdle()

            val row = rule.row().bounds()
            val expected = rule.px(spreadScrollTargetDp(position, 40, row.width / rule.px(1.dp)).dp)
            assertEquals("scroll for position $position", expected, state.scroll.value.toFloat(), 1f)
            if (position == 20) {
                assertEquals("this card centred", row.center.x, rule.cardNode("m21").layoutBounds().center.x, 1f)
            }
            if (position == 0) assertEquals(0, state.scroll.value)
            if (position == 39) assertEquals(state.scroll.maxValue, state.scroll.value)
            rule.cardNode("m${position + 1}").assertIsDisplayed()
        }
    }

    // TC-77
    @Test
    fun issue104_tc77_tappingAnotherBookOpensIt() {
        val state = FanState(fanModel(5, current = 0), spread = true)
        rule.setFan(state)
        rule.waitForIdle()
        rule.cardNode("m2").performClick()
        rule.waitForIdle()
        assertEquals(listOf("m2"), state.opened)
        assertEquals(0, state.toggleCount)
    }

    // TC-78
    @Test
    fun issue104_tc78_scrolledCardOpens() {
        val state = FanState(fanModel(40, current = 0), spread = true)
        rule.setFan(state)
        rule.waitForIdle()
        rule.row().performScrollToNode(hasTestTag(SeriesFanTestTags.card("m31")))
        rule.cardNode("m31").performClick()
        rule.waitForIdle()
        assertEquals(listOf("m31"), state.opened)
    }

    // TC-79
    @Test
    fun issue104_tc79_tappingThisBookGathers() {
        val state = FanState(fanModel(5, current = 2), spread = true)
        rule.setFan(state)
        rule.waitForIdle()
        rule.cardNode("m3").performClick()
        rule.waitForIdle()
        assertEquals(1, state.toggleCount)
        assertTrue(state.opened.isEmpty())
    }

    // TC-80
    @Test
    fun issue104_tc80_gatheringScrollsBackToStart() {
        val state = FanState(fanModel(40, current = 0), spread = true)
        rule.setFan(state)
        rule.waitForIdle()
        rule.scrollTo(state.scroll)
        assertTrue("scrolled away", state.scroll.value > 0)

        rule.runOnIdle { state.spread = false }
        rule.waitForIdle()
        assertEquals(0, state.scroll.value)
        assertEquals(0, state.scroll.maxValue)
        val section = rule.headingRow().bounds()
        assertEquals(section.left, rule.cardNode("m1").layoutBounds().left, 1f)
        assertEquals(section.right, rule.cardNode("m40").layoutBounds().right, 1f)
    }

    // TC-81
    @Test
    fun issue104_tc81_absentBookIsUnmarked() {
        rule.setFan(FanState(fanModel(4, current = null)))
        rule.waitForIdle()
        rule.onAllNodesWithTag(SeriesFanTestTags.RIBBON, useUnmergedTree = true).assertCountEquals(0)
        val tops = (1..4).map { rule.cardNode("m$it").layoutBounds().top }
        tops.forEach { assertEquals(tops.first(), it, 1f) }
        (1..4).forEach { i ->
            val node = rule.cardNode("m$i")
            assertFalse("no Selected on card $i", node.fetchSemanticsNode().config.contains(SemanticsProperties.Selected))
            val description = node.description().orEmpty()
            assertFalse(description.contains("This book") || description.contains("Up next"))
        }
    }

    // TC-83
    @Test
    fun issue104_tc83_cardRoleAndSelected() {
        val state = FanState(fanModel(5, current = 2))
        rule.setFan(state)
        rule.waitForIdle()
        for (spread in listOf(false, true)) {
            (1..5).forEach { i ->
                val node = rule.cardNode("m$i")
                assertEquals("role of card $i", Role.Button, node.fetchSemanticsNode().config[SemanticsProperties.Role])
                if (i == 3) {
                    node.assertIsSelected()
                } else {
                    assertFalse(node.fetchSemanticsNode().config.contains(SemanticsProperties.Selected))
                }
            }
            rule.runOnIdle { state.spread = !spread }
            rule.waitForIdle()
        }
    }

    // TC-84
    @Test
    fun issue104_tc84_clickLabels() {
        val state = FanState(fanModel(5, current = 2))
        rule.setFan(state)
        rule.waitForIdle()
        (1..5).forEach { assertEquals("gathered label on card $it", "Show the whole series", rule.cardNode("m$it").clickLabel()) }

        rule.runOnIdle { state.spread = true }
        rule.waitForIdle()
        (1..5).forEach { i ->
            val expected = if (i == 3) "Gather the series" else "Open book"
            assertEquals("spread label on card $i", expected, rule.cardNode("m$i").clickLabel())
        }
    }

    // TC-85
    @Test
    fun issue104_tc85_oneNodePerCard() {
        val state = FanState(fanModel(5, current = 2))
        rule.setFan(state)
        rule.waitForIdle()
        for (spread in listOf(false, true)) {
            rule.onAllNodes(hasClickAction().and(hasAnyAncestor(hasTestTag(SeriesFanTestTags.ROW))))
                .assertCountEquals(5)
            rule.onAllNodes(hasAnyAncestor(hasTestTag(SeriesFanTestTags.ROW))).assertCountEquals(5)
            rule.runOnIdle { state.spread = !spread }
            rule.waitForIdle()
        }
    }

    // TC-86
    @Test
    fun issue104_tc86_cardsMeetTheTouchTarget() {
        val state = FanState(fanModel(2, current = 0))
        rule.setFan(state)
        rule.waitForIdle()
        for (n in listOf(2, 40)) {
            for (spread in listOf(false, true)) {
                rule.runOnIdle {
                    state.model = fanModel(n, current = 0)
                    state.spread = spread
                }
                rule.waitForIdle()
                (1..n).forEach { i ->
                    rule.cardNode("m$i").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
                }
            }
        }
    }

    // TC-87
    @Test
    fun issue104_tc87_reducedMotionSnaps() {
        rule.mainClock.autoAdvance = false
        val state = FanState(fanModel(4, current = 0))
        rule.setFan(state, reducedMotion = true)
        rule.waitForIdle()

        rule.runOnIdle { state.spread = true }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val lastLeft = rule.cardNode("m4").layoutBounds().left
        val rowHeight = rule.row().bounds().height
        rule.mainClock.advanceTimeBy(2000)
        assertEquals("last card settles at once", lastLeft, rule.cardNode("m4").layoutBounds().left, 1f)
        assertEquals("row settles at once", rowHeight, rule.row().bounds().height, 1f)

        rule.runOnIdle { state.spread = false }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val gatheredLeft = rule.cardNode("m4").layoutBounds().left
        rule.mainClock.advanceTimeBy(2000)
        assertEquals("gather settles at once", gatheredLeft, rule.cardNode("m4").layoutBounds().left, 1f)
    }

    // TC-88
    @Test
    fun issue104_tc88_animatedSpreadPassesThroughIntermediateFrames() {
        rule.mainClock.autoAdvance = false
        val state = FanState(fanModel(4, current = 0))
        rule.setFan(state, reducedMotion = false)
        rule.waitForIdle()
        val gatheredLeft = rule.cardNode("m4").layoutBounds().left

        rule.runOnIdle { state.spread = true }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
        val midLeft = rule.cardNode("m4").layoutBounds().left
        rule.mainClock.advanceTimeBy(700)
        val finalLeft = rule.cardNode("m4").layoutBounds().left

        val fraction = (midLeft - gatheredLeft) / (finalLeft - gatheredLeft)
        assertTrue("at 100 ms the card is part way: $fraction", fraction > 0f && fraction < 1f)
        assertEquals(
            "the spread's final position: card 4 is 3 x 120 dp from the section's left edge",
            rule.headingRow().bounds().left + rule.px(120.dp) * 3,
            rule.cardNode("m4").layoutBounds().left,
            1f,
        )
    }

    // TC-89
    @Test
    fun issue104_tc89_rowHeightGrowsWithTheSpread() {
        rule.mainClock.autoAdvance = false
        val state = FanState(fanModel(4, current = 0))
        rule.setFan(state, reducedMotion = false)
        rule.waitForIdle()
        val gathered = rule.row().bounds().height

        rule.runOnIdle { state.spread = true }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(100)
        val mid = rule.row().bounds().height
        rule.mainClock.advanceTimeBy(700)
        val settled = rule.row().bounds().height

        assertTrue("the row is between the two heights: $mid", mid > gathered + 0.5f && mid < settled - 0.5f)
        assertEquals("settled over gathered", 250f / 190f, settled / gathered, 0.01f * (250f / 190f))
    }

    // TC-90
    @Test
    fun issue104_tc90_rtlMirrorsTheHand() {
        val state = FanState(fanModel(4, current = 0))
        rule.setFan(state, direction = LayoutDirection.Rtl)
        rule.waitForIdle()

        val section = rule.headingRow().bounds()
        assertTrue("this book is raised", rule.cardNode("m1").layoutBounds().top < rule.cardNode("m2").layoutBounds().top)
        rule.onAllNodesWithTag(SeriesFanTestTags.RIBBON, useUnmergedTree = true).assertCountEquals(1)

        rule.runOnIdle { state.spread = true }
        rule.waitForIdle()
        val lefts = (1..4).map { rule.cardNode("m$it").layoutBounds().left }
        assertTrue("left edges decrease with index: $lefts", lefts.zipWithNext().all { (a, b) -> a > b })
        assertEquals("card 0 meets the section's right edge", section.right, rule.cardNode("m1").layoutBounds().right, 1f)

        rule.cardNode("m2").performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()
        assertEquals(listOf("m2"), state.opened)
    }
}
