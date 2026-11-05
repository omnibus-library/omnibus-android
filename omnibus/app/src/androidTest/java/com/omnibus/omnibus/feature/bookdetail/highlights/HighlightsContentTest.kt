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
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val NOW_MILLIS = 1_793_620_800_000L

private const val OTHER_DEVICE = "A passage kept on another device, with no text to show here."
private const val EMPTY_TEXT = "Highlight while you read to keep passages here."
private const val FAILED_TEXT = "Highlights couldn't be loaded."

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

    // TC-36
    @Test
    fun tc36_loadingShowsTheHeadingOnly() {
        setContent(HighlightsUIContent.Loading)

        rule.onNodeWithTag(HighlightsTestTags.HEADING).assertIsDisplayed()
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.EMPTY).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.FAILED).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.MORE).assertDoesNotExist()
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(0)
    }

    // TC-37
    @Test
    fun tc37_theHeadingIsAHeadingAndTheCountFollowsTheRows() {
        val state = setContent(HighlightsUIContent.Failed)

        rule.onNodeWithTag(HighlightsTestTags.HEADING)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertTextEquals("Highlights")
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertDoesNotExist()

        state.value = HighlightsUIState(HighlightsUIContent.Loaded(emptyList()), NOW_MILLIS)
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertDoesNotExist()

        state.value = HighlightsUIState(HighlightsUIContent.Loaded(listOf(testRow(1))), NOW_MILLIS)
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertTextEquals("1")

        state.value = HighlightsUIState(
            HighlightsUIContent.Loaded(listOf(testRow(1), testRow(2), testRow(3))),
            NOW_MILLIS,
        )
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertTextEquals("3")
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(3)
    }

    // TC-40
    @Test
    fun tc40_anEmptyBookShowsTheEmptyLineEightDpBelowTheHeading() {
        setContent(HighlightsUIContent.Loaded(emptyList()))

        rule.onNodeWithTag(HighlightsTestTags.EMPTY).assertTextEquals(EMPTY_TEXT)
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertDoesNotExist()
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(0)
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertDoesNotExist()

        val heading = rule.onNodeWithTag(HighlightsTestTags.HEADING).bounds()
        val empty = rule.onNodeWithTag(HighlightsTestTags.EMPTY).bounds()
        val gap = rule.dpOf(empty.top - heading.bottom)
        assertTrue("empty line must sit 8 dp under the heading, was $gap dp", kotlin.math.abs(gap - 8f) <= 1f)
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

    // TC-43
    @Test
    fun tc43_rowVariantsShowWhatTheyHave() {
        val rows = listOf(
            testRow(1, text = "Only passage", note = null, chapterNumber = 2),
            testRow(2, text = null, note = "Kobo note", createdAtMillis = 1_761_775_445_000L, chapterNumber = null),
            testRow(3, text = "No chapter", chapterNumber = null, createdAtMillis = 1_761_775_445_000L),
            testRow(4, text = "Chapter alone", chapterNumber = 3, createdAtMillis = null),
        )
        setContent(HighlightsUIContent.Loaded(rows))

        rule.onAllNodesWithTag(HighlightsTestTags.note(1), useUnmergedTree = true).assertCountEquals(0)

        rule.onNodeWithTag(HighlightsTestTags.noText(2), useUnmergedTree = true).assertTextEquals(OTHER_DEVICE)
        rule.onAllNodesWithTag(HighlightsTestTags.text(2), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(HighlightsTestTags.note(2), useUnmergedTree = true).assertTextEquals("Kobo note")
        rule.onNodeWithTag(HighlightsTestTags.footer(2), useUnmergedTree = true)
            .assertTextEquals(expectedFooter(null, rows[1].createdAtMillis, NOW_MILLIS)!!)

        rule.onNodeWithTag(HighlightsTestTags.footer(3), useUnmergedTree = true)
            .assertTextEquals(expectedFooter(null, rows[2].createdAtMillis, NOW_MILLIS)!!)

        rule.onNodeWithTag(HighlightsTestTags.footer(4), useUnmergedTree = true).assertTextEquals(detailChapter(3))
    }

    // TC-44
    @Test
    fun tc44_theFooterRowIsAtLeast44DpWideAndTheListHasNoButtonOrClickableRow() {
        val rows = listOf(
            testRow(1, text = "No footer text", chapterNumber = null, createdAtMillis = null),
            testRow(2, text = "Normal", chapterNumber = 1),
        )
        setContent(HighlightsUIContent.Loaded(rows))

        rule.onAllNodesWithTag(HighlightsTestTags.footer(1), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(HighlightsTestTags.footerRow(1), useUnmergedTree = true).assertExists()
        rows.forEach { row ->
            val rowBounds = rule.onNodeWithTag(HighlightsTestTags.row(row.id)).bounds()
            val footerBounds = rule.onNodeWithTag(HighlightsTestTags.footerRow(row.id), useUnmergedTree = true).bounds()
            assertTrue(
                "footer row ${row.id} must be at least 44 dp tall, was ${rule.dpOf(footerBounds.height)} dp",
                rule.dpOf(footerBounds.height) >= 44f - 1f,
            )
            assertTrue(
                "footer row ${row.id} must span the row's width",
                kotlin.math.abs(footerBounds.left - rowBounds.left) <= rule.pxOf(1f) &&
                    kotlin.math.abs(footerBounds.right - rowBounds.right) <= rule.pxOf(1f),
            )
        }
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
