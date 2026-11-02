package com.omnibus.omnibus.feature.bookdetail.progress

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class ReadingProgressSectionTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-92
    @Test
    fun tc92_aBookWithNoFileShowsNothing() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadingProgressSection.Content(
                    context = testSectionContext(book = testDetailBook(formats = emptyList())),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(ReadingProgressTestTags.BAR).assertDoesNotExist()
        rule.onNodeWithTag(ReadingProgressTestTags.LINE).assertDoesNotExist()
        rule.onNodeWithTag(ReadingProgressTestTags.IN_PROGRESS).assertDoesNotExist()
        rule.onNodeWithTag(ReadingProgressTestTags.OFFLINE_NOTE).assertDoesNotExist()
    }
}
