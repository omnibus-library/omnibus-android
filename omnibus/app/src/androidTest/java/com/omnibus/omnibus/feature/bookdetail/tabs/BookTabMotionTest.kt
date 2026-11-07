package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import org.junit.Rule
import org.junit.Test

/** The panel under rapid tab changes, with motion on. */
class BookTabMotionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun fourFakes() = BookDetailSlots(
        overviewSections = listOf(fakeSection("fake-overview")),
        reviewsSections = listOf(fakeSection("fake-reviews")),
        highlightsSections = listOf(fakeSection("fake-highlights")),
        filesSections = listOf(fakeSection("fake-files")),
    )

    // TC-54
    @Test
    fun issue94_tc54_rapidTabChangesLeaveOneSettledPanel() {
        rule.mainClock.autoAdvance = false
        rule.setTabHost(fourFakes(), reducedMotion = false)

        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.tabNode(BookDetailTab.Highlights).performClick()
        rule.mainClock.advanceTimeBy(2000)
        rule.waitForIdle()
        rule.onAllNodesWithTag("fake-reviews").assertCountEquals(0)
        rule.onAllNodesWithTag("fake-overview").assertCountEquals(0)
        rule.onAllNodesWithTag("fake-highlights").assertCountEquals(1)
    }
}
