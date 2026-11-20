package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.audiobook.player.PlayerActions
import com.omnibus.omnibus.feature.audiobook.player.PlayerTestTags
import com.omnibus.omnibus.feature.audiobook.player.PlayerToolRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class SyncHereToolCellTest {

    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val syncHereLabel = context.getString(R.string.audiobook_bookmarks_sync_here)
    private val syncingLabel = context.getString(R.string.audiobook_bookmarks_syncing)
    private val syncedLabel = context.getString(R.string.audiobook_bookmarks_synced)
    private val failedLabel = context.getString(R.string.audiobook_bookmarks_sync_failed)
    private val needsConnection = context.getString(R.string.audiobook_bookmarks_needs_connection)

    /** Renders the full tool row, as PlayerScreenContent places it, with the Sync here cell built from [state] by the real rules. */
    private fun ComposeContentTestRule.setSyncCell(state: State<SyncHereUIState>, onClick: () -> Unit) {
        setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    val button = syncHereButton(
                        state = state.value,
                        syncHereLabel = syncHereLabel,
                        syncingLabel = syncingLabel,
                        syncedLabel = syncedLabel,
                        syncFailedLabel = failedLabel,
                        needsConnection = needsConnection,
                        onClick = onClick,
                    )
                    PlayerToolRow(
                        rate = 1.25,
                        hasEbook = true,
                        actions = PlayerActions(
                            onOpenSpeed = {},
                            onOpenCarMode = {},
                            onOpenSleep = {},
                            onAddBookmark = {},
                            syncHere = button,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
                    )
                }
            }
        }
    }

    // TC-19, R8, P-A11Y-5, AC7: offline, a failed cell is disabled, speaks why, and does not fire
    @Test
    fun offlineSyncCellIsDisabledAndAnnouncesWhy() {
        var taps = 0
        val state = mutableStateOf(SyncHereUIState(visible = true, online = false, status = SyncHereStatus.Failed))
        rule.setSyncCell(state) { taps++ }

        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assertIsNotEnabled()
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC)
            .assert(hasContentDescription("$failedLabel, $needsConnection"))
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).performClick()
        rule.waitForIdle()

        assertEquals(0, taps)
    }

    // TC-18, R3, AC1: while Syncing, the cell reads Syncing…, is disabled, and does not fire
    @Test
    fun syncingCellIsDisabledAndDoesNotFire() {
        var taps = 0
        val state = mutableStateOf(SyncHereUIState(visible = true, online = true, status = SyncHereStatus.Syncing))
        rule.setSyncCell(state) { taps++ }

        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assertIsNotEnabled()
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).assert(hasText(syncingLabel))
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).performClick()
        rule.waitForIdle()

        assertEquals(0, taps)
    }

    // TC-20, R12, AC8: each Sync label stays on one line in the full row, at whatever width the window has
    @Test
    fun everySyncLabelFitsOnOneLineInTheFullRow() {
        val labels = mapOf(
            SyncHereStatus.Idle to syncHereLabel,
            SyncHereStatus.Syncing to syncingLabel,
            SyncHereStatus.Synced to syncedLabel,
            SyncHereStatus.Failed to failedLabel,
        )
        val state = mutableStateOf(SyncHereUIState(visible = true, online = true))
        rule.setSyncCell(state) {}

        SyncHereStatus.entries.forEach { status ->
            state.value = state.value.copy(status = status)
            rule.waitForIdle()

            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNode(hasText(labels.getValue(status)), useUnmergedTree = true)
                .fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult]
                .action!!
                .invoke(layouts)
            val layout = layouts.single()

            assertEquals("$status: line count", 1, layout.lineCount)
            assertFalse("$status: ellipsized", layout.isLineEllipsized(0))
            assertFalse("$status: overflows", layout.hasVisualOverflow)
        }
    }
}
