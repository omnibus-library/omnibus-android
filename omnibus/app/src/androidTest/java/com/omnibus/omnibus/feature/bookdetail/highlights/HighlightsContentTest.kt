package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
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

private const val FAILED_TEXT = "Highlights couldn't be loaded."

/** Retry, semantics and TalkBack text. HighlightsScreenshotTest covers how each state looks. */
class HighlightsContentTest {

    @get:Rule
    val rule = createComposeRule()

    /** Renders [HighlightsContent] in dark theme inside a scrolling column, as the panel does, and returns its state. */
    private fun setContent(
        initial: HighlightsUIContent,
        onRetry: () -> Unit = {},
        darkTheme: Boolean = true,
    ) = mutableStateOf(HighlightsUIState(initial, NOW_MILLIS)).also { holder ->
        rule.setContent {
            OmnibusTheme(darkTheme = darkTheme) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HighlightsContent(holder.value, onRetry = onRetry)
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
    fun tc41_tryAgainCallsRetryOnceAndTheListComesBack() {
        var retries = 0
        val state = setContent(HighlightsUIContent.Failed, onRetry = { retries++ })

        rule.onNodeWithTag(HighlightsTestTags.FAILED).assertTextEquals(FAILED_TEXT)
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, retries)

        state.value = HighlightsUIState(HighlightsUIContent.Loading, NOW_MILLIS)
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.FAILED).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertDoesNotExist()
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(0)

        state.value = HighlightsUIState(HighlightsUIContent.Loaded(listOf(testRow(1), testRow(2))), NOW_MILLIS)
        rule.waitForIdle()
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(2)
    }

    // TC-44
    @Test
    fun tc44_theListHasNoButtonOrClickableRow() {
        setContent(
            HighlightsUIContent.Loaded(
                listOf(
                    testRow(1, text = "No footer text", chapterNumber = null, createdAtMillis = null),
                    testRow(2, text = "Normal", chapterNumber = 1),
                ),
            ),
        )

        rule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-46
    @Test
    fun tc46_eachRowNamesItsColourAndMergesItsText() {
        val colours = HighlightColor.entries
        val rows = colours.mapIndexed { index, colour ->
            testRow(index + 1L, text = "Passage ${index + 1}", color = colour, chapterNumber = index + 1)
        }
        setContent(HighlightsUIContent.Loaded(rows))

        rows.forEach { row ->
            val label = when (row.color) {
                HighlightColor.Amber -> "Amber highlight"
                HighlightColor.Green -> "Green highlight"
                HighlightColor.Blue -> "Blue highlight"
                HighlightColor.Rose -> "Rose highlight"
                HighlightColor.Violet -> "Violet highlight"
            }
            rule.onNodeWithTag(HighlightsTestTags.ribbon(row.id), useUnmergedTree = true)
                .assertContentDescriptionEquals(label)
            rule.onNodeWithTag(HighlightsTestTags.row(row.id))
                .assert(hasText(requireNotNull(row.text)))
                .assert(hasText(expectedFooter(row.chapterNumber, row.createdAtMillis, NOW_MILLIS)!!))
                .assert(hasContentDescription(label))
        }
    }
}
