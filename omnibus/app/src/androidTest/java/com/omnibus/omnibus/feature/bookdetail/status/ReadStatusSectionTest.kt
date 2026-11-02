package com.omnibus.omnibus.feature.bookdetail.status

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class ReadStatusSectionTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-101
    @Test
    fun tc101_sectionRendersNothingWithoutATrackableBook() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReadStatusSection.Content(
                    context = testSectionContext(book = testDetailBook(formats = emptyList())),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(ReadStatusTestTags.PILL).assertDoesNotExist()
    }
}
