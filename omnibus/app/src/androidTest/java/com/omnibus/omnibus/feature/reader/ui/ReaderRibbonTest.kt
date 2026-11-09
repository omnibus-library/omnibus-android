package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val CONTAINER = "reader_ribbon_test_container"
private const val UNDERLAY = "reader_ribbon_test_underlay"
private const val PAGE = "epubcfi(/6/8!/4/2/1:0)"
private val RIBBON_COLOR = Color(0xFF2A8C8C)
private val HIDDEN = RibbonFrame(PAGE, shown = false, tapped = false)
private val SHOWN = RibbonFrame(PAGE, shown = true, tapped = false)
private val SINGLE_PLACEMENT = RibbonPlacement(ReaderRibbonMetrics.EDGE, translucent = false, twoPage = false)

// TC-9 to TC-14. The ribbon is driven directly, with its frame and reduced motion as parameters. Tests compare bounds
// relative to the container and to each other, never a fixed dp or screen size, so they hold on any phone-sized AVD.
/** The ribbon's motion, touch pass-through and placement. ReaderScreenTest covers when it shows on the page. */
class ReaderRibbonTest {

    @get:Rule
    val rule = createComposeRule()

    /** One reading of the ribbon: its top relative to the container, in dp, and its motion alpha. */
    private data class Sample(val top: Float, val alpha: Float)

