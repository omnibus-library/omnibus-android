package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerOfflineDialogTest {

    @get:Rule
    val rule = createComposeRule()

    // R3, P-SCR-2, AND-RUSE-7
    @Test
    fun offlineGateShowsDialogWithTitledMessageAndNoControls() {
        var closed = 0
        rule.setPlayer(fullUiState(gate = PlayerGate.Offline, title = "Dune"), onClose = { closed++ })

        rule.onNodeWithText("Can't play offline").assertIsDisplayed()
        rule.onNodeWithText("Dune", substring = true).assertIsDisplayed()
        rule.onNodeWithText("listen anywhere", substring = true).assertIsDisplayed()
        rule.onAllNodesWithTag(PlayerTestTags.TRANSPORT).assertCountEquals(0)
        rule.onAllNodesWithTag(PlayerTestTags.SCRUBBER).assertCountEquals(0)
        rule.onAllNodesWithTag(PlayerTestTags.TOOL_ROW).assertCountEquals(0)

        rule.onNodeWithText("OK").performClick()
        rule.waitForIdle()
        assertEquals(1, closed)
    }

    // R3: back closes the offline dialog, and so the player
    @Test
    fun offlineDialogOkAndBackBothClose() {
        var closed = 0
        rule.setPlayer(fullUiState(gate = PlayerGate.Offline, title = "Dune"), onClose = { closed++ })
        rule.waitForIdle()

        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(1, closed)
    }

    // R3, P-SCR-2 (title-less default)
    @Test
    fun offlineGateWithBlankTitleUsesUntitledMessage() {
        rule.setPlayer(fullUiState(gate = PlayerGate.Offline, title = ""))

        rule.onNodeWithText("This audiobook isn't downloaded to this device", substring = true).assertIsDisplayed()
        rule.onAllNodesWithText("“”", substring = true).assertCountEquals(0)
    }
}
