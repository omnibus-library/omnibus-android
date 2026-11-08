package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val ROOT_TAG = "reader_context_stage_test_root"
private const val PROBE_TAG = "reader_context_stage_test_probe"
private const val TOP_LABEL_TAG = "reader_context_stage_test_top_label"
private const val BOTTOM_LABEL_TAG = "reader_context_stage_test_bottom_label"

/** Whole-pixel rounding of the card's offset and size can move an edge by a pixel on each side. */
private const val SLACK = 2f

/** What the stage is drawn from. Tests change it through a mutable state. */
private data class StageInputs(
    val contextMode: Boolean = true,
    val peeks: Set<PeekSide> = setOf(PeekSide.Previous, PeekSide.Next),
    val reduceMotion: Boolean = true,
    /** The share of the window the stage fills, so no test pins a size in dp. */
    val fraction: Float = 1f,
)

// TC-17 to TC-21. Bounds are relative to the root, which is a fraction of the window, so they hold on the 320dp CI AVD
// and on a phone-sized one.
/** Touch ownership, geometry, peeks and motion of [ReaderContextStage]. The screen's wiring is in ReaderScreenTest. */
class ReaderContextStageTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * Sets the stage once, with the page under it. A tap on the page appends "page" to [events], and [onLeave] appends
     * "leave". Each layout of the page appends its size to [sizes].
     */
    private fun setStage(inputs: MutableState<StageInputs>, events: MutableList<String>, sizes: MutableList<IntSize>) {
        rule.setContent {
            val i = inputs.value
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth(i.fraction).fillMaxHeight(i.fraction).testTag(ROOT_TAG)) {
                        ReaderContextStage(
                            contextMode = i.contextMode,
                            peeks = i.peeks,
                            colors = readerChromeColors(ReaderTheme.Light),
                            ground = Color(ReaderTheme.Light.groundArgb),
                            reduceMotion = i.reduceMotion,
                            insets = WindowInsets(0),
                            onLeave = { events += "leave" },
                        ) {
                            Box(Modifier.fillMaxSize()) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .testTag(PROBE_TAG)
                                        .onSizeChanged { sizes += it }
                                        .clickable { events += "page" },
                                )
                                Text("Nineteen Eighty-Four", Modifier.align(Alignment.TopCenter).testTag(TOP_LABEL_TAG))
                                Text("142", Modifier.align(Alignment.BottomCenter).testTag(BOTTOM_LABEL_TAG))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun rootBounds() = rule.onNodeWithTag(ROOT_TAG).fetchSemanticsNode().boundsInRoot

    private fun cardBounds() = rule.onNodeWithTag(ReaderContextStageTags.CARD).fetchSemanticsNode().boundsInRoot

    // TC-17
    @Test
    fun cs01_aSwipeOnTheZoomedCardDoesNothing() {
        val events = mutableListOf<String>()
        setStage(mutableStateOf(StageInputs(contextMode = true)), events, mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderContextStageTags.CARD).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(emptyList<String>(), events)
    }

    // TC-17
    @Test
    fun cs01_aLongPressOnTheZoomedCardDoesNothing() {
        val events = mutableListOf<String>()
        setStage(mutableStateOf(StageInputs(contextMode = true)), events, mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderContextStageTags.CARD).performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(emptyList<String>(), events)
    }

    // TC-17
    @Test
    fun cs01_aTapOnTheZoomedCardLeavesOnceAndNeverReachesThePage() {
        val events = mutableListOf<String>()
        setStage(mutableStateOf(StageInputs(contextMode = true)), events, mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderContextStageTags.CARD).performTouchInput { click() }
        rule.waitForIdle()

        assertEquals(listOf("leave"), events)
    }

    // TC-17
    @Test
    fun cs01_theScrimIsAReturnButtonLabelledForTheBook() {
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.reader_context_return)
        val events = mutableListOf<String>()
        setStage(mutableStateOf(StageInputs(contextMode = true)), events, mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithContentDescription(label)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performSemanticsAction(SemanticsActions.OnClick)
        rule.waitForIdle()

        assertEquals(listOf("leave"), events)
    }

    // TC-17
    @Test
    fun cs01_theFocusedPageReceivesTheTapOnceTheZoomIsOver() {
        val events = mutableListOf<String>()
        val inputs = mutableStateOf(StageInputs(contextMode = true, reduceMotion = false))
        setStage(inputs, events, mutableListOf())
        rule.waitForIdle()

        rule.runOnIdle { inputs.value = inputs.value.copy(contextMode = false) }
        // waitForIdle runs the zoom-out to its end, so the scrim is gone by the time the page is tapped.
        rule.waitForIdle()
        rule.onNodeWithTag(ReaderContextStageTags.SCRIM).assertDoesNotExist()
        rule.onNodeWithTag(PROBE_TAG).performClick()
        rule.waitForIdle()

        assertEquals(listOf("page"), events)
    }

    // TC-18
    @Test
    fun cs02_theCardIsNarrowerThanTheRootAndCentred() {
        setStage(mutableStateOf(StageInputs(contextMode = true)), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        val root = rootBounds()
        val card = cardBounds()
        assertTrue("the card is narrower than the root", card.width < root.width)
        assertTrue("the card is centred horizontally", abs(card.center.x - root.center.x) <= SLACK)
    }

    // TC-18
    @Test
    fun cs02_theCardKeepsTheAreaAspectRatio() {
        setStage(mutableStateOf(StageInputs(contextMode = true)), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        val root = rootBounds()
        val card = cardBounds()
        assertEquals(root.width / root.height, card.width / card.height, 0.02f)
    }

    // TC-18
    @Test
    fun cs02_theCardSitsBelowTheTopAndAboveTheBottomEdge() {
        setStage(mutableStateOf(StageInputs(contextMode = true)), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        val root = rootBounds()
        val card = cardBounds()
        assertTrue("the card starts below the top of the area", card.top > root.top)
        assertTrue("the card ends above the bottom of the area", card.bottom < root.bottom)
    }

    // TC-18
    @Test
    fun cs02_thePageIsNotRelaidOutByTheZoom() {
        val sizes = mutableListOf<IntSize>()
        val inputs = mutableStateOf(StageInputs(contextMode = false))
        setStage(inputs, mutableListOf(), sizes)
        rule.waitForIdle()

        rule.runOnIdle { inputs.value = inputs.value.copy(contextMode = true) }
        rule.waitForIdle()

        assertTrue("the page was laid out", sizes.isNotEmpty())
        assertEquals(1, sizes.distinct().size)
        assertTrue("the page is laid out at the area's size", abs(sizes.last().width - rootBounds().width) <= SLACK)
    }

    // TC-18, TC-17
    @Test
    fun cs02_theLabelsStayInsideTheCard() {
        setStage(mutableStateOf(StageInputs(contextMode = true)), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        val card = cardBounds()
        listOf(TOP_LABEL_TAG, BOTTOM_LABEL_TAG).forEach { tag ->
            val label = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            assertTrue("$tag is inside the card", label.left >= card.left && label.right <= card.right)
            assertTrue("$tag is inside the card", label.top >= card.top && label.bottom <= card.bottom)
        }
    }

    // TC-18
    @Test
    fun cs02_aResizeKeepsTheCardInsideTheRootWithoutReplayingTheZoom() {
        val inputs = mutableStateOf(StageInputs(contextMode = true, reduceMotion = false))
        setStage(inputs, mutableListOf(), mutableListOf())
        rule.waitForIdle()
        val widthBefore = rootBounds().width

        // With the clock stopped, the new size must show at once: a replayed zoom would still be running.
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { inputs.value = inputs.value.copy(fraction = 0.6f) }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        assertTrue("the root changed size", rootBounds().width < widthBefore)

        val root = rootBounds()
        val card = cardBounds()
        assertTrue("the card is narrower than the root", card.width < root.width)
        assertTrue("the card is centred horizontally", abs(card.center.x - root.center.x) <= SLACK)
        assertEquals(root.width / root.height, card.width / card.height, 0.02f)
    }

    // TC-19
    @Test
    fun cs03_eachPeekSitsBesideTheCardAtThePageGap() {
        setStage(mutableStateOf(StageInputs(contextMode = true)), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        val card = cardBounds()
        val previous = rule.onNodeWithTag(ReaderContextStageTags.PEEK_PREVIOUS).fetchSemanticsNode().boundsInRoot
        val next = rule.onNodeWithTag(ReaderContextStageTags.PEEK_NEXT).fetchSemanticsNode().boundsInRoot
        val gap = with(rule.density) { 12.dp.toPx() }
        assertTrue("the previous peek is left of the card", abs(previous.right + gap - card.left) <= SLACK)
        assertTrue("the next peek is right of the card", abs(next.left - gap - card.right) <= SLACK)
    }

    // TC-19
    @Test
    fun cs03_aPeekIsTheSizeOfTheCard() {
        setStage(mutableStateOf(StageInputs(contextMode = true)), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        val card = cardBounds()
        val peek = rule.onNodeWithTag(ReaderContextStageTags.PEEK_NEXT).fetchSemanticsNode().boundsInRoot
        assertTrue("the peek has the card's width", abs(peek.width - card.width) <= SLACK)
        assertTrue("the peek has the card's height", abs(peek.height - card.height) <= SLACK)
    }

    // TC-19
    @Test
    fun cs03_aMissingPreviousPeekIsNotDrawn() {
        setStage(mutableStateOf(StageInputs(contextMode = true, peeks = setOf(PeekSide.Next))), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderContextStageTags.PEEK_PREVIOUS).assertDoesNotExist()
        rule.onNodeWithTag(ReaderContextStageTags.PEEK_NEXT).assertExists()
    }

    // TC-19
    @Test
    fun cs03_noPeeksAreDrawnWhenNeitherNeighbourExists() {
        setStage(mutableStateOf(StageInputs(contextMode = true, peeks = emptySet())), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderContextStageTags.PEEK_PREVIOUS).assertDoesNotExist()
        rule.onNodeWithTag(ReaderContextStageTags.PEEK_NEXT).assertDoesNotExist()
    }

    // TC-19
    @Test
    fun cs03_aPeekCarriesNoText() {
        setStage(mutableStateOf(StageInputs(contextMode = true)), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderContextStageTags.PEEK_PREVIOUS).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
        rule.onNodeWithTag(ReaderContextStageTags.PEEK_NEXT).assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    // TC-19
    @Test
    fun cs03_noPeeksAreDrawnWhileFocused() {
        setStage(mutableStateOf(StageInputs(contextMode = false)), mutableListOf(), mutableListOf())
        rule.waitForIdle()

        rule.onNodeWithTag(ReaderContextStageTags.PEEK_PREVIOUS).assertDoesNotExist()
        rule.onNodeWithTag(ReaderContextStageTags.PEEK_NEXT).assertDoesNotExist()
    }

    // TC-21
    @Test
    fun cs04_reducedMotionLandsOnTheNextFrame() {
        val inputs = mutableStateOf(StageInputs(contextMode = false, reduceMotion = true))
        setStage(inputs, mutableListOf(), mutableListOf())
        rule.waitForIdle()

        rule.mainClock.autoAdvance = false
        rule.runOnIdle { inputs.value = inputs.value.copy(contextMode = true) }
        // Two frames: the first one starts the clock, and the snap then lands on the target.
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        val afterFrames = cardBounds().width

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val settled = cardBounds().width

        assertTrue("the card has zoomed out", settled < rootBounds().width - SLACK)
        assertEquals(settled, afterFrames, SLACK)
    }

    // TC-21
    @Test
    fun cs04_animatedZoomIsStillInFlightAfterAFewFrames() {
        val inputs = mutableStateOf(StageInputs(contextMode = false, reduceMotion = false))
        setStage(inputs, mutableListOf(), mutableListOf())
        rule.waitForIdle()
        val rootWidth = rootBounds().width

        rule.mainClock.autoAdvance = false
        rule.runOnIdle { inputs.value = inputs.value.copy(contextMode = true) }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(64)
        val midway = cardBounds().width

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        val settled = cardBounds().width

        assertTrue("the zoom has started", midway < rootWidth - SLACK)
        assertTrue("the zoom is not finished yet", midway > settled + SLACK)
    }
}
