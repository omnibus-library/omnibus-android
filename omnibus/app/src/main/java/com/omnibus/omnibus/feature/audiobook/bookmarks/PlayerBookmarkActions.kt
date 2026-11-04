package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.audiobook.player.PlayerToolButton

/**
 * The tool row's Bookmark action (P-BMK-1) for the `// #171` block in PlayerScreen. A tap while the book is ready gives a
 * confirm haptic (AND-UI-4) and adds the bookmark. Otherwise it does nothing, and there is no toast.
 */
@Composable
internal fun rememberAddBookmarkAction(
    bookUuid: String,
    viewModel: PlayerBookmarksViewModel = hiltViewModel<PlayerBookmarksViewModel, PlayerBookmarksViewModel.Factory>(
        creationCallback = { it.create(bookUuid) },
    ),
): () -> Unit {
    val haptics = LocalHapticFeedback.current
    val canAdd by viewModel.canAddBookmark.collectAsState()
    val currentCanAdd by rememberUpdatedState(canAdd)
    return remember(viewModel, haptics) {
        {
            if (currentCanAdd) {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                viewModel.onAddBookmark()
            }
        }
    }
}

/**
 * The Sync here cell (P-BMK-4, P-A11Y-5) for the `// #171` block in PlayerScreen, or null while it is hidden.
 */
@Composable
internal fun rememberSyncHereButton(
    bookUuid: String,
    viewModel: SyncHereViewModel = hiltViewModel<SyncHereViewModel, SyncHereViewModel.Factory>(
        creationCallback = { it.create(bookUuid) },
    ),
): PlayerToolButton? {
    val state by viewModel.uiState.collectAsState()
    return syncHereButton(
        state = state,
        syncHereLabel = stringResource(R.string.audiobook_bookmarks_sync_here),
        syncedLabel = stringResource(R.string.audiobook_bookmarks_synced),
        needsConnection = stringResource(R.string.audiobook_bookmarks_needs_connection),
        onClick = viewModel::onSyncHere,
    )
}
