package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The strip pinned under the top bar on the full page, with a real scroll. */
class BookTabPinningTest {

    @get:Rule
    val rule = createComposeRule()

    private val linkedBook: Book = testDetailBook(creators = listOf(Creator("Ann Lee", "author", null, null)))

    private fun ComposeContentTestRule.stripTop(): Float = onNodeWithTag(BookDetailTabsTestTags.STRIP).bounds().top

    private fun ComposeContentTestRule.screen(scroll: ScrollState, slots: BookDetailSlots, book: Book = linkedBook) {
        setDetailScreen(BookDetailUIState(book = book, isLoading = false), slots = slots, scrollState = scroll)
        waitForIdle()
    }

    private fun allFakes(): BookDetailSlots = BookDetailSlots(
        overviewSections = listOf(tallFakeSection("fake-overview")),
        reviewsSections = listOf(fakeSection("fake-reviews")),
        highlightsSections = listOf(fakeSection("fake-highlights")),
        filesSections = listOf(tallFakeSection("fake-files")),
    )

    // TC-43
    @Test
    fun issue94_tc43_pinnedStripIsStillTappable() {
        val scroll = ScrollState(0)
        rule.screen(scroll, allFakes())
        rule.scrollTo(scroll)
        val pinnedTop = rule.stripTop()

        rule.tabNode(BookDetailTab.Reviews).performClick()
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Reviews).assertIsSelected()
        rule.onNodeWithTag("fake-reviews").assertExists()
        assertEquals(pinnedTop, rule.stripTop(), 1f)

        rule.tabNode(BookDetailTab.Files).performClick()
        rule.waitForIdle()
        rule.tabNode(BookDetailTab.Files).assertIsSelected()
        rule.onNodeWithTag("fake-files").assertExists()
        assertEquals(pinnedTop, rule.stripTop(), 1f)
    }
}
