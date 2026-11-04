package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerDismissGestureTest {

    @get:Rule
    val rule = createComposeRule()

    // R17, P-SCR-13, P-A11Y-6: a long downward swipe on the stage closes the player
    @Test
    fun longDownSwipeOnStageCloses() {
        var closed = 0
        rule.setPlayer(fullUiState(), onClose = { closed++ }, reducedMotion = true)

        rule.onNodeWithTag(PlayerTestTags.STAGE).performTouchInput {
            swipeDown(startY = top + height * 0.1f, endY = top + height * 0.7f, durationMillis = 200)
        }
        rule.waitForIdle()
        assertEquals(1, closed)
    }

    // R17, P-A11Y-6 (animations off): a short slow drag springs back at once
    @Test
    fun shortDragOnStageSpringsBack() {
        var closed = 0
        rule.setPlayer(fullUiState(), onClose = { closed++ }, reducedMotion = true)
        val before = rule.boundsOf(PlayerTestTags.COVER)

        rule.onNodeWithTag(PlayerTestTags.STAGE).performTouchInput {
            swipeDown(startY = top + height * 0.1f, endY = top + height * 0.18f, durationMillis = 1000)
        }
        rule.waitForIdle()

        assertEquals(0, closed)
        assertEquals(before.top, rule.boundsOf(PlayerTestTags.COVER).top, 1f)
    }

    // R17, P-A11Y-6: a short slow drag springs back with motion on
    @Test
    fun shortDragSpringsBackAnimated() {
        var closed = 0
        rule.setPlayer(fullUiState(), onClose = { closed++ }, reducedMotion = false)
        val before = rule.boundsOf(PlayerTestTags.COVER)

        rule.onNodeWithTag(PlayerTestTags.STAGE).performTouchInput {
            swipeDown(startY = top + height * 0.1f, endY = top + height * 0.18f, durationMillis = 1000)
        }
        rule.waitForIdle()

        assertEquals(0, closed)
        assertEquals(before.top, rule.boundsOf(PlayerTestTags.COVER).top, 1f)
    }

    // R17: only a drag on the stage can dismiss, so upward drags are ignored
    @Test
    fun upwardDragOnStageIsIgnored() {
        var closed = 0
        rule.setPlayer(fullUiState(), onClose = { closed++ }, reducedMotion = true)
        val before = rule.boundsOf(PlayerTestTags.COVER)

        rule.onNodeWithTag(PlayerTestTags.STAGE).performTouchInput {
            swipeUp(startY = top + height * 0.7f, endY = top + height * 0.1f, durationMillis = 200)
        }
        rule.waitForIdle()

        assertEquals(0, closed)
        assertEquals(before.top, rule.boundsOf(PlayerTestTags.COVER).top, 1f)
    }

    // R17, R10, R13: a long downward swipe that starts on the scrubber or the transport never closes
    @Test
    fun swipeDownOnScrubberOrTransportDoesNotClose() {
        var closed = 0
        rule.setPlayer(fullUiState(), onClose = { closed++ }, reducedMotion = true)
        val root = rule.boundsOf(PlayerTestTags.ROOT)

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput {
            swipeDown(startY = centerY, endY = root.bottom - 2f, durationMillis = 300)
        }
        rule.waitForIdle()

        rule.onNodeWithTag(PlayerTestTags.PLAY_PAUSE).performTouchInput {
            swipeDown(startY = centerY, endY = root.bottom - 2f, durationMillis = 300)
        }
        rule.waitForIdle()

        assertEquals(0, closed)
    }

    // R17: a horizontal swipe across the stage is not a dismiss
    @Test
    fun horizontalSwipeOnStageDoesNotClose() {
        var closed = 0
        rule.setPlayer(fullUiState(), onClose = { closed++ }, reducedMotion = true)

        rule.onNodeWithTag(PlayerTestTags.STAGE).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(0, closed)
    }
}
