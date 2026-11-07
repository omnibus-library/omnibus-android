package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerScreenContentTest {

    @get:Rule
    val rule = createComposeRule()

    // R3, P-SCR-AC6 gate
    @Test
    fun checkingGateShowsNoControlsAndNoDialog() {
        var closed = 0
        rule.setPlayer(fullUiState(gate = PlayerGate.Checking), onClose = { closed++ })

        rule.onNodeWithTag(PlayerTestTags.ROOT).assertIsDisplayed()
        rule.onAllNodesWithTag(PlayerTestTags.TRANSPORT).assertCountEquals(0)
        rule.onAllNodesWithTag(PlayerTestTags.SCRUBBER).assertCountEquals(0)
        rule.onAllNodesWithText("Can't play offline").assertCountEquals(0)
        assertEquals(0, closed)
    }

    // R20 (P-SCR edge case: unloaded book closes the player)
    @Test
    fun leaveFlagClosesPlayerOnce() {
        val state = mutableStateOf(fullUiState(leave = false))
        var closed = 0
        rule.setPlayer(state, onClose = { closed++ })
        rule.waitForIdle()
        assertEquals(0, closed)

        rule.runOnIdle { state.value = state.value.copy(leave = true) }
        rule.waitForIdle()
        assertEquals(1, closed)

        // Recomposition with the flag still set must not close the player again.
        rule.runOnIdle { state.value = state.value.copy(isPlaying = false) }
        rule.waitForIdle()
        assertEquals(1, closed)
    }

    // P-SCR-7, P-A11Y-1: the chapter row opens the chapter list
    @Test
    fun chapterRowOpensChapters() {
        val actions = RecordedActions()
        rule.setPlayer(fullUiState(chapterTitle = "004"), actions = actions.actions())

        rule.onNodeWithTag(PlayerTestTags.CHAPTER_ROW).performClick()
        rule.waitForIdle()
        assertEquals(1, actions.chapters)
    }
}
