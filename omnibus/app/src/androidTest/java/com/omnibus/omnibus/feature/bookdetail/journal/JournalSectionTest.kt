package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class JournalSectionTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-62
    @Test
    fun tc62_aNullIdentifierRendersNothing() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                JournalSection.Content(
                    context = testSectionContext(book = testDetailBook(uniqueIdentifier = null)),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(JournalTestTags.SECTION).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.HEADING).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.RETRY).assertDoesNotExist()
    }

    // TC-63
    @Test
    fun tc63_anEmptyIdentifierRendersNothing() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                JournalSection.Content(
                    context = testSectionContext(book = testDetailBook(uniqueIdentifier = "")),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(JournalTestTags.SECTION).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.HEADING).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.RETRY).assertDoesNotExist()
    }

    // TC-63
    @Test
    fun tc63_aBlankIdentifierRendersNothing() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                JournalSection.Content(
                    context = testSectionContext(book = testDetailBook(uniqueIdentifier = "   ")),
                    modifier = Modifier,
                )
            }
        }

        rule.onNodeWithTag(JournalTestTags.SECTION).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.HEADING).assertDoesNotExist()
        rule.onNodeWithTag(JournalTestTags.RETRY).assertDoesNotExist()
    }
}
