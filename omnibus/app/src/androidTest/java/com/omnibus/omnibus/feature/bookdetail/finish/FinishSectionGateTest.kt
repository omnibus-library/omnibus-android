package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

class FinishSectionGateTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue97_tc40_aBookWithNoFileShowsNothingAndNeedsNoHilt() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                val context = testSectionContext(book = testDetailBook(formats = emptyList()))
                Box(Modifier.fillMaxSize()) {
                    FinishCardSection.Content(context, Modifier)
                    FinishCelebrationSection.Content(context, Modifier)
                }
            }
        }

        rule.onNodeWithTag(FinishTestTags.CARD).assertDoesNotExist()
        rule.onNodeWithTag(FinishTestTags.BURST).assertDoesNotExist()
        rule.onNodeWithTag(FinishTestTags.COVER_TAP).assertDoesNotExist()
    }
}
