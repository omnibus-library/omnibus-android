package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule

import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The review sheet's star rows: taps, drags and semantics. Every gesture is placed as a fraction of the row's own width,
 * so no test pins a window size.
 */
class ReviewSheetStarsTest {

    @get:Rule
    val rule = createComposeRule()

    /** Lays [content] out on the dark theme, in the given layout direction. */
    private fun setContentWith(rtl: Boolean = false, content: @Composable () -> Unit) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxWidth()) { content() }
                }
            }
        }
    }

    @Test
    fun tc84_aTapSetsTheHalfStarUnderIt() {
        val changes = mutableListOf<Float?>()
        setContentWith { ReviewSheetStars(null, { changes += it }, Color.Red, reducedMotion = true) }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        stars.performTouchInput { click(Offset(width * 0.05f, height / 2f)) }
        stars.performTouchInput { click(Offset(width * 0.15f, height / 2f)) }
        stars.performTouchInput { click(Offset(width * 0.85f, height / 2f)) }
        stars.performTouchInput { click(Offset(width * 0.95f, height / 2f)) }

        assertEquals(listOf(0.5f, 1f, 4.5f, 5f), changes)
    }

    @Test
    fun tc85_aTapOnTheCurrentRatingClearsItAndAnotherStepDoesNot() {
        val changes = mutableListOf<Float?>()
        setContentWith { ReviewSheetStars(3f, { changes += it }, Color.Red, reducedMotion = true) }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        stars.performTouchInput { click(Offset(width * 0.55f, height / 2f)) }
        stars.performTouchInput { click(Offset(width * 0.25f, height / 2f)) }

        assertEquals(listOf(null, 1.5f), changes)
    }

    @Test
    fun tc86_aDragPastSlopUpdatesTheRatingLiveAndReportsNothingOnRelease() {
        val changes = mutableListOf<Float?>()
        setContentWith { ReviewSheetStars(null, { changes += it }, Color.Red, reducedMotion = true) }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        // Each call dispatches its events before it returns, so the reads below see what the drag reported so far.
        stars.performTouchInput { down(Offset(width * 0.15f, height / 2f)) }
        stars.performTouchInput { moveTo(Offset(width * 0.35f, height / 2f)) }
        stars.performTouchInput { moveTo(Offset(width * 0.55f, height / 2f)) }
        stars.performTouchInput { moveTo(Offset(width * 0.75f, height / 2f)) }
        val beforeUp = changes.toList()
        stars.performTouchInput { up() }

        assertTrue("the drag reports while the finger is down", beforeUp.isNotEmpty())
        val steps = beforeUp.filterNotNull()
        assertTrue("the steps do not go down", steps.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(4f, steps.last(), 0f)
        assertEquals("nothing is reported on release", beforeUp, changes.toList())
    }

    @Test
    fun tc88_aVerticalSwipeTheParentScrollTakesChangesNothing() {
        val changes = mutableListOf<Float?>()
        val scroll = ScrollState(0)
        setContentWith {
            Column(Modifier.verticalScroll(scroll)) {
                ReviewSheetStars(null, { changes += it }, Color.Red, reducedMotion = true)
                Spacer(Modifier.height(2000.dp))
            }
        }

        rule.onNode(hasTestTag(ReviewSheetTestTags.STARS)).performTouchInput { swipeUp() }

        assertTrue("the parent scrolled", scroll.value > 0)
        assertTrue("the rating did not change", changes.isEmpty())
    }

    @Test
    fun tc89_aHorizontalDragOnTheStarsInsideTheScrollChangesTheRatingAndNotTheScroll() {
        val changes = mutableListOf<Float?>()
        val scroll = ScrollState(0)
        setContentWith {
            Column(Modifier.verticalScroll(scroll)) {
                ReviewSheetStars(null, { changes += it }, Color.Red, reducedMotion = true)
                Spacer(Modifier.height(2000.dp))
            }
        }

        rule.onNode(hasTestTag(ReviewSheetTestTags.STARS)).performTouchInput { swipeRight() }

        assertTrue("the rating changed", changes.isNotEmpty())
        assertEquals("the parent did not scroll", 0, scroll.value)
    }

    @Test
    fun tc90_rightToLeftLayoutsMirrorTheTapsAndTheDrag() {
        val changes = mutableListOf<Float?>()
        setContentWith(rtl = true) { ReviewSheetStars(null, { changes += it }, Color.Red, reducedMotion = true) }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        // The left edge is the start of the rating scale in right-to-left, so it gives the highest rating.
        stars.performTouchInput { click(Offset(width * 0.05f, height / 2f)) }
        stars.performTouchInput { click(Offset(width * 0.95f, height / 2f)) }
        assertEquals(listOf(5f, 0.5f), changes)

        // A drag from right to left moves up the scale, so the rating rises.
        changes.clear()
        stars.performTouchInput {
            down(Offset(width * 0.75f, height / 2f))
            moveTo(Offset(width * 0.55f, height / 2f))
            moveTo(Offset(width * 0.15f, height / 2f))
            up()
        }
        val steps = changes.filterNotNull()
        assertTrue(steps.isNotEmpty())
        assertTrue("the rating rises with the drag", steps.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(4.5f, steps.last(), 0f)
    }

    @Test
    fun tc91_theStarsAreOneAdjustableNodeWithTheRatingAsItsState() {
        setContentWith { ReviewSheetStars(4.5f, {}, Color.Red, reducedMotion = true) }

        rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))
            .assert(hasStateDescription("4.5 of 5 stars, Loved it"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, rangeOf(4.5f)))
        rule.onAllNodes(hasTestTag(ReviewSheetTestTags.STARS)).assertCountEquals(1)
    }

    @Test
    fun tc92_setProgressReportsTheHalfStepAndZeroClears() {
        val changes = mutableListOf<Float?>()
        setContentWith { ReviewSheetStars(3f, { changes += it }, Color.Red, reducedMotion = true) }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        stars.performSemanticsAction(SemanticsActions.SetProgress) { it(3.5f) }
        stars.performSemanticsAction(SemanticsActions.SetProgress) { it(3.3f) }
        stars.performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
        stars.performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }

        assertEquals(listOf(3.5f, 3.5f, 5f, null), changes)
    }

    @Test
    fun tc93_theReadoutIsAPoliteLiveRegion() {
        setContentWith { ReviewSheetStars(4.5f, {}, Color.Red, reducedMotion = true) }

        rule.onNode(hasTestTag(ReviewSheetTestTags.READOUT))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun tc94_aTapOnTheCompactStarsSetsTheHalfStarUnderIt() {
        val changes = mutableListOf<Float?>()
        setContentWith { ReviewSheetCompactStars(4.5f, { changes += it }, Color.Red) }

        rule.onNode(hasTestTag(ReviewSheetTestTags.COMPACT_STARS)).performTouchInput { click(Offset(width * 0.05f, height / 2f)) }

        assertEquals(listOf(0.5f), changes)
    }

    /** The range of a progress from 0 to 5 in nine steps, at [value]. */
    private fun rangeOf(value: Float) = ProgressBarRangeInfo(value, 0f..5f, steps = 9)
}
