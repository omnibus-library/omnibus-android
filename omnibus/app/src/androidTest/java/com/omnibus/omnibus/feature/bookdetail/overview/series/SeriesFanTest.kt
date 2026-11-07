package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The stateless [SeriesFan]: the taps, scrolling and semantics. SeriesFanScreenshotTest covers how the hand and the
 * row look.
 */
class SeriesFanTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ComposeContentTestRule.cardNode(uuid: String) = onNodeWithTag(SeriesFanTestTags.card(uuid))

    private fun ComposeContentTestRule.row() = onNodeWithTag(SeriesFanTestTags.ROW)

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
}
