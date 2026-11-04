package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.audiobook.player.PlayerActions
import com.omnibus.omnibus.feature.audiobook.player.PlayerTestTags
import com.omnibus.omnibus.feature.audiobook.player.PlayerToolRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SyncHereToolCellTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val syncHereLabel = context.getString(R.string.audiobook_bookmarks_sync_here)
    private val syncedLabel = context.getString(R.string.audiobook_bookmarks_synced)
    private val needsConnection = context.getString(R.string.audiobook_bookmarks_needs_connection)

    /** Renders the tool row with the Sync here cell built from [state] by the real rules, and [onClick] as its tap. */
    private fun ComposeContentTestRule.setSyncCell(state: State<SyncHereUIState>, onClick: () -> Unit) {
        setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    val button = syncHereButton(
                        state = state.value,
                        syncHereLabel = syncHereLabel,
                        syncedLabel = syncedLabel,
                        needsConnection = needsConnection,
                        onClick = onClick,
                    )
                    PlayerToolRow(rate = 1.0, hasEbook = true, actions = PlayerActions(syncHere = button))
                }
            }
        }
    }

    // TC64, R9, P-BMK-4: the label reads Synced once the state says so, and the cell stays tappable
    @Test
    fun syncedLabelReplacesSyncHere() {
        var taps = 0
        val state = mutableStateOf(SyncHereUIState(visible = true, online = true, synced = false))
        rule.setSyncCell(state) { taps++ }

        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assert(hasText(syncHereLabel))

        rule.runOnIdle { state.value = SyncHereUIState(visible = true, online = true, synced = true) }
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assert(hasText(syncedLabel))
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).performClick()
        rule.waitForIdle()

        assertEquals(1, taps)
    }

    // TC65, R10, P-A11Y-5, AC4: offline, the cell is disabled, speaks why, and does not fire
    @Test
    fun offlineSyncCellIsDisabledAndAnnouncesWhy() {
        var taps = 0
        val state = mutableStateOf(SyncHereUIState(visible = true, online = false, synced = false))
        rule.setSyncCell(state) { taps++ }

        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assertIsNotEnabled()
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC)
            .assert(hasContentDescription("$syncHereLabel, $needsConnection"))
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).performClick()
        rule.waitForIdle()

        assertEquals(0, taps)
    }

    // TC67, R10: back online, the cell is enabled again, reads Sync here, and fires
    @Test
    fun syncCellReenablesWhenOnline() {
        var taps = 0
        val state = mutableStateOf(SyncHereUIState(visible = true, online = false, synced = false))
        rule.setSyncCell(state) { taps++ }
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assertIsNotEnabled()

        rule.runOnIdle { state.value = SyncHereUIState(visible = true, online = true, synced = false) }
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assertIsEnabled()
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assert(hasText(syncHereLabel))
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).performClick()
        rule.waitForIdle()

        assertEquals(1, taps)
    }
}
