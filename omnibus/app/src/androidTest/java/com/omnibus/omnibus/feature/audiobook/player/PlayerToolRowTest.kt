package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assert
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class PlayerToolRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setToolRow(rate: Double, hasEbook: Boolean, actions: PlayerActions) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    PlayerToolRow(rate = rate, hasEbook = hasEbook, actions = actions)
                }
            }
        }
    }

    // R14, P-SCR-11: five cells in order, the rate as the speed glyph, equal widths
    @Test
    fun toolRowShowsFiveCellsInOrderWithRateGlyph() {
        val actions = RecordedActions().actions()
        setToolRow(rate = 1.25, hasEbook = true, actions = actions)

        val tags = listOf(
            PlayerTestTags.TOOL_SPEED,
            PlayerTestTags.TOOL_CAR_MODE,
            PlayerTestTags.TOOL_SLEEP,
            PlayerTestTags.TOOL_BOOKMARK,
            PlayerTestTags.TOOL_SYNC,
        )
        val bounds = tags.map { rule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue("the cells should run left to right", left.left < right.left)
            assertTrue("the cells should share a row", abs(left.center.y - right.center.y) < left.height)
        }
        bounds.forEach { assertTrue("the cells should have equal widths", abs(it.width - bounds[0].width) < 1f) }

        rule.onNodeWithTag(PlayerTestTags.TOOL_SPEED).assert(hasText("1.25x"))
        listOf("Speed", "Car Mode", "Sleep", "Bookmark", "Sync here").forEach {
            rule.onNodeWithText(it).assertExists()
        }
    }

    // R14: each cell fires its own action once
    @Test
    fun toolCellsInvokeTheirActions() {
        val recorded = RecordedActions()
        setToolRow(rate = 1.0, hasEbook = true, actions = recorded.actions())

        rule.onNodeWithTag(PlayerTestTags.TOOL_SPEED).performClick()
        rule.onNodeWithTag(PlayerTestTags.TOOL_CAR_MODE).performClick()
        rule.onNodeWithTag(PlayerTestTags.TOOL_SLEEP).performClick()
        rule.onNodeWithTag(PlayerTestTags.TOOL_BOOKMARK).performClick()
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).performClick()
        rule.waitForIdle()

        assertEquals(1, recorded.speed)
        assertEquals(1, recorded.carMode)
        assertEquals(1, recorded.sleep)
        assertEquals(1, recorded.bookmark)
        assertEquals(1, recorded.sync)
    }

    // R14: only the cells with an action show, and they share the width equally
    @Test
    fun toolCellsShowOnlyWhenTheirActionIsSet() {
        val recorded = RecordedActions()
        setToolRow(
            rate = 1.0,
            hasEbook = true,
            actions = PlayerActions(onOpenSpeed = { recorded.speed++ }, onAddBookmark = { recorded.bookmark++ }),
        )

        val speed = rule.onNodeWithTag(PlayerTestTags.TOOL_SPEED).fetchSemanticsNode().boundsInRoot
        val bookmark = rule.onNodeWithTag(PlayerTestTags.TOOL_BOOKMARK).fetchSemanticsNode().boundsInRoot
        rule.onAllNodesWithTag(PlayerTestTags.TOOL_CAR_MODE).assertCountEquals(0)
        rule.onAllNodesWithTag(PlayerTestTags.TOOL_SLEEP).assertCountEquals(0)
        rule.onAllNodesWithTag(PlayerTestTags.TOOL_SYNC).assertCountEquals(0)
        assertTrue("the two cells should share the width", abs(speed.width - bookmark.width) < 1f)
    }

    // R14, P-SCR edge case: Sync here needs an ebook
    @Test
    fun syncHereNeedsAnEbook() {
        setToolRow(
            rate = 1.0,
            hasEbook = false,
            actions = PlayerActions(syncHere = PlayerToolButton(label = "Sync here", enabled = true, onClick = {})),
        )

        rule.onAllNodesWithTag(PlayerTestTags.TOOL_SYNC).assertCountEquals(0)
    }

    // R14, R18, P-A11Y-5: a disabled Sync here is dimmed, not clickable, and says why
    @Test
    fun disabledSyncHereIsNotClickableAndSpeaksWhy() {
        var clicked = 0
        setToolRow(
            rate = 1.0,
            hasEbook = true,
            actions = PlayerActions(
                syncHere = PlayerToolButton(
                    label = "Sync here",
                    enabled = false,
                    onClick = { clicked++ },
                    disabledDescription = "Needs a connection",
                ),
            ),
        )

        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assertIsNotEnabled()
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).performClick()
        rule.waitForIdle()
        assertEquals(0, clicked)
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assert(hasContentDescription("Sync here", substring = true))
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assert(hasContentDescription("Needs a connection", substring = true))
    }
}
