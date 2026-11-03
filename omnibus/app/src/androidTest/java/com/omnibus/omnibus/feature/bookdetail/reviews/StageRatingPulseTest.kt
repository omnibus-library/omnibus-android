package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The stage rating's save pulse. The clock is driven by hand: the pulse waits 240 ms, rises to 1.2 over 180 ms, then
 * falls back to 1.0 over 280 ms, so the scale is 1.0 at 100 ms, between 1.0 and 1.2 at 330 ms, and 1.0 by 900 ms.
 */
class StageRatingPulseTest {

    @get:Rule
    val rule = createComposeRule()

    private val average = StageRatingModel.Average(stars = 4f, average = 4f, count = 1)

    /** Shows the stage rating with [pulse] as its save count. [shown] removes and restores it, to test a return. */
    private fun host(
        pulse: MutableIntState,
        clicks: MutableList<Unit> = mutableListOf(),
        reducedMotion: Boolean = false,
        shown: MutableState<Boolean> = mutableStateOf(true),
    ) = rule.setContent {
        OmnibusTheme(darkTheme = true) {
            Box(Modifier.fillMaxWidth()) {
                if (shown.value) {
                    StageRating(
                        average,
                        Color.Red,
                        onClick = { clicks += Unit },
                        pulse = pulse.intValue,
                        reducedMotion = reducedMotion,
                    )
                }
            }
        }
    }

    /** Advances the clock by [millis], then returns the stars' scale. */
    private fun scaleAfter(millis: Long): Float {
        rule.mainClock.advanceTimeBy(millis)
        rule.waitForIdle()
        return rule.onNodeWithTag(ReviewsTestTags.STAGE_RATING).fetchSemanticsNode().config[StageRatingPulseKey]
    }

    // TC-78 (AC1, R19)
    @Test
    fun tc78_aSaveRaisesTheStarsOnceAndTwoQuickSavesEndAtRest() {
        rule.mainClock.autoAdvance = false
        val pulse = mutableIntStateOf(0)
        val clicks = mutableListOf<Unit>()
        host(pulse, clicks)
        rule.waitForIdle()

        rule.runOnIdle { pulse.intValue = 1 }
        assertEquals(1f, scaleAfter(100), 0.001f)
        val rising = scaleAfter(230)
        assertTrue("scale was $rising at 330 ms", rising > 1f && rising <= 1.2f)
        assertEquals(1f, scaleAfter(570), 0.001f)

        rule.runOnIdle { pulse.intValue = 2 }
        scaleAfter(100)
        rule.runOnIdle { pulse.intValue = 3 }
        assertEquals(1f, scaleAfter(1_000), 0.001f)

        rule.onNodeWithTag(ReviewsTestTags.STAGE_RATING).assertHasClickAction().performClick()
        rule.waitForIdle()
        assertEquals(1, clicks.size)
    }

    // TC-79 (R19)
    @Test
    fun tc79_noPulseOnFirstCompositionRecompositionOrReturn() {
        rule.mainClock.autoAdvance = false
        val pulse = mutableIntStateOf(3)
        val shown = mutableStateOf(true)
        host(pulse, shown = shown)
        rule.waitForIdle()

        assertEquals(1f, scaleAfter(0), 0.001f)
        assertEquals(1f, scaleAfter(330), 0.001f)
        assertEquals(1f, scaleAfter(570), 0.001f)

        rule.runOnIdle { pulse.intValue = 3 }
        assertEquals(1f, scaleAfter(330), 0.001f)

        rule.runOnIdle { shown.value = false }
        rule.waitForIdle()
        rule.runOnIdle { shown.value = true }
        assertEquals(1f, scaleAfter(0), 0.001f)
        assertEquals(1f, scaleAfter(330), 0.001f)
        assertEquals(1f, scaleAfter(570), 0.001f)
    }

    // TC-79 (R19)
    @Test
    fun tc79_reducedMotionSkipsThePulse() {
        rule.mainClock.autoAdvance = false
        val pulse = mutableIntStateOf(3)
        host(pulse, reducedMotion = true)
        rule.waitForIdle()

        rule.runOnIdle { pulse.intValue = 4 }
        assertEquals(1f, scaleAfter(0), 0.001f)
        assertEquals(1f, scaleAfter(330), 0.001f)
        assertEquals(1f, scaleAfter(570), 0.001f)
    }
}
