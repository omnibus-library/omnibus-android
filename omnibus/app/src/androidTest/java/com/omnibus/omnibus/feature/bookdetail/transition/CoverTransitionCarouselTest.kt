package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.library.ui.ContinueTestTags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CoverTransitionCarouselTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // Title is short so it does not reach the resume area's tap point.
    private val book = testDetailBook(id = 1, title = "Book One")
    private val card = harnessCard(book)

    // TC-11
    @Test
    fun issue122_tc11_carouselCoverAndTitleOpenFromTheCardCover() {
        val h = CoverTransitionHarness(listOf(book), cards = listOf(card))
        rule.setContent { h.Content() }
        rule.waitForIdle()

        val cover = { rule.onNodeWithTag(continueSourceTag(book.uuid), useUnmergedTree = true) }
        cover().assertIsDisplayed()
        val settled = rule.settledStageRect(h, cover())
        val source = cover().rootRect()

        // The cover opens the page from the card's cover.
        val tapsBefore = h.opened.size
        rule.mainClock.autoAdvance = false
        cover().performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(150)
        assertTrue("cover tap should start the move", h.sharedScope.isTransitionActive)
        assertInTransit(rule.movingStage(book.uuid).rootRect(), source, settled, "carousel cover at 150 ms")

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(tapsBefore + 1, h.opened.size)
        assertEquals(book.uuid, h.opened.last())

        // Back, then the title opens the page from the same card cover.
        rule.runOnIdle { h.stack.removeLastOrNull() }
        rule.waitForIdle()
        assertSameRect(source, cover().rootRect(), "card cover after back")

        val tapsBeforeTitle = h.opened.size
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(ContinueTestTags.title(card.id), useUnmergedTree = true).performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(150)
        assertTrue("title tap should start the move", h.sharedScope.isTransitionActive)
        assertInTransit(rule.movingStage(book.uuid).rootRect(), source, settled, "carousel title at 150 ms")

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertEquals(tapsBeforeTitle + 1, h.opened.size)
        assertEquals(book.uuid, h.opened.last())
    }

    // TC-12
    @Test
    fun issue122_tc12_tappingTheRestOfTheCardDoesNotOpenThePage() {
        val h = CoverTransitionHarness(listOf(book), cards = listOf(card))
        rule.setContent { h.Content() }
        rule.waitForIdle()

        rule.onNodeWithTag(ContinueTestTags.card(card.id)).performTouchInput {
            click(Offset(width * 0.85f, height * 0.5f))
        }
        rule.waitForIdle()

        assertTrue("the opener must not be called", h.opened.isEmpty())
        assertEquals(listOf(card.id), h.resumed)
        assertFalse("no transition", h.sharedScope.isTransitionActive)
    }
}
