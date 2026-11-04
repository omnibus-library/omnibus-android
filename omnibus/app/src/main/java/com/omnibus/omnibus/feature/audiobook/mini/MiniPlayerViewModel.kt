package com.omnibus.omnibus.feature.audiobook.mini

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.currentChapter
import com.omnibus.omnibus.ui.components.CoverIdentity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the mini player shows. Every field is at its default while [visible] is false, so a stale title never shows.
 *
 * @property visible Whether the bar shows: a book is loaded and it is not the one being stopped.
 * @property bookUuid The loaded book, for the body's tap to open the player.
 * @property cover The cover to draw, from the playing book.
 * @property title The book's title, empty while a load is in flight.
 * @property subtitle The current chapter's title, else the author, else null.
 * @property progress The whole book's position as a fraction in [0, 1]. Zero while the duration is unknown.
 * @property isPlaying Whether the play/pause button shows pause.
 */
data class MiniPlayerUIState(
    val visible: Boolean = false,
    val bookUuid: String = "",
    val cover: CoverIdentity = CoverIdentity(uuid = "", title = "", hasCover = false),
    val title: String = "",
    val subtitle: String? = null,
    val progress: Float = 0f,
    val isPlaying: Boolean = false,
)

/**
 * Maps the controller's [playback] state to what the mini player shows. [stopping] is the book the ✕ has just stopped:
 * while the controller still reports that same [PlayingBook], the bar stays hidden.
 */
internal fun miniPlayerUIState(playback: PlaybackState, stopping: PlayingBook?): MiniPlayerUIState {
    val book = playback.book
    if (book == null || book == stopping) return MiniPlayerUIState()
    val duration = playback.durationSeconds
    val position = playback.positionSeconds
    val progress = if (duration.isFinite() && duration > 0 && position.isFinite()) {
        (position / duration).coerceIn(0.0, 1.0).toFloat()
    } else {
        0f
    }
    return MiniPlayerUIState(
        visible = true,
        bookUuid = book.bookUuid,
        cover = CoverIdentity(uuid = book.bookUuid, title = book.title, creator = book.author, hasCover = true),
        title = book.title,
        subtitle = playback.currentChapter?.title?.takeIf { it.isNotBlank() }
            ?: book.author?.takeIf { it.isNotBlank() },
        progress = progress,
        isPlaying = playback.isPlaying,
    )
}

/**
 * The mini player's state, shared by the shell bar and the reader dock (one instance per host entry). It shows whenever
 * the controller holds a book, so a load with autoplay (Immersive read, P-MINI-6) shows the dock before any player
 * screen opens.
 */
@HiltViewModel
class MiniPlayerViewModel @Inject constructor(private val controller: AudiobookController) : ViewModel() {
    private var stopping: PlayingBook? = null
    private val _uiState = MutableStateFlow(miniPlayerUIState(controller.state.value, null))

    /** What the bar shows. */
    val uiState: StateFlow<MiniPlayerUIState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state.collect { state ->
                if (stopping != null && state.book != stopping) stopping = null
                _uiState.value = miniPlayerUIState(state, stopping)
            }
        }
    }

    /** Goes back 15 seconds. */
    fun onSkipBack() = controller.skipBack()

    /** Plays when paused, pauses when playing. */
    fun onPlayPauseToggled() = controller.togglePlayPause()

    /** ✕: hides the bar at once, then stops. The controller saves the place in its unload listeners. */
    fun onStop() {
        val book = controller.state.value.book ?: return
        stopping = book
        _uiState.value = miniPlayerUIState(controller.state.value, stopping)
        controller.stop()
    }
}
