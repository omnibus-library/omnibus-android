package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

/** The section's gate: a book with nothing to read renders nothing, before any ViewModel is created. */
class MyReviewSectionTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-77 (R9)
    @Test
    fun tc77_sectionRendersNothingForABookWithNothingToRead() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                MyReviewSection.Content(
                    context = testSectionContext(
                        book = testDetailBook(formats = emptyList(), creators = emptyList()),
                    ),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(MyReviewTestTags.CARD).assertDoesNotExist()
    }
}
