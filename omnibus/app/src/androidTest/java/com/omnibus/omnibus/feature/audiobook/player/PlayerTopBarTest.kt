package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import androidx.compose.runtime.mutableStateOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerTopBarTest {

    @get:Rule
    val rule = createComposeRule()

    private fun runningTimer(seconds: Double) =
        SleepTimerState(option = SleepTimerOption.After(minutes = 10), remainingSeconds = seconds)

    // R6, P-A11Y-1: the chevron collapses the player and does nothing else
    @Test
    fun chevronCallsOnCloseOnly() {
        val actions = RecordedActions()
        var closed = 0
        rule.setPlayer(fullUiState(hasEbook = true, sleepTimer = runningTimer(600.0)), actions = actions.actions(), onClose = { closed++ })

        rule.onNodeWithTag(PlayerTestTags.CLOSE).performClick()
        rule.waitForIdle()

        assertEquals(1, closed)
        assertEquals(0, actions.openBook + actions.openBookmarks + actions.sync + actions.chapters)
    }

    // R6: with no actions, only the chevron shows
    @Test
    fun topBarHidesRightButtonsWhenActionsNull() {
        rule.setPlayer(fullUiState(hasEbook = true, sleepTimer = runningTimer(600.0)))

        rule.onNodeWithTag(PlayerTestTags.CLOSE).assertIsDisplayed()
        rule.onAllNodesWithTag(PlayerTestTags.OPEN_BOOK).assertCountEquals(0)
        rule.onAllNodesWithTag(PlayerTestTags.BOOKMARKS).assertCountEquals(0)
        rule.onAllNodesWithTag(PILL_TAG).assertCountEquals(0)
    }

    // R6, P-SCR edge cases: the book button needs an ebook, the pill a running timer
    @Test
    fun bookButtonNeedsAnEbookAndPillNeedsARunningTimer() {
        val state = mutableStateOf(fullUiState(hasEbook = false, sleepTimer = null))
        val actions = RecordedActions()
        rule.setPlayer(state, actions = mutableStateOf(actions.actions()))

        rule.onAllNodesWithTag(PlayerTestTags.OPEN_BOOK).assertCountEquals(0)
        rule.onNodeWithTag(PlayerTestTags.BOOKMARKS).assertIsDisplayed()
        rule.onAllNodesWithTag(PILL_TAG).assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(hasEbook = true, sleepTimer = runningTimer(0.0)) }
        rule.waitForIdle()
        rule.onNodeWithTag(PlayerTestTags.OPEN_BOOK).assertIsDisplayed()
        rule.onAllNodesWithTag(PILL_TAG).assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(sleepTimer = runningTimer(600.0)) }
        rule.waitForIdle()
        rule.onNodeWithTag(PILL_TAG).assertIsDisplayed()
    }

    // R6, P-A11Y-1: the book and bookmarks buttons run their actions once each
    @Test
    fun bookAndBookmarkButtonsInvokeTheirActions() {
        val actions = RecordedActions()
        rule.setPlayer(fullUiState(hasEbook = true), actions = actions.actions())

        rule.onNodeWithTag(PlayerTestTags.OPEN_BOOK).performClick()
        rule.onNodeWithTag(PlayerTestTags.BOOKMARKS).performClick()
        rule.waitForIdle()

        assertEquals(1, actions.openBook)
        assertEquals(1, actions.openBookmarks)
    }
}
