package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.omnibus.omnibus.feature.bookdetail.scrollTo
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private fun rows(count: Int): List<HighlightRowModel> =
    (1..count).map { testRow(it.toLong(), text = "Passage $it", chapterNumber = 1) }

class HighlightsLongListTest {

    @get:Rule
    val rule = createComposeRule()

    private fun ComposeContentTestRule.setList(state: androidx.compose.runtime.MutableState<HighlightsUIState>, scroll: ScrollState) {
        setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(scroll)) {
                    HighlightsContent(state.value, onRetry = {})
                }
            }
        }
    }

    private fun composedRows(): Int = rule.onAllNodes(highlightRowMatcher).fetchSemanticsNodes().size

    /** Scrolls the page to its end, one round at a time, until the list has no more pages or [maxRounds] pass. */
    private fun scrollToEnd(scroll: ScrollState, maxRounds: Int = 100): Int {
        var rounds = 0
        while (rounds < maxRounds && rule.onAllNodesWithTagOrEmpty(HighlightsTestTags.MORE)) {
            rule.scrollTo(scroll)
            rounds++
        }
        return rounds
    }

    private fun ComposeContentTestRule.onAllNodesWithTagOrEmpty(tag: String): Boolean =
        onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()

    // TC-48
    @Test
    fun tc48_aThousandRowsComposeOnlyTheFirstPage() {
        val state = mutableStateOf(HighlightsUIState(HighlightsUIContent.Loaded(rows(1000)), 0L))
        rule.setList(state, ScrollState(0))

        rule.onNodeWithTag(HighlightsTestTags.row(1)).assertExists()
        rule.onNodeWithTag(HighlightsTestTags.row(501)).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertTextEquals("1000")
        rule.onNodeWithTag(HighlightsTestTags.MORE).assertExists()
        val composed = composedRows()
        assertTrue("first page must be composed, was $composed", composed >= 20)
        assertTrue("composition must stay bounded, was $composed", composed < 1000)
    }

    // TC-49
    @Test
    fun tc49_scrollingComposesMoreAndReachesTheLastRow() {
        val scroll = ScrollState(0)
        val state = mutableStateOf(HighlightsUIState(HighlightsUIContent.Loaded(rows(1000)), 0L))
        rule.setList(state, scroll)
        val before = composedRows()

        rule.scrollTo(scroll)
        assertTrue("one scroll round must compose more rows: $before to ${composedRows()}", composedRows() > before)

        scrollToEnd(scroll)

        rule.onNodeWithTag(HighlightsTestTags.row(1000)).assertExists()
        rule.onNodeWithTag(HighlightsTestTags.row(1000)).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag(HighlightsTestTags.MORE).assertDoesNotExist()
    }

    // TC-50
    @Test
    fun tc50_aListOfTwentyComposesAllAtOnceAndTwentyOneWaitsForScroll() {
        val twenty = mutableStateOf(HighlightsUIState(HighlightsUIContent.Loaded(rows(20)), 0L))
        rule.setList(twenty, ScrollState(0))
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(20)
        rule.onNodeWithTag(HighlightsTestTags.MORE).assertDoesNotExist()
    }

    @Test
    fun tc50_twentyOneRowsComposeTheTwentyFirstAfterScrolling() {
        val scroll = ScrollState(0)
        val state = mutableStateOf(HighlightsUIState(HighlightsUIContent.Loaded(rows(21)), 0L))
        rule.setList(state, scroll)
        rule.onNodeWithTag(HighlightsTestTags.MORE).assertExists()

        scrollToEnd(scroll)

        rule.onNodeWithTag(HighlightsTestTags.row(21)).assertExists()
        rule.onNodeWithTag(HighlightsTestTags.MORE).assertDoesNotExist()
    }

    // TC-51
    @Test
    fun tc51_theComposedPageSurvivesARestoreAndAShrinkingListIsSafe() {
        val tester = StateRestorationTester(rule)
        val scroll = ScrollState(0)
        val state = mutableStateOf(HighlightsUIState(HighlightsUIContent.Loaded(rows(1000)), 0L))
        tester.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.verticalScroll(scroll)) {
                    HighlightsContent(state.value, onRetry = {})
                }
            }
        }
        var rounds = 0
        while (!rule.onAllNodesWithTagOrEmpty(HighlightsTestTags.row(41)) && rounds < 100) {
            rule.scrollTo(scroll)
            rounds++
        }
        rule.onNodeWithTag(HighlightsTestTags.row(41)).assertExists()

        tester.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag(HighlightsTestTags.row(41)).assertExists()

        state.value = HighlightsUIState(HighlightsUIContent.Loaded(rows(5)), 0L)
        rule.waitForIdle()
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(5)
        rule.onNodeWithTag(HighlightsTestTags.MORE).assertDoesNotExist()
    }
}
