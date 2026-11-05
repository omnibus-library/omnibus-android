package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // P-SCR-7, P-A11Y-1: the chapter row shows its title and opens the chapter list
    @Test
    fun chapterRowShowsTitleAndOpensChapters() {
        val actions = RecordedActions()
        rule.setPlayer(fullUiState(chapterTitle = "004"), actions = actions.actions())

        // The clickable row merges its children, so the title's own tag lives in the unmerged tree.
        rule.onNodeWithTag(PlayerTestTags.CHAPTER_TITLE, useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("004").assertIsDisplayed()

        val root = rule.boundsOf(PlayerTestTags.ROOT)
        val chapter = rule.onNodeWithTag(PlayerTestTags.CHAPTER_TITLE, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the chapter title should be left-aligned", chapter.left < root.width / 4f)

        val label = rule.onNodeWithTag(PlayerTestTags.CHAPTER_ROW).fetchSemanticsNode()
            .config[SemanticsActions.OnClick].label
        assertEquals("Open chapters", label)

        rule.onNodeWithTag(PlayerTestTags.CHAPTER_ROW).performClick()
        rule.waitForIdle()
        assertEquals(1, actions.chapters)
    }

    // P-SCR-7, P-SCR-AC5: the row needs the action, and chapters
    @Test
    fun chapterRowHiddenWithoutActionOrChapters() {
        val actions = mutableStateOf(PlayerActions())
        val state = mutableStateOf(fullUiState(hasChapters = true))
        rule.setPlayer(state, actions = actions)

        rule.onAllNodesWithTag(PlayerTestTags.CHAPTER_ROW).assertCountEquals(0)
        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.TRANSPORT).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.TOOL_ROW).assertIsDisplayed()

        rule.runOnIdle { actions.value = PlayerActions(onOpenChapters = {}) }
        rule.waitForIdle()
        rule.onNodeWithTag(PlayerTestTags.CHAPTER_ROW).assertIsDisplayed()

        rule.runOnIdle { state.value = state.value.copy(hasChapters = false) }
        rule.waitForIdle()
        rule.onAllNodesWithTag(PlayerTestTags.CHAPTER_ROW).assertCountEquals(0)
        rule.onNodeWithTag(PlayerTestTags.SCRUBBER).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.TRANSPORT).assertIsDisplayed()
        rule.onNodeWithTag(PlayerTestTags.TOOL_ROW).assertIsDisplayed()
    }

    // R15, P-SCR-12: the overlays appear one at a time, over the cover
    @Test
    fun preparingAndBufferingOverlaysSitOverTheCover() {
        val state = mutableStateOf(fullUiState(isPreparing = true, isBuffering = false))
        rule.setPlayer(state)

        val cover = rule.boundsOf(PlayerTestTags.COVER)
        rule.onNodeWithText("Preparing audio").assertIsDisplayed()
        assertTrue("the preparing label should be over the cover", isWithin(rule.onNodeWithText("Preparing audio").fetchSemanticsNode().boundsInRoot, cover))
        rule.onAllNodesWithText("Buffering").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(isPreparing = false, isBuffering = true) }
        rule.waitForIdle()
        rule.onNodeWithText("Buffering").assertIsDisplayed()
        rule.onAllNodesWithText("Preparing audio").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(isBuffering = false) }
        rule.waitForIdle()
        rule.onAllNodesWithText("Buffering").assertCountEquals(0)
        rule.onAllNodesWithText("Preparing audio").assertCountEquals(0)
    }
}
