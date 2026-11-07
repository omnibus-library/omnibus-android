package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BookDetailActionAreaTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-110
    @Test
    fun issue93_tc110_readPillOpensTheReader() {
        val book = testDetailBook(formats = listOf("epub"))
        val navigations = mutableListOf<BookDetailDestination>()
        rule.setDetailScreen(
            BookDetailUIState(book = book, isLoading = false),
            onNavigate = { navigations += it },
        )

        rule.onNodeWithText(detailText(R.string.book_detail_read)).performClick()
        rule.waitForIdle()

        assertEquals(listOf(BookDetailDestination.Reader(book.uuid)), navigations)
    }
}
