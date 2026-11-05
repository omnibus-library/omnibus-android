package com.omnibus.omnibus.feature.audiobook.sleep

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.audiobook.playback.Chapter
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.feature.bookdetail.more.pressBackKey
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The sleep sheet's body and window: the countdown, the selection and the taps (R9-R14). SleepSheetScreenshotTest covers
 * how the grid looks.
 */
class SleepSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = PlayingBook("book-1", 11L, "The Book", "An Author", false)

    private val threeChapters = listOf(
        Chapter(1L, "One", 0.0, 600.0),
        Chapter(2L, "Two", 600.0, 1200.0),
        Chapter(3L, "Three", 1800.0, 900.0),
    )

    /** A playing book with three chapters, at [position], with [timer] running. */
    private fun playing(position: Double, timer: SleepTimerState? = null): PlaybackState = PlaybackState(
        book = book,
        chapters = threeChapters,
        positionSeconds = position,
        durationSeconds = 2700.0,
        sleepTimer = timer,
    )

    private fun showSheet(ui: State<SleepUIState>, onSelect: (SleepChoice) -> Unit = {}) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                SleepSheetContent(ui.value, onSelect)
            }
        }
    }

    /** Checks that only [expected] is selected among [choices], and that each is a radio button at least 44 dp high. */
    private fun assertSelectedOnly(choices: List<SleepChoice>, expected: SleepChoice) {
        choices.forEach { choice ->
            val cell = rule.onNodeWithTag(SleepTestTags.of(choice))
            if (choice == expected) cell.assertIsSelected() else cell.assertIsNotSelected()
            cell.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            cell.assertHeightIsAtLeast(44.dp)
        }
    }

    // TC-36, R12, R14: the countdown shows only while a timer runs, in the clock format, and is not a live region
    @Test
    fun issue169_tc36_headerCountdownShowsOnlyWhileATimerRunsAndIsNotLive() {
        val ui = mutableStateOf(sleepUIState(playing(100.0)))
        showSheet(ui)
        rule.onAllNodesWithTag(SleepTestTags.HEADER_COUNTDOWN).assertCountEquals(0)

        rule.runOnIdle { ui.value = sleepUIState(playing(100.0, SleepTimerState(SleepTimerOption.After(15), 899.0))) }
        rule.onNodeWithTag(SleepTestTags.HEADER_COUNTDOWN).assertTextEquals("0:14:59 remaining")
        rule.onNodeWithTag(SleepTestTags.HEADER_COUNTDOWN)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion))

        rule.runOnIdle { ui.value = sleepUIState(playing(100.0, SleepTimerState(SleepTimerOption.After(60), 3661.0))) }
        rule.onNodeWithTag(SleepTestTags.HEADER_COUNTDOWN).assertTextEquals("1:01:01 remaining")

        rule.runOnIdle { ui.value = sleepUIState(playing(100.0)) }
        rule.onAllNodesWithTag(SleepTestTags.HEADER_COUNTDOWN).assertCountEquals(0)
    }

    // TC-37, R11, R14: exactly the chosen cell is selected, every cell is a radio button, and the grid is a selectable group
    @Test
    fun issue169_tc37_exactlyTheChosenCellIsSelectedAndEachIsARadioButton() {
        val ui = mutableStateOf(sleepUIState(playing(700.0)))
        showSheet(ui)
        assertSelectedOnly(ui.value.choices, SleepChoice.Off)

        rule.runOnIdle { ui.value = sleepUIState(playing(700.0, SleepTimerState(SleepTimerOption.After(45), 2700.0))) }
        assertSelectedOnly(ui.value.choices, SleepChoice.Timed(45))

        rule.runOnIdle { ui.value = sleepUIState(playing(700.0, SleepTimerState(SleepTimerOption.EndOfChapter, 1000.0))) }
        assertSelectedOnly(ui.value.choices, SleepChoice.EndOfChapter(2))

        rule.onNodeWithTag(SleepTestTags.OPTIONS)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
    }

    // TC-38, R11: a tap on each cell reports that cell's choice, once, in tap order
    @Test
    fun issue169_tc38_tappingEachCellReportsItsChoiceOnce() {
        val picked = mutableListOf<SleepChoice>()
        val choices = sleepChoices(playing(700.0))
        showSheet(mutableStateOf(sleepUIState(playing(700.0))), onSelect = { picked += it })
        choices.forEach { rule.onNodeWithTag(SleepTestTags.of(it)).performClick() }
        rule.waitForIdle()
        assertEquals(choices, picked)
    }

    // TC-39, R9, R11: a pick keeps the sheet open, and system back dismisses it (scrim and drag are M-1)
    @Test
    fun issue169_tc39_aPickKeepsTheSheetOpenAndBackDismissesIt() {
        val picked = mutableListOf<SleepChoice>()
        var dismisses = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                SleepSheetModal(
                    uiState = sleepUIState(playing(100.0)),
                    onSelect = { picked += it },
                    onDismiss = { dismisses++ },
                )
            }
        }
        rule.onNodeWithTag(SleepTestTags.SHEET).assertIsDisplayed()
        rule.onNodeWithTag(SleepTestTags.timed(30)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(SleepChoice.Timed(30)), picked)
        assertEquals(0, dismisses)
        rule.onNodeWithTag(SleepTestTags.SHEET).assertIsDisplayed()

        rule.pressBackKey()
        rule.waitUntil(5_000) { dismisses == 1 }
        rule.waitForIdle()
        assertEquals(1, dismisses)
    }
}
