package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test

class CoverTransitionPlateTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // testDetailBook has no cover URL, so the cover is the generated plate.
    private val book = testDetailBook(id = 1)

    // TC-18
    @Test
    fun issue122_tc18_generatedPlateScalesWithoutReflow() {
        val h = CoverTransitionHarness(listOf(book))
        rule.setContent { h.Content() }
        rule.waitForIdle()

        val gridCover = { rule.onNodeWithTag(gridSourceTag("uuid-1"), useUnmergedTree = true) }
        // The settled layout size, read while the page is open.
        rule.tapAndSettle(gridCover())
        val settledSize = rule.movingStage(book.uuid).fetchSemanticsNode().size
        rule.backAndSettle(h)

        rule.mainClock.autoAdvance = false
        gridCover().performClick()
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        val samples = mutableListOf<Rect>()
        repeat(3) {
            rule.mainClock.advanceTimeBy(60)
            val node = rule.movingStage(book.uuid).fetchSemanticsNode()
            assertEquals("layout size at sample $it", settledSize, node.size)
            samples += node.boundsInRoot
        }
        assertNotEquals("the cover moves between samples", samples[0], samples[1])
        assertNotEquals("the cover moves between samples", samples[1], samples[2])
    }
}
