package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailError
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookDetailScreenStatesTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-74
    @Test
    fun issue93_tc74_retryInTheErrorStateRetries() {
        var retries = 0
        rule.setDetailScreen(
            BookDetailUIState(error = BookDetailError.NotFound, isLoading = false),
            onRetry = { retries++ },
        )

        rule.onNodeWithText(detailText(R.string.book_detail_retry)).performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }

    // TC-76
    @Test
    fun issue93_tc76_backDiscCloses() {
        var closes = 0
        rule.setDetailScreen(BookDetailUIState(book = testDetailBook(), isLoading = false), onClose = { closes++ })

        rule.onNodeWithContentDescription(detailText(R.string.navigation_back)).performClick()
        rule.waitForIdle()

        assertEquals(1, closes)
    }
}
