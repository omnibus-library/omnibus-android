package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The page's cover arrives after the page is already open: the book loads over the network. */
class CoverTransitionLateCoverTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val book = testDetailBook(id = 1)
    private val gridCover = { rule.onNodeWithTag(gridSourceTag("uuid-1"), useUnmergedTree = true) }

    // TC-22
    @Test
    fun issue122_tc22_coverArrivingAfterSettleDoesNotAnimateOrJump() {
        val h = CoverTransitionHarness(listOf(book))
        h.detailOverride = BookDetailUIState()
        rule.setContent { h.Content() }
        rule.waitForIdle()

        rule.tapAndSettle(gridCover())
        assertFalse("loading page shows no move", h.sharedScope.isTransitionActive)

        rule.runOnIdle { h.detailOverride = null }
        rule.waitForIdle()

        assertFalse("a late cover after the settle does not animate", h.sharedScope.isTransitionActive)
        assertSameRect(
            rule.stageCover().rootRect(),
            rule.movingStage(book.uuid).rootRect(),
            "stage cover after a late arrival",
        )
    }

    // TC-23
    @Test
    fun issue122_tc23_coverArrivingAfterTheJoinWindowDoesNotJoin() {
        val h = CoverTransitionHarness(listOf(book))
        h.detailOverride = BookDetailUIState()
        rule.setContent { h.Content() }
        rule.waitForIdle()
        val source = gridCover().rootRect()

        rule.mainClock.autoAdvance = false
        gridCover().performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(150)

        // Beyond the 100 ms join window, the cover arrives.
        rule.runOnIdle { h.detailOverride = null }
        repeat(60) {
            rule.mainClock.advanceTimeByFrame()
            val moving = rule.onAllNodesWithTag(BookCoverTransitionTestTags.stage(book.uuid), useUnmergedTree = true)
                .fetchSemanticsNodes()
            if (moving.isNotEmpty()) {
                assertSameRect(rule.stageCover().rootRect(), moving.single().boundsInRoot, "stage cover, frame $it")
            }
            val grid = rule.onAllNodesWithTag(gridSourceTag("uuid-1"), useUnmergedTree = true).fetchSemanticsNodes()
            if (grid.isNotEmpty()) assertSameRect(source, grid.single().boundsInRoot, "grid source, frame $it")
        }
    }

    // TC-27
    @Test
    fun issue122_tc27_coverArrivingInsideTheJoinWindowJoins() {
        val h = CoverTransitionHarness(listOf(book))
        rule.setContent { h.Content() }
        rule.waitForIdle()

        // Reference: the cover is there from the start, so the settled bounds are the stage's.
        val settled = rule.settledStageRect(h, gridCover())
        val source = gridCover().rootRect()

        rule.runOnIdle { h.detailOverride = BookDetailUIState() }
        rule.waitForIdle()

        rule.mainClock.autoAdvance = false
        gridCover().performClick()
        rule.mainClock.advanceTimeByFrame()
        // Flip to the loaded page after one frame, inside the join window.
        rule.runOnIdle { h.detailOverride = null }
        rule.mainClock.advanceTimeBy(150)

        assertTrue("a cover inside the window joins the open", h.sharedScope.isTransitionActive)
        assertInTransit(rule.movingStage(book.uuid).rootRect(), source, settled, "joined cover at 150 ms")

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertSameRect(rule.stageCover().rootRect(), rule.movingStage(book.uuid).rootRect(), "joined cover settles")
        assertEquals(2, h.stack.size)
    }
}
