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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
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
 * The review sheet's star rows: taps, drags, the bubble, semantics, and the motion, with reduced motion on and off. Every
 * gesture is placed as a fraction of the row's own width, so no test pins a window size.
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
    fun `TC-84 a tap sets the half star under it`() {
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
    fun `TC-85 a tap on the current rating clears it, and another step does not`() {
        val changes = mutableListOf<Float?>()
        setContentWith { ReviewSheetStars(3f, { changes += it }, Color.Red, reducedMotion = true) }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        stars.performTouchInput { click(Offset(width * 0.55f, height / 2f)) }
        stars.performTouchInput { click(Offset(width * 0.25f, height / 2f)) }

        assertEquals(listOf(null, 1.5f), changes)
    }

    @Test
    fun `TC-86 a drag past slop updates the rating live and reports nothing on release`() {
        val changes = mutableListOf<Float?>()
        setContentWith { ReviewSheetStars(null, { changes += it }, Color.Red, reducedMotion = true) }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        var beforeUp = emptyList<Float?>()
        stars.performTouchInput {
            down(Offset(width * 0.15f, height / 2f))
            moveTo(Offset(width * 0.35f, height / 2f))
            moveTo(Offset(width * 0.55f, height / 2f))
            moveTo(Offset(width * 0.75f, height / 2f))
            beforeUp = changes.toList()
            up()
        }

        assertTrue("the drag reports while the finger is down", beforeUp.isNotEmpty())
        val steps = beforeUp.filterNotNull()
        assertTrue("the steps do not go down", steps.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(4f, steps.last(), 0f)
        assertEquals("nothing is reported on release", beforeUp, changes.toList())
    }

    @Test
    fun `TC-87 the bubble shows the number while the finger is down, and a release commits it`() {
        val changes = mutableListOf<Float?>()
        setContentWith { ReviewSheetStars(null, { changes += it }, Color.Red, reducedMotion = true) }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        stars.performTouchInput { down(Offset(width * 0.65f, height / 2f)) }
        stars.assert(SemanticsMatcher.expectValue(ReviewStarsBubbleKey, "3.5"))
        assertTrue("nothing is committed while the finger is down", changes.isEmpty())

        stars.performTouchInput { up() }
        stars.assert(SemanticsMatcher.keyNotDefined(ReviewStarsBubbleKey))
        assertEquals(listOf(3.5f), changes)
    }

    @Test
    fun `TC-88 a vertical swipe the parent scroll takes changes nothing`() {
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
    fun `TC-89 a horizontal drag on the stars inside the scroll changes the rating and not the scroll`() {
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
    fun `TC-90 right-to-left layouts mirror the taps and the drag`() {
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
    fun `TC-91 the stars are one adjustable node with the rating as its state`() {
        setContentWith { ReviewSheetStars(4.5f, {}, Color.Red, reducedMotion = true) }

        rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))
            .assert(hasStateDescription("4.5 of 5 stars, Loved it"))
            .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, rangeOf(4.5f)))
        rule.onAllNodes(hasTestTag(ReviewSheetTestTags.STARS)).assertCountEquals(1)
    }

    @Test
    fun `TC-91 an unrated row reads Not rated`() {
        setContentWith { ReviewSheetStars(null, {}, Color.Red, reducedMotion = true) }
        rule.onNode(hasTestTag(ReviewSheetTestTags.STARS)).assert(hasStateDescription("Not rated"))
    }

    @Test
    fun `TC-92 set progress reports the half step, and zero clears`() {
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
    fun `TC-93 the readout is a polite live region that follows the rating`() {
        val rating = mutableStateOf<Float?>(4.5f)
        setContentWith { ReviewSheetStars(rating.value, { rating.value = it }, Color.Red, reducedMotion = true) }
        val readout = rule.onNode(hasTestTag(ReviewSheetTestTags.READOUT))

        readout.assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        readout.assertTextEquals("4.5 Loved it")

        rating.value = 3.5f
        rule.waitForIdle()
        readout.assertTextEquals("3.5 Liked it a lot")
    }

    @Test
    fun `TC-94 the compact stars behave like the large row, are smaller, and keep a 48dp target`() {
        val changes = mutableListOf<Float?>()
        setContentWith {
            Column {
                ReviewSheetStars(4.5f, {}, Color.Red, reducedMotion = true)
                ReviewSheetCompactStars(4.5f, { changes += it }, Color.Red)
            }
        }
        val compact = rule.onNode(hasTestTag(ReviewSheetTestTags.COMPACT_STARS))
        val large = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        compact.assert(hasStateDescription("4.5 of 5 stars, Loved it"))
        compact.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        compact.assertHeightIsAtLeast(48.dp)
        assertTrue(
            "the compact row is shorter than the large one",
            compact.fetchSemanticsNode().size.height < large.fetchSemanticsNode().size.height,
        )

        compact.performTouchInput { click(Offset(width * 0.05f, height / 2f)) }
        assertEquals(listOf(0.5f), changes)
    }

    @Test
    fun `TC-95 reduced motion keeps the star motion at rest, and the bubble still shows`() {
        rule.mainClock.autoAdvance = false
        val rating = mutableStateOf<Float?>(3f)
        setContentWith {
            ReviewSheetStars(rating.value, { rating.value = it }, Color.Red, reducedMotion = true)
        }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))
        stars.assert(SemanticsMatcher.expectValue(ReviewStarsMotionKey, StarsMotion.Rest))

        rating.value = 5f
        rule.waitForIdle()
        stars.assert(SemanticsMatcher.expectValue(ReviewStarsMotionKey, StarsMotion.Rest))

        stars.performTouchInput { down(Offset(width * 0.5f, height / 2f)) }
        stars.assert(SemanticsMatcher.expectValue(ReviewStarsMotionKey, StarsMotion.Rest))
        stars.assert(SemanticsMatcher.keyIsDefined(ReviewStarsBubbleKey))
        rule.onNode(hasTestTag(ReviewSheetTestTags.READOUT)).assertTextEquals("2.5 Mixed feelings")
        stars.performTouchInput { up() }
    }

    @Test
    fun `TC-96 with motion on, a pop, a ripple and a lift run and then settle`() {
        rule.mainClock.autoAdvance = false
        val rating = mutableStateOf<Float?>(null)
        setContentWith {
            ReviewSheetStars(rating.value, { rating.value = it }, Color.Red, reducedMotion = false)
        }
        val stars = rule.onNode(hasTestTag(ReviewSheetTestTags.STARS))

        rating.value = 4.5f
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        stars.assert(SemanticsMatcher.expectValue(ReviewStarsMotionKey, StarsMotion(lifted = false, pop = 4, ripple = false)))

        rating.value = 5f
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        stars.assert(SemanticsMatcher.expectValue(ReviewStarsMotionKey, StarsMotion(lifted = false, pop = null, ripple = true)))

        // Holding the finger lifts the stars. The press moves the preview, so the pop may be running, and only the lift is asserted.
        stars.performTouchInput { down(Offset(width * 0.5f, height / 2f)) }
        rule.waitForIdle()
        stars.assert(liftedIs(true))

        stars.performTouchInput { up() }
        rule.mainClock.advanceTimeBy(1000L)
        stars.assert(SemanticsMatcher.expectValue(ReviewStarsMotionKey, StarsMotion.Rest))
    }

    /** Matches a star row whose motion has its lift set to [expected]. */
    private fun liftedIs(expected: Boolean) = SemanticsMatcher("lifted is $expected") {
        it.config.getOrNull(ReviewStarsMotionKey)?.lifted == expected
    }

    /** The range of a progress from 0 to 5 in nine steps, at [value]. */
    private fun rangeOf(value: Float) = ProgressBarRangeInfo(value, 0f..5f, steps = 9)
}
