package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.click
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** The inputs a scrubber test drives, so a test can change them between frames. */
private class ScrubberInputs {
    var position by mutableStateOf(100.0)
    var rate by mutableStateOf(1.0)
    var hasChapters by mutableStateOf(true)
    var length by mutableStateOf(600.0)
    var start by mutableStateOf(1000.0)
    val seeks = mutableListOf<Double>()
}

private fun ComposeContentTestRule.setScrubber(inputs: ScrubberInputs) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize()) {
                PlayerScrubber(
                    spanStartSeconds = inputs.start,
                    spanLengthSeconds = inputs.length,
                    spanPositionSeconds = inputs.position,
                    durationSeconds = 7200.0,
                    rate = inputs.rate,
                    hasChapters = inputs.hasChapters,
                    accent = Color(0xFFF0A556),
                    onSeek = { inputs.seeks += it },
                )
            }
        }
    }
}

class PlayerScrubberTest {

    @get:Rule
    val rule = createComposeRule()

    // R11, P-SCR-9, AC3: the three labels at 1x, with chapters
    @Test
    fun labelsShowElapsedBookLeftAndSpanLeftAtRate() {
        val inputs = ScrubberInputs()
        rule.setScrubber(inputs)

        rule.onNodeWithTag(PlayerTestTags.LABEL_ELAPSED).assertTextEquals("1:40")
        rule.onNodeWithTag(PlayerTestTags.LABEL_BOOK_LEFT).assertTextEquals("1h 41m left")
        rule.onNodeWithTag(PlayerTestTags.LABEL_SPAN_LEFT).assertTextEquals("8:20 left")

        // At 2x the left labels halve, and the elapsed label doesn't change.
        rule.runOnIdle { inputs.rate = 2.0 }
        rule.waitForIdle()
        rule.onNodeWithTag(PlayerTestTags.LABEL_ELAPSED).assertTextEquals("1:40")
        rule.onNodeWithTag(PlayerTestTags.LABEL_BOOK_LEFT).assertTextEquals("50m left")
        rule.onNodeWithTag(PlayerTestTags.LABEL_SPAN_LEFT).assertTextEquals("4:10 left")

        val elapsed = rule.onNodeWithTag(PlayerTestTags.LABEL_ELAPSED).fetchSemanticsNode().boundsInRoot
        val centre = rule.onNodeWithTag(PlayerTestTags.LABEL_BOOK_LEFT).fetchSemanticsNode().boundsInRoot
        val right = rule.onNodeWithTag(PlayerTestTags.LABEL_SPAN_LEFT).fetchSemanticsNode().boundsInRoot
        assertTrue("elapsed should sit left of the centre label", elapsed.right <= centre.left + 1f)
        assertTrue("the centre label should sit left of the right label", centre.right <= right.left + 1f)
    }

    // R11, P-SCR-AC5: no centre label without chapters
    @Test
    fun centreLabelHiddenWithoutChapters() {
        val inputs = ScrubberInputs()
        inputs.hasChapters = false
        inputs.start = 0.0
        inputs.length = 7200.0
        rule.setScrubber(inputs)

        rule.onAllNodesWithTag(PlayerTestTags.LABEL_BOOK_LEFT).assertCountEquals(0)
        rule.onNodeWithTag(PlayerTestTags.LABEL_ELAPSED).assertTextEquals("1:40")
        rule.onNodeWithTag(PlayerTestTags.LABEL_SPAN_LEFT).assertTextEquals("1:58:20 left")
    }

    // R11: under a second left reads 0:00
    @Test
    fun rightLabelReadsZeroUnderOneSecond() {
        val inputs = ScrubberInputs()
        inputs.position = 599.5
        rule.setScrubber(inputs)

        rule.onNodeWithTag(PlayerTestTags.LABEL_SPAN_LEFT).assertTextEquals("0:00 left")
    }

    // R10, P-SCR-8, AC3: a tap seeks to book time inside the chapter span
    @Test
    fun tapSeeksToBookTimeInsideTheChapterSpan() {
        val inputs = ScrubberInputs()
        rule.setScrubber(inputs)

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput { click(Offset(width * 0.25f, height / 2f)) }
        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput { click(Offset(width * 0.75f, height / 2f)) }
        rule.waitForIdle()

        assertEquals(2, inputs.seeks.size)
        assertTrue("a tap at 25% should seek near a quarter of the span", abs((inputs.seeks[0] - 1000.0) / 600.0 - 0.25) < 0.05)
        assertTrue("a tap at 75% should seek near three quarters of the span", abs((inputs.seeks[1] - 1000.0) / 600.0 - 0.75) < 0.05)
    }

