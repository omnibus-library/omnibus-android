package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The same book is in the Continue carousel and the All books grid. Only the cover that was tapped may move.
 *
 * The grid and carousel covers are read through their existing cover tags, because those tags sit on the same node as
 * the source tag and replace it.
 */
class CoverTransitionOriginTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val book = testDetailBook(id = 1, title = "Book One")
    private val other = testDetailBook(id = 2, title = "Book Two")
    private val card = harnessCard(book)

    private val gridCover = { rule.onNodeWithTag(gridSourceTag(book.uuid), useUnmergedTree = true) }
    private val carouselCover = { rule.onNodeWithTag(continueSourceTag(book.uuid), useUnmergedTree = true) }

    // TC-13 (grid tap)
    @Test
    fun issue122_tc13_onlyTheTappedDuplicateCoverAnimates() {
        val h = CoverTransitionHarness(listOf(book, other), cards = listOf(card))
        rule.setContent { h.Content() }
        rule.waitForIdle()
        gridCover().assertIsDisplayed()
        carouselCover().assertIsDisplayed()

        val settled = rule.settledStageRect(h, gridCover())
        val gridSource = gridCover().rootRect()
        val carouselSource = carouselCover().rootRect()

        rule.mainClock.autoAdvance = false
        gridCover().performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(150)

        assertEquals(BookCoverOrigin.Grid, h.state.keyFor(book.uuid).origin)
        assertInTransit(rule.movingStage(book.uuid).rootRect(), gridSource, settled, "grid tap at 150 ms")
        assertSameRect(carouselSource, carouselCover().rootRect(), "untapped carousel cover, at 150 ms")

        repeat(20) {
            rule.mainClock.advanceTimeByFrame()
            val carousel = rule.onAllNodesWithTag(continueSourceTag(book.uuid), useUnmergedTree = true).fetchSemanticsNodes()
            if (carousel.isNotEmpty()) {
                assertSameRect(carouselSource, carousel.single().boundsInRoot, "untapped carousel cover, sampled frame $it")
            }
        }
    }

    // TC-13 (carousel tap, the separate run)
    @Test
    fun issue122_tc13_onlyTheTappedDuplicateCoverAnimatesFromTheCarousel() {
        val h = CoverTransitionHarness(listOf(book, other), cards = listOf(card))
        rule.setContent { h.Content() }
        rule.waitForIdle()

        val settled = rule.settledStageRect(h, carouselCover())
        val carouselSource = carouselCover().rootRect()
        val gridSource = gridCover().rootRect()

        rule.mainClock.autoAdvance = false
        carouselCover().performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(150)

        assertEquals(BookCoverOrigin.Continue, h.state.keyFor(book.uuid).origin)
        assertInTransit(rule.movingStage(book.uuid).rootRect(), carouselSource, settled, "carousel tap at 150 ms")
        assertSameRect(gridSource, gridCover().rootRect(), "untapped grid cover, at 150 ms")
    }

    // TC-14
    @Test
    fun issue122_tc14_accessibilityClickRecordsTheOrigin() {
        val h = CoverTransitionHarness(listOf(book, other), cards = listOf(card))
        rule.setContent { h.Content() }
        rule.waitForIdle()

        val settled = rule.settledStageRect(h, carouselCover())
        val carouselSource = carouselCover().rootRect()

        rule.mainClock.autoAdvance = false
        carouselCover().performSemanticsAction(SemanticsActions.OnClick)
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(150)

        assertEquals(BookCoverOrigin.Continue, h.state.keyFor(book.uuid).origin)
        assertInTransit(rule.movingStage(book.uuid).rootRect(), carouselSource, settled, "accessibility click at 150 ms")
    }

    // TC-15
    @Test
    fun issue122_tc15_originSurvivesRotationWhileThePageIsOpen() {
        val h = CoverTransitionHarness(listOf(book, other), cards = listOf(card), restorable = true)
        val restoration = StateRestorationTester(rule)
        restoration.setContent { h.Content() }
        rule.waitForIdle()

        val settled = rule.settledStageRect(h, carouselCover())
        val carouselSource = carouselCover().rootRect()
        val gridSource = gridCover().rootRect()

        // Open from the carousel, let it settle, then rotate.
        rule.onNodeWithTag(continueSourceTag(book.uuid), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals(BookCoverOrigin.Continue, h.state.keyFor(book.uuid).origin)

        // Going back after rotation still starts from the carousel cover, not the grid cell.
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { h.stack.removeLastOrNull() }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(150)

        assertTrue("back should move the cover", h.sharedScope.isTransitionActive)
        assertInTransit(rule.movingStage(book.uuid).rootRect(), settled, carouselSource, "back after rotation at 150 ms")
        assertNotEquals("grid cell is not the origin", gridSource, rule.movingStage(book.uuid).rootRect())
    }
}
