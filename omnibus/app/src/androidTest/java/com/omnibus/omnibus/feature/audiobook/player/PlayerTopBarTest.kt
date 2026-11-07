package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
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
