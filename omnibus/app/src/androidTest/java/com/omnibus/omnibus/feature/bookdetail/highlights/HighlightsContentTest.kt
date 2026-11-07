package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private const val NOW_MILLIS = 1_793_620_800_000L

/** Retry, semantics and TalkBack text. HighlightsScreenshotTest covers how each state looks. */
class HighlightsContentTest {

    @get:Rule
    val rule = createComposeRule()

    /** Renders [HighlightsContent] in dark theme inside a scrolling column, as the panel does. */
    private fun setContent(content: HighlightsUIContent, onRetry: () -> Unit = {}) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HighlightsContent(HighlightsUIState(content, NOW_MILLIS), onRetry = onRetry)
                }
            }
        }
    }

    // TC-37
    @Test
    fun tc37_theHeadingIsAHeading() {
        setContent(HighlightsUIContent.Failed)

        rule.onNodeWithTag(HighlightsTestTags.HEADING)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    // TC-41
    @Test
    fun tc41_tryAgainCallsRetryOnce() {
        var retries = 0
        setContent(HighlightsUIContent.Failed, onRetry = { retries++ })

        rule.onNodeWithTag(HighlightsTestTags.RETRY).performClick()
        rule.waitForIdle()
        assertEquals(1, retries)
    }

    // TC-46
    @Test
    fun tc46_aRowNamesItsColourAndMergesItsText() {
        val row = testRow(1, text = "Passage 1", color = HighlightColor.Green)
        setContent(HighlightsUIContent.Loaded(listOf(row)))

        rule.onNodeWithTag(HighlightsTestTags.row(row.id))
            .assert(hasText("Passage 1"))
            .assert(hasContentDescription("Green highlight"))
    }
}