    /** Sets the ribbon over a page that reports taps to [events], with [frame] read on every composition. */
    private fun setRibbon(
        frame: MutableState<RibbonFrame>,
        events: MutableList<String>,
        reduceMotion: Boolean = false,
    ) {
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxSize().testTag(CONTAINER)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .testTag(UNDERLAY)
                            .pointerInput(Unit) { detectTapGestures { events += "page" } },
                    )
                    ReaderRibbon(frame.value, RIBBON_COLOR, SINGLE_PLACEMENT, reduceMotion)
                }
            }
        }
    }

    private fun ribbon(): SemanticsNodeInteraction = rule.onNodeWithTag(ReaderRibbonTags.RIBBON, useUnmergedTree = true)

    /** The ribbon's top edge relative to the container's top edge, in dp. Unclipped, so a slide above the top shows. */
    private fun top(): Float =
        ribbon().getUnclippedBoundsInRoot().top.value - rule.onNodeWithTag(CONTAINER).getUnclippedBoundsInRoot().top.value

    private fun alpha(): Float = ribbon().fetchSemanticsNode().config[ReaderRibbonAlphaKey]

    private fun sample(): Sample {
        rule.waitForIdle()
        return Sample(top(), alpha())
    }

    /** Lets an entrance that is already running finish, with the clock driven by the test. */
    private fun settle() {
        rule.mainClock.advanceTimeBy(2_500)
        rule.waitForIdle()
    }

    /** Samples one frame, then 40, 80 and 400 ms after the change that was just made, as the frames fall at 16 ms. */
    private fun sampleAfterChange(): List<Sample> {
        val samples = mutableListOf<Sample>()
        rule.mainClock.advanceTimeByFrame()
        samples += sample()
        rule.mainClock.advanceTimeBy(24)
        samples += sample()
        rule.mainClock.advanceTimeBy(40)
        samples += sample()
        rule.mainClock.advanceTimeBy(320)
        samples += sample()
        return samples
    }

    // TC-9
    @Test
    fun issue224_tc9_tappingToAddUnfurlsFromAboveTheTop() {
        val frame = mutableStateOf(HIDDEN)
        setRibbon(frame, events = mutableListOf())
        settle()
        rule.mainClock.autoAdvance = false
        assertEquals(0f, alpha(), 0f)

        rule.runOnIdle { frame.value = RibbonFrame(PAGE, shown = true, tapped = true) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(80)
        rule.waitForIdle()
        assertTrue(top() < 0f)

        rule.mainClock.advanceTimeBy(2_500)
        rule.waitForIdle()
        assertEquals(0f, top(), 0.5f)
        assertEquals(1f, alpha(), 0.001f)
    }

    // TC-10
    @Test
    fun issue224_tc10_tappingToRemoveFurlsUp() {
        val frame = mutableStateOf(SHOWN)
        setRibbon(frame, events = mutableListOf())
        settle()
        rule.mainClock.autoAdvance = false

        rule.runOnIdle { frame.value = RibbonFrame(PAGE, shown = false, tapped = true) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(200)
        val early = sample()
        rule.mainClock.advanceTimeBy(80)
        val later = sample()
        assertTrue(early.top < 0f)
        assertTrue(later.top < early.top)

        rule.mainClock.advanceTimeBy(1_000)
        val rest = sample()
        assertTrue(rest.top <= 0f)
        assertEquals(0f, rest.alpha, 0.001f)
    }

    // TC-11
    @Test
    fun issue224_tc11_pageTurnDropsTheOldRibbonAndFadesTheNewOneIn() {
        val frame = mutableStateOf(SHOWN)
        setRibbon(frame, events = mutableListOf())
        settle()
        rule.mainClock.autoAdvance = false

        // From rest on page A to a bookmarked page B: the new ribbon fades in at rest, with no unfurl.
        rule.runOnIdle { frame.value = RibbonFrame("page-b", shown = true, tapped = false) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        val oneFrame = sample()
        assertTrue(oneFrame.alpha < 0.5f)
        assertEquals(0f, oneFrame.top, 0.5f)

        rule.mainClock.advanceTimeBy(70)
        val midway = sample()
        assertTrue(midway.alpha > 0f && midway.alpha < 1f)
        assertEquals(0f, midway.top, 0.5f)

        rule.mainClock.advanceTimeBy(430)
        assertEquals(1f, sample().alpha, 0.001f)

        // Back on page A at rest, then to a hidden page C: the old ribbon goes at once.
        rule.runOnIdle { frame.value = SHOWN }
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(500)
        assertEquals(1f, sample().alpha, 0.001f)

        rule.runOnIdle { frame.value = RibbonFrame("page-c", shown = false, tapped = false) }
        rule.waitForIdle()
        rule.mainClock.advanceTimeByFrame()
        val dropped = sample()
        assertEquals(0f, dropped.alpha, 0.001f)
        assertEquals(0f, dropped.top, 0.5f)
    }

    // TC-12
    @Test
    fun issue224_tc12_reducedMotionCrossfadesInPlace() {
        val frame = mutableStateOf(HIDDEN)
        setRibbon(frame, events = mutableListOf(), reduceMotion = true)
        settle()
        rule.mainClock.autoAdvance = false

        rule.runOnIdle { frame.value = RibbonFrame(PAGE, shown = true, tapped = true) }
        rule.waitForIdle()
        val added = sampleAfterChange()
        assertTrue(added.all { abs(it.top) <= 0.5f })
        assertTrue(added.zipWithNext().all { (a, b) -> b.alpha >= a.alpha })
        assertEquals(1f, added.last().alpha, 0.001f)

        rule.runOnIdle { frame.value = RibbonFrame(PAGE, shown = false, tapped = true) }
        rule.waitForIdle()
        val removed = sampleAfterChange()
        assertTrue(removed.all { abs(it.top) <= 0.5f })
        assertTrue(removed.zipWithNext().all { (a, b) -> b.alpha <= a.alpha })
        assertEquals(0f, removed.last().alpha, 0.001f)
    }

    // TC-13
    @Test
    fun issue224_tc13_touchesPassThrough() {
        val events = mutableListOf<String>()
        setRibbon(mutableStateOf(SHOWN), events = events)
        settle()

        ribbon().performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(listOf("page"), events)
    }

    /**
     * Sets the ribbon in a full-size container in [direction], as a single page or a spread, and returns the ribbon's
     * centre minus the container's centre, in dp.
     */
    private fun centreOffset(twoPage: Boolean, direction: LayoutDirection): Float {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                OmnibusTheme {
                    BoxWithConstraints(Modifier.fillMaxSize().testTag(CONTAINER)) {
                        val placement = if (twoPage) {
                            RibbonPlacement(maxWidth.value / 2f - ReaderRibbonMetrics.WIDTH / 2f, translucent = false, twoPage = true)
                        } else {
                            ribbonPlacement(maxWidth.value, 0f, 0f, ReaderMargins.Normal, "none")
                        }
                        ReaderRibbon(SHOWN, RIBBON_COLOR, placement, reduceMotion = true)
                    }
                }
            }
        }
        rule.waitForIdle()
        val container = rule.onNodeWithTag(CONTAINER).getUnclippedBoundsInRoot()
        val ribbon = ribbon().getUnclippedBoundsInRoot()
        return (ribbon.left.value + ribbon.right.value) / 2f - (container.left.value + container.right.value) / 2f
    }

    // TC-14
    @Test
    fun issue224_tc14_singleSitsOnTheLeftLtr() {
        assertTrue(centreOffset(twoPage = false, direction = LayoutDirection.Ltr) < 0f)
    }

    // TC-14
    @Test
    fun issue224_tc14_singleSitsOnTheLeftRtl() {
        assertTrue(centreOffset(twoPage = false, direction = LayoutDirection.Rtl) < 0f)
    }

    // TC-14
    @Test
    fun issue224_tc14_spreadIsCentredLtr() {
        assertTrue(abs(centreOffset(twoPage = true, direction = LayoutDirection.Ltr)) <= 1f)
    }

    // TC-14
    @Test
    fun issue224_tc14_spreadIsCentredRtl() {
        assertTrue(abs(centreOffset(twoPage = true, direction = LayoutDirection.Rtl)) <= 1f)
    }
}
