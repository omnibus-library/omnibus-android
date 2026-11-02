package com.omnibus.omnibus.feature.bookdetail.stub

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookDetailPlaceholderScreenTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-131
    @Test
    fun issue93_tc131_placeholderShowsTitleAndComingSoon() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookDetailPlaceholderScreen(title = "Frank Herbert")
            }
        }

        val title = rule.onNodeWithText("Frank Herbert")
        title.assertIsDisplayed()
        val message = rule.onNodeWithText(detailText(R.string.book_detail_placeholder_message))
        message.assertIsDisplayed()
        assertTrue(title.bounds().bottom <= message.bounds().top)
    }
}
