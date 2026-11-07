package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlayerOfflineDialogTest {

    @get:Rule
    val rule = createComposeRule()

    // R3, P-SCR-2, AND-RUSE-7: OK closes the offline dialog, and so the player
    @Test
    fun offlineDialogOkCloses() {
        var closed = 0
        rule.setPlayer(fullUiState(gate = PlayerGate.Offline, title = "Dune"), onClose = { closed++ })

        rule.onNodeWithText("OK").performClick()
        rule.waitForIdle()
        assertEquals(1, closed)
    }

    // R3: back closes the offline dialog, and so the player
    @Test
    fun offlineDialogBackCloses() {
        var closed = 0
        rule.setPlayer(fullUiState(gate = PlayerGate.Offline, title = "Dune"), onClose = { closed++ })
        rule.waitForIdle()

        Espresso.pressBack()
        rule.waitForIdle()
        assertEquals(1, closed)
    }
}
