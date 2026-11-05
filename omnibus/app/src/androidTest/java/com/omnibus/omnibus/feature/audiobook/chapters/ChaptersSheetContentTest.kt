package com.omnibus.omnibus.feature.audiobook.chapters

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ChaptersSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun rows(count: Int, title: (Int) -> String = { "Chapter $it" }) =
        (1..count).map { ChapterRowModel(it, title(it), "${it}:00") }

    private fun setSheet(
        state: MutableState<ChaptersUiState>,
        onChosen: (Int) -> Unit = {},
        onDone: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    ChaptersSheetContent(state.value, onChosen, onDone, Modifier.fillMaxSize())
                }
            }
        }
    }

    // P-CHP-1: the title is a heading, and Done is a pill that reports once
    @Test
    fun titleIsHeadingAndDoneReports() {
        var done = 0
        setSheet(mutableStateOf(ChaptersUiState(rows(3), currentIndex = 0)), onDone = { done++ })

        rule.onNodeWithTag(ChaptersTestTags.TITLE)
            .assertTextEquals(context.getString(R.string.audiobook_chapters_title))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        rule.onNodeWithTag(ChaptersTestTags.DONE)
            .assertTextEquals(context.getString(R.string.audiobook_chapters_done))
            .performClick()

        assertEquals(1, done)
    }

    // P-CHP-2, P-CHP-AC2: each row shows its number, title and length
    @Test
    fun rowShowsNumberTitleAndLength() {
        setSheet(mutableStateOf(ChaptersUiState(rows(3) { "Part $it" }, currentIndex = 0)))

        rule.onNodeWithTag(ChaptersTestTags.number(1), useUnmergedTree = true).assertTextEquals("2")
        rule.onNodeWithTag(ChaptersTestTags.title(1), useUnmergedTree = true).assertTextEquals("Part 2")
        rule.onNodeWithTag(ChaptersTestTags.length(1), useUnmergedTree = true).assertTextEquals("2:00")
    }

    // P-CHP-3, P-CHP-AC2: only the current row has the progress line
    @Test
    fun onlyTheCurrentRowHasProgressLine() {
        setSheet(mutableStateOf(ChaptersUiState(rows(4), currentIndex = 2, currentProgress = 0.5f)))

        rule.onAllNodesWithTag(ChaptersTestTags.PROGRESS, useUnmergedTree = true).assertCountEquals(1)
        rule.onNodeWithTag(ChaptersTestTags.PROGRESS, useUnmergedTree = true).assertIsDisplayed()
    }

    // P-A11Y-3: a row reads as "Chapter {n}, {title}, {length}"
    @Test
    fun rowReadsNumberTitleAndLength() {
        setSheet(mutableStateOf(ChaptersUiState(rows(3) { "Part $it" }, currentIndex = 0)))

        val expected = context.getString(R.string.audiobook_chapters_row_description, 2, "Part 2", "2:00")
        val description = rule.onNodeWithTag(ChaptersTestTags.row(1)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf(expected), description)
    }

    // P-A11Y-3: only the current row is announced as current
    @Test
    fun onlyCurrentRowIsAnnouncedAsCurrent() {
        setSheet(mutableStateOf(ChaptersUiState(rows(3), currentIndex = 1)))

        val current = context.getString(R.string.audiobook_chapters_current)
        fun stateOf(index: Int) = rule.onNodeWithTag(ChaptersTestTags.row(index)).fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.StateDescription)

        assertEquals(current, stateOf(1))
        assertEquals(null, stateOf(0))
        assertEquals(null, stateOf(2))
    }

    // P-CHP-5, P-CHP-AC3: a tap reports the row's index
    @Test
    fun tappingARowReportsItsIndex() {
        val chosen = mutableListOf<Int>()
        setSheet(mutableStateOf(ChaptersUiState(rows(3), currentIndex = 0)), onChosen = { chosen += it })

        rule.onNodeWithTag(ChaptersTestTags.row(2)).performClick()

        assertEquals(listOf(2), chosen)
    }

    // P-CHP-4, P-CHP-AC1: a long list opens with the current chapter in view, not at the top
    @Test
    fun opensWithCurrentChapterInView() {
        setSheet(mutableStateOf(ChaptersUiState(rows(200), currentIndex = 150)))

        rule.waitForIdle()
        rule.onNodeWithTag(ChaptersTestTags.row(150)).assertIsDisplayed()
        rule.onAllNodesWithTag(ChaptersTestTags.row(0)).assertCountEquals(0)
    }
}
