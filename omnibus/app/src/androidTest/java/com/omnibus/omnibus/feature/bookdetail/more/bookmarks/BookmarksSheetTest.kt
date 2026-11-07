package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Heading and close semantics, and retry. BookmarksSheetScreenshotTest covers how each state looks. */
class BookmarksSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val title get() = detailText(R.string.book_detail_bookmarks_title)
    private val closeLabel get() = detailText(R.string.book_detail_bookmarks_close)
    private val retryLabel get() = detailText(R.string.book_detail_bookmarks_retry)

    // TC-9
    @Test
    fun issue117_tc9_titleIsAHeadingAndCloseIsLabelled() {
        rule.setSheetContent(BookmarksContent.Loaded(emptyList()))

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(title).assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        rule.onNodeWithContentDescription(closeLabel).assertIsDisplayed().assertHasClickAction()
    }

    // TC-40
    @Test
    fun issue117_tc40_retryInTheFailedStateRetriesOnce() {
        var retries = 0
        rule.setSheetContent(BookmarksContent.Failed, onRetry = { retries++ })

        rule.onNodeWithText(retryLabel).performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }
}
