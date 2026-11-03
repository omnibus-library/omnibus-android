package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class HighlightsSectionTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-52
    @Test
    fun tc52_aNullIdentifierRendersNothing() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                HighlightsSection.Content(
                    context = testSectionContext(book = testDetailBook(uniqueIdentifier = null)),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(HighlightsTestTags.SECTION).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.HEADING).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertDoesNotExist()
    }

    // TC-52
    @Test
    fun tc52_aBlankIdentifierRendersNothing() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                HighlightsSection.Content(
                    context = testSectionContext(book = testDetailBook(uniqueIdentifier = "  ")),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(HighlightsTestTags.SECTION).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.HEADING).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertDoesNotExist()
    }
}
