package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

/**
 * The overlay's gate: a book with nothing to read renders no sheet and no picker, and creates no ViewModel, so the
 * plain test host needs no Hilt.
 */
class ReviewSheetSectionTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `TC-103 a book with nothing to read renders no sheet and no picker`() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ReviewSheetSection.Content(
                    context = testSectionContext(
                        book = testDetailBook(formats = emptyList(), creators = emptyList()),
                    ),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(ReviewSheetTestTags.SHEET).assertDoesNotExist()
        rule.onNodeWithTag(ReviewSheetTestTags.PICKER).assertDoesNotExist()
    }
}
