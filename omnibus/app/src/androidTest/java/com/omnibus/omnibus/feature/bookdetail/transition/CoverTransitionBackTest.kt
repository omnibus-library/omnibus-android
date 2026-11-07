package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Rule
import org.junit.Test

class CoverTransitionBackTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val books: List<Book> = listOf(testDetailBook(id = 1), testDetailBook(id = 2, title = "Book Two"))

    /** Opens book 1 from the grid, lets the open settle, and returns the settled stage and the grid cell's cover. */
    private fun openFromGrid(h: CoverTransitionHarness): Pair<Rect, Rect> {
        rule.setContent { h.Content() }
        rule.waitForIdle()
        val source = rule.onNodeWithTag(gridSourceTag("uuid-1"), useUnmergedTree = true).rootRect()
        rule.onNodeWithTag(gridSourceTag("uuid-1"), useUnmergedTree = true).performClick()
        rule.waitForIdle()
        return rule.stageCover().rootRect() to source
    }

    // TC-8
    @Test
    fun issue122_tc8_backReturnsCoverToTheSameGridCell() {
        val h = CoverTransitionHarness(books)
        val (settled, source) = openFromGrid(h)

        rule.mainClock.autoAdvance = false
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeBy(150)

        assertInTransit(rule.movingStage(books[0].uuid).rootRect(), settled, source, "back at 150 ms")

        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        assertSameRect(source, rule.onNodeWithTag(gridSourceTag("uuid-1"), useUnmergedTree = true).rootRect(), "grid cell after back")
        rule.onAllNodesWithTag(BookCoverTransitionTestTags.stage(books[0].uuid), useUnmergedTree = true)
            .assertCountEquals(0)
    }
}
