package com.omnibus.omnibus.feature.audiobook.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.feature.audiobook.player.offers.PlayerBannerSlot

/**
 * The audiobook player (C10): the root-route screen for one open of the player. It is the host every later player ticket
 * mounts into. Its controls, overlays and banner come from [PlayerActions], the `when (overlay)` block and the banner
 * slot.
 *
 * Mount points, for the tickets that add to this file:
 * - One `// #<ticket>` block per later ticket. Each block is separated from the next by an unchanged blank line, so
 *   parallel edits merge.
 * - A ticket edits only the lines inside its own blocks, and adds its imports (keep the union on conflict).
 * - Expressions that need a ViewModel call a composable inline, since there is no setup block.
 * - In scope for those expressions: `bookUuid`, `uiState`, `onOpenReader`, `overlay`, `dismissOverlay`.
 * - The controls and the overlay state stay here. Layout lives in [PlayerScreenContent].
 *
 * @param bookUuid The book the player opens.
 * @param bookFileId The file to open, or null for the saved one.
 * @param positionSeconds The position to start at, or null to resume.
 * @param onClose Collapses the player. Playback keeps going.
 * @param onOpenReader Opens the reader for a book, from a ticket's control. Unused in #165.
 * @param viewModel The player's state. Hilt builds it per route; tests may pass their own.
 */
@Composable
fun PlayerScreen(
    bookUuid: String,
    bookFileId: Long?,
    positionSeconds: Double?,
    onClose: () -> Unit,
    onOpenReader: (bookUuid: String, startCfi: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel<PlayerViewModel, PlayerViewModel.Factory>(
        creationCallback = { it.create(bookUuid, bookFileId, positionSeconds) },
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    var overlay by rememberSaveable { mutableStateOf<PlayerOverlay?>(null) }
    val dismissOverlay: () -> Unit = { overlay = null }

    // Each ticket replaces the null / Unit in its own block only.
    val actions = PlayerActions(
        // #167
        onOpenChapters = null,

        // #168
        onOpenSpeed = null,

        // #169
        onOpenSleep = null,
        sleepPill = null,

        // #170
        onOpenCarMode = null,

        // #171
        onAddBookmark = null,
        onOpenBookmarks = null,
        syncHere = null,

        // #172
        onOpenBook = null,
    )

    Box(modifier.fillMaxSize()) {
        PlayerScreenContent(
            uiState = uiState,
            actions = actions,
            onClose = onClose,
            onPlayPauseToggled = viewModel::onPlayPauseToggled,
            onSkipBack = viewModel::onSkipBack,
            onSkipForward = viewModel::onSkipForward,
            onPreviousChapter = viewModel::onPreviousChapter,
            onNextChapter = viewModel::onNextChapter,
            onSeek = viewModel::onSeek,
            banner = {
                // #166
                PlayerBannerSlot(bookUuid = bookUuid, error = uiState.error, onRetry = viewModel::onRetry)

            },
        )

        BackHandler(enabled = overlay != null, onBack = dismissOverlay)

        when (overlay) {
            // #167
            PlayerOverlay.Chapters -> Unit

            // #168
            PlayerOverlay.Speed -> Unit

            // #169
            PlayerOverlay.Sleep -> Unit

            // #170
            PlayerOverlay.CarMode -> Unit

            // #171
            PlayerOverlay.Bookmarks -> Unit

            null -> Unit
        }
    }
}
