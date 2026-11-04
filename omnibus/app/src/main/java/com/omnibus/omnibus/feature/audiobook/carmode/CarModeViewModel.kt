package com.omnibus.omnibus.feature.audiobook.carmode

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.bookSecondsLeftAtRate
import com.omnibus.omnibus.data.audiobook.playback.currentChapter
import com.omnibus.omnibus.data.audiobook.playback.nextChapterTarget
import com.omnibus.omnibus.data.audiobook.playback.previousChapterTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What car mode shows. Plain values only, so the content composable and its tests build it by hand.
 *
 * @property title The current chapter's title, else the book's title. Empty while nothing is loaded.
 * @property bookSecondsLeftAtRate The whole book's time left divided by the speed, in seconds (P-CAR-2).
 * @property isPlaying Whether the big tile shows pause.
 * @property canPreviousChapter Whether previous chapter has somewhere to go. False without chapters.
 * @property canNextChapter Whether next chapter has somewhere to go. False without chapters.
 */
data class CarModeUIState(
    val title: String = "",
    val bookSecondsLeftAtRate: Double = 0.0,
    val isPlaying: Boolean = false,
    val canPreviousChapter: Boolean = false,
    val canNextChapter: Boolean = false,
)

/**
 * Maps the controller's [playback] state to what car mode shows. The chapter rules are the player's own (P-CAR-4), so
 * each tile is enabled exactly when its counterpart on the player is.
 */
internal fun carModeUIState(playback: PlaybackState): CarModeUIState {
    val book = playback.book ?: return CarModeUIState()
    return CarModeUIState(
        title = playback.currentChapter?.title?.takeIf { it.isNotBlank() } ?: book.title,
        bookSecondsLeftAtRate = playback.bookSecondsLeftAtRate,
        isPlaying = playback.isPlaying,
        canPreviousChapter = previousChapterTarget(playback.chapters, playback.positionSeconds) != null,
        canNextChapter = nextChapterTarget(playback.chapters, playback.positionSeconds) != null,
    )
}

/**
 * Car mode's state and commands. Car mode only reads the controller and sends the transport commands the player sends;
 * it has no speed or sleep controls, and it never loads a book.
 */
@HiltViewModel
class CarModeViewModel @Inject constructor(private val controller: AudiobookController) : ViewModel() {

    /** What car mode shows, recomputed on every controller change. */
    val uiState: StateFlow<CarModeUIState> = controller.state
        .map(::carModeUIState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, carModeUIState(controller.state.value))

    /** Skips back 15 seconds. */
    fun onSkipBack() = controller.skipBack()

    /** Skips forward 30 seconds. */
    fun onSkipForward() = controller.skipForward()

    /** Plays when paused, pauses when playing. */
    fun onPlayPauseToggled() = controller.togglePlayPause()

    /** Goes to the previous chapter, or restarts the current one. */
    fun onPreviousChapter() = controller.previousChapter()

    /** Goes to the next chapter. */
    fun onNextChapter() = controller.nextChapter()
}
