package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookDetailScrollTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-102
    @Test
    fun issue93_tc102_stageScrollsFullyUnderTheTopBar() {
        val scroll = ScrollState(0)
        rule.setDetailScreen(BookDetailUIState(book = testDetailBook(), isLoading = false), scrollState = scroll)
        rule.waitForIdle()
        assertTrue("the page must scroll with no tab content", scroll.maxValue > 0)

        rule.scrollTo(scroll)
        val stage = rule.onNodeWithTag(BookDetailTestTags.STAGE).bounds()
        val bar = rule.onNodeWithTag(BookDetailTestTags.TOP_BAR).bounds()
        val tabArea = rule.onNodeWithTag(BookDetailTestTags.TAB_AREA).bounds()
        val actionArea = rule.onNodeWithTag(BookDetailTestTags.ACTION_AREA).bounds()
        assertTrue("stage bottom ${stage.bottom} above bar bottom ${bar.bottom}", stage.bottom <= bar.bottom + 1f)
        assertTrue(tabArea.bottom <= actionArea.top + 1f)
    }
}
