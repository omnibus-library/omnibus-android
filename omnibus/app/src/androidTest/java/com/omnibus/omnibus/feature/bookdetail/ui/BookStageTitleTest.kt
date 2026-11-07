package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Rule
import org.junit.Test

class BookStageTitleTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-81
    @Test
    fun issue93_tc81_titleIsAHeading() {
        rule.setDetailScreen(BookDetailUIState(book = testDetailBook(title = "The Left Hand of Darkness"), isLoading = false))

        rule.onNodeWithTag(BookDetailTestTags.TITLE, useUnmergedTree = true).assert(isHeading())
    }
}
