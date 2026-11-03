package com.omnibus.omnibus.feature.bookdetail.reviews.stars

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The star rating input: its taps, drags and vertical swipes, its right-to-left layout, and its accessibility node. Every
 * gesture is placed by fractions of the row's own measured width, so the test holds at any window width.
 */
class StarRatingInputTest {

    @get:Rule
    val rule = createComposeRule()

    /** Shows the input at [rating]. Every change it asks for is recorded in [calls], and the rating does not move. */
    private fun show(
        rating: Float?,
        calls: MutableList<Float?> = mutableListOf(),
        enabled: Boolean = true,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) = rule.setContent {
        OmnibusTheme(darkTheme = true) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Box(Modifier.fillMaxWidth()) {
                    StarRatingInput(rating, { calls += it }, Color.Red, enabled = enabled)
                }
            }
        }
    }

    /** The row's semantics config, read from the merged tree. */
    private fun row() = rule.onNodeWithTag(StarRatingInputTestTags.ROW)

    /** A tap at [fraction] of the row's width, at its vertical centre. */
    private fun tapAt(fraction: Float) = row().performTouchInput {
        click(Offset(width * fraction, height / 2f))
    }

    /** The middle of half-star step [k] (1..10) as a fraction of the width. */
    private fun middleOf(k: Int): Float = (k - 0.5f) / 10f

    // TC-54 (AC1)
    @Test
    fun tc54_tappingEachHalfStarSetsItsValue() {
        val calls = mutableListOf<Float?>()
        show(rating = null, calls = calls)

        (1..10).forEach { k -> tapAt(middleOf(k)) }

        assertEquals((1..10).map { it / 2f }, calls)
    }

    // TC-55 (AC2)
    @Test
    fun tc55_tappingTheCurrentHalfStarClearsIt() {
        val calls = mutableListOf<Float?>()
        show(rating = 4f, calls = calls)

        tapAt(middleOf(8)) // the second half of the fourth star, the current rating
        tapAt(middleOf(7)) // the first half of the fourth star
        tapAt(middleOf(10)) // the fifth star

        assertEquals(listOf(null, 3.5f, 5f), calls)
    }

    // TC-56 (AC1, AC6)
    @Test
    fun tc56_aDragShowsThePreviewAndSavesOnRelease() {
        val calls = mutableListOf<Float?>()
        show(rating = 2f, calls = calls)

        row().performTouchInput {
            down(Offset(width * 0.05f, height / 2f))
            moveTo(Offset(width * 0.5f, height / 2f))
            moveTo(Offset(width * 0.75f, height / 2f))
        }
        rule.waitForIdle()
        row().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current shouldBeEqual 4f
        assertTrue(calls.isEmpty())
        row().assertStateDescriptionIs(detailText(R.string.book_detail_rate_stars_state, "4"))

        row().performTouchInput { up() }
        assertEquals(listOf(4f), calls)
    }

    // TC-57 (AC1)
    @Test
    fun tc57_dragsClampToTheEndsAndNeverClearOrRepeat() {
        val calls = mutableListOf<Float?>()
        show(rating = 4f, calls = calls)

        row().performTouchInput {
            down(Offset(width * 0.3f, height / 2f))
            moveTo(Offset(-width.toFloat(), height / 2f))
            up()
        }
        row().performTouchInput {
            down(Offset(width * 0.3f, height / 2f))
            moveTo(Offset(width * 2f, height / 2f))
            up()
        }
        // Released in the step that is already the rating: no call.
        row().performTouchInput {
            down(Offset(width * 0.72f, height / 2f))
            moveTo(Offset(width * 0.78f, height / 2f))
            up()
        }
        // Cancelled mid-drag: no call, and the shown value goes back to the rating.
        row().performTouchInput {
            down(Offset(width * 0.5f, height / 2f))
            moveTo(Offset(width * 0.75f, height / 2f))
            cancel()
        }
        rule.waitForIdle()

        assertEquals(listOf(0.5f, 5f), calls)
        row().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current shouldBeEqual 4f
    }

    // TC-58 (AC6)
    @Test
    fun tc58_aVerticalSwipeOnTheStarsScrollsThePage() {
        val calls = mutableListOf<Float?>()
        lateinit var scroll: ScrollState
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                scroll = rememberScrollState()
                val window = LocalWindowInfo.current.containerSize
                val spacer = with(LocalDensity.current) { (window.height * 2).toDp() }
                Column(Modifier.fillMaxWidth().verticalScroll(scroll)) {
                    StarRatingInput(rating = null, onRatingChange = { calls += it }, tint = Color.Red)
                    Spacer(Modifier.height(spacer))
                }
            }
        }

        row().performTouchInput { swipeUp() }
        rule.waitForIdle()
        assertTrue(scroll.value > 0)
        assertTrue(calls.isEmpty())

        val before = scroll.value
        row().performTouchInput {
            down(Offset(width * 0.5f, height / 2f))
            moveTo(Offset(width * 0.5f + 10f, height / 2f - 200f))
            moveTo(Offset(width * 0.5f + 10f, height / 2f - 400f))
            up()
        }
        rule.waitForIdle()
        assertTrue(scroll.value > before)
        assertTrue(calls.isEmpty())
    }

    // TC-59 (AC6)
    @Test
    fun tc59_rightToLeftCountsFromTheRightEdge() {
        val calls = mutableListOf<Float?>()
        show(rating = null, calls = calls, direction = LayoutDirection.Rtl)

        tapAt(0.95f) // 5% from the right edge
        tapAt(0.05f) // 5% from the left edge, which is the far end in RTL
        row().performTouchInput {
            down(Offset(width * 0.95f, height / 2f))
            moveTo(Offset(width * 0.45f, height / 2f))
            up()
        }

        assertEquals(listOf(0.5f, 5f, 3f), calls)
    }

    // TC-60 (AC6)
    @Test
    fun tc60_theRowIsOneAdjustableNodeWithItsState() {
        show(rating = 4.5f)

        row().assertContentDescriptionEquals(detailText(R.string.book_detail_rate_stars_description))
        row().assertStateDescriptionIs(detailText(R.string.book_detail_rate_stars_state, "4.5"))
        val range = row().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(4.5f, range.current, 0.001f)
        assertEquals(0f, range.range.start, 0.001f)
        assertEquals(5f, range.range.endInclusive, 0.001f)
        assertEquals(9, range.steps)
        row().onChildren().assertCountEquals(0)
    }

    // TC-60 (AC6)
    @Test
    fun tc60_anUnratedRowReadsNotRated() {
        show(rating = null)

        row().assertStateDescriptionIs(detailText(R.string.book_detail_rate_stars_unrated))
        assertEquals(0f, row().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0.001f)
    }

    // TC-61 (AC6)
    @Test
    fun tc61_setProgressSnapsToAHalfStarAndSavesOnlyAChange() {
        val calls = mutableListOf<Float?>()
        show(rating = 4f, calls = calls)

        val results = listOf(4f, 3.7f, 3.8f, 0f, -1f, 5.4f, 4.5f).map { setProgress(it) }

        assertEquals(listOf(3.5f, null, null, 5f, 4.5f), calls)
        assertTrue(results.all { it })
    }

    // TC-61 (AC6)
    @Test
    fun tc61_setProgressAtTheEnds() {
        val calls = mutableListOf<Float?>()
        show(rating = 5f, calls = calls)

        assertTrue(setProgress(5.5f))
        assertEquals(emptyList<Float?>(), calls)
    }

    // TC-62 (AC5, AC6)
    @Test
    fun tc62_aDisabledRowIgnoresEverything() {
        val calls = mutableListOf<Float?>()
        show(rating = 4f, calls = calls, enabled = false)

        row().assertIsNotEnabled()
        assertFalse(row().fetchSemanticsNode().config.contains(SemanticsActions.SetProgress))
        tapAt(0.5f)
        row().performTouchInput {
            down(Offset(width * 0.2f, height / 2f))
            moveTo(Offset(width * 0.9f, height / 2f))
            up()
        }
        row().performTouchInput { swipeUp() }

        assertTrue(calls.isEmpty())
        row().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current shouldBeEqual 4f
    }

    /** Runs the row's SetProgress action with [target] on the UI thread, and returns what it answered. */
    private fun setProgress(target: Float): Boolean {
        val action = row().fetchSemanticsNode().config[SemanticsActions.SetProgress].action!!
        var answer = false
        rule.runOnIdle { answer = action.invoke(target) }
        return answer
    }

    /** Asserts the row's state description, which Compose reads as a semantics property. */
    private fun SemanticsNodeInteraction.assertStateDescriptionIs(expected: String) =
        assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, expected))

    private infix fun Float.shouldBeEqual(expected: Float) = assertEquals(expected, this, 0.001f)
}
