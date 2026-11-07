package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.feature.bookdetail.BookDetailError
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookDetailRefreshTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-105
    @Test
    fun issue93_tc105_pullingDownRefreshes() {
        val book = testDetailBook(title = "Refreshable")
        var refreshes = 0
        rule.setDetailScreen(
            BookDetailUIState(book = book, isLoading = false),
            onRefresh = { refreshes++ },
        )

        rule.onNodeWithTag(BookDetailTestTags.SCROLL).performTouchInput { swipeDown() }
        rule.waitForIdle()
        assertEquals(1, refreshes)
    }

    // TC-108
    @Test
    fun issue93_tc108_failedRefreshSnackbarOverContent() {
        val book = testDetailBook(title = "Kept On Screen")
        val unreachable = detailText(R.string.library_error_unreachable)
        var transientShown = 0
        rule.setDetailScreen(
            BookDetailUIState(
                book = book,
                isLoading = false,
                transientError = BookDetailError.Failed(LibraryError.Unreachable),
            ),
            onTransientErrorShown = { transientShown++ },
        )
        rule.waitForIdle()

        rule.onNodeWithText(unreachable).assertIsDisplayed()
        rule.onNodeWithText(book.displayTitle).assertIsDisplayed()
        assertEquals(1, transientShown)
    }
}