    // R10: a drag seeks on release only
    @Test
    fun dragSeeksOnReleaseOnly() {
        val inputs = ScrubberInputs()
        rule.setScrubber(inputs)

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput {
            down(Offset(width * 0.1f, height / 2f))
            moveTo(Offset(width * 0.6f, height / 2f))
        }
        rule.waitForIdle()
        assertEquals(0, inputs.seeks.size)

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput { up() }
        rule.waitForIdle()
        assertEquals(1, inputs.seeks.size)
        assertTrue("the release should seek near 60% of the span", abs(inputs.seeks[0] - (1000.0 + 0.6 * 600.0)) < 30.0)
    }

    // R10, R11: while the finger is down, all three labels follow the drag
    @Test
    fun labelsFollowTheDragBeforeRelease() {
        val inputs = ScrubberInputs()
        rule.setScrubber(inputs)

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput {
            down(Offset(width * 0.75f, height / 2f))
            moveTo(Offset(width * 0.75f, height / 2f))
        }
        rule.waitForIdle()

        val elapsed = clockSeconds(rule.onNodeWithTag(PlayerTestTags.LABEL_ELAPSED).textOf())
        assertTrue("elapsed should follow the finger near 450 s, was $elapsed", abs(elapsed - 450) < 30)

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput { up() }
        rule.waitForIdle()
    }

    // R10: the thumb holds the released value until playback catches up
    @Test
    fun thumbHoldsReleasedValueUntilPlaybackCatchesUp() {
        val inputs = ScrubberInputs()
        rule.setScrubber(inputs)
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput { click(Offset(width * 0.75f, height / 2f)) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val released = clockSeconds(rule.onNodeWithTag(PlayerTestTags.LABEL_ELAPSED).textOf())
        assertTrue("the held value should be near 450 s, was $released", abs(released - 450) < 30)

        // The engine lands on the seek; once the position is within 1.5 s the hold clears.
        rule.runOnIdle { inputs.position = released + 0.5 }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        rule.runOnIdle { inputs.position = 300.0 }
        rule.mainClock.advanceTimeBy(100)
        rule.waitForIdle()
        rule.onNodeWithTag(PlayerTestTags.LABEL_ELAPSED).assertTextEquals("5:00")
    }

    // R10: an unconfirmed seek falls back to the playback position after one second
    @Test
    fun heldValueTimesOutAfterOneSecond() {
        val inputs = ScrubberInputs()
        rule.setScrubber(inputs)
        rule.mainClock.autoAdvance = false

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput { click(Offset(width * 0.75f, height / 2f)) }
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        val held = clockSeconds(rule.onNodeWithTag(PlayerTestTags.LABEL_ELAPSED).textOf())
        assertTrue("the held value should be near 450 s, was $held", abs(held - 450) < 30)

        rule.mainClock.advanceTimeBy(1100)
        rule.waitForIdle()
        rule.onNodeWithTag(PlayerTestTags.LABEL_ELAPSED).assertTextEquals("1:40")
    }

    // R10: an empty span is inert
    @Test
    fun scrubberIsInertWhenSpanIsEmpty() {
        val inputs = ScrubberInputs()
        inputs.length = 0.0
        rule.setScrubber(inputs)

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput { click(Offset(width * 0.5f, height / 2f)) }
        rule.waitForIdle()

        assertEquals(0, inputs.seeks.size)
        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
    }

    // R10, P-SCR-AC5: with no chapters the scrubber covers the whole book
    @Test
    fun noChapterScrubberCoversWholeBook() {
        val inputs = ScrubberInputs()
        inputs.hasChapters = false
        inputs.start = 0.0
        inputs.length = 7200.0
        rule.setScrubber(inputs)

        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).performTouchInput { click(Offset(width * 0.5f, height / 2f)) }
        rule.waitForIdle()

        assertEquals(1, inputs.seeks.size)
        assertTrue("a tap at the middle should seek near the middle of the book", abs(inputs.seeks[0] - 3600.0) < 200.0)
    }
}
