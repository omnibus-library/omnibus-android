package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.feature.bookdetail.BookDetailViewModel
import com.omnibus.omnibus.feature.bookdetail.FakeBookDetailRepository
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookDetailReloadOnReturnTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-109
    @Test
    fun issue93_tc109_returningReloadsSilently() {
        // Nothing to track and no linked author: the Hilt-only read-status and more-by-author sections stay out.
        val book = testDetailBook(formats = emptyList(), creators = emptyList())
        val repository = FakeBookDetailRepository(bookResult = BookResult.Success(book))
        val viewModel = BookDetailViewModel("uuid-1", repository)
        val shown = mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                if (shown.value) {
                    BookDetailScreen(
                        uuid = "uuid-1",
                        onNavigate = {},
                        onClose = {},
                        modifier = Modifier.fillMaxSize(),
                        viewModel = viewModel,
                    )
                }
            }
        }
        rule.waitForIdle()
        assertEquals(1, repository.bookRequests.size)

        // Leave and return: each return reloads silently, with the content kept.
        rule.runOnIdle { shown.value = false }
        rule.waitForIdle()
        rule.runOnIdle { shown.value = true }
        rule.waitForIdle()
        assertEquals(2, repository.bookRequests.size)
        rule.onNodeWithText(book.displayTitle).assertIsDisplayed()
        rule.onAllNodesWithText(detailText(R.string.book_detail_loading)).assertCountEquals(0)

        rule.runOnIdle { shown.value = false }
        rule.waitForIdle()
        rule.runOnIdle { shown.value = true }
        rule.waitForIdle()
        assertEquals(3, repository.bookRequests.size)
    }
}
