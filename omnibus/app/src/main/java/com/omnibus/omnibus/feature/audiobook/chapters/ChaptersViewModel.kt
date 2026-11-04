package com.omnibus.omnibus.feature.audiobook.chapters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Owns the chapters sheet's state, which follows the player, and its one intent: jump to a chapter (P-CHP-5). */
@HiltViewModel
class ChaptersViewModel @Inject constructor(private val controller: AudiobookController) : ViewModel() {

    /** The rows, the current chapter and its progress. The progress moves while playing (P-CHP-3). */
    val uiState: StateFlow<ChaptersUiState> = controller.state
        .map { chaptersUiState(it) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), chaptersUiState(controller.state.value))

    /** Seeks to the start of the chapter at [index] and plays. Does nothing for an index with no chapter (P-CHP-5). */
    fun onChapterChosen(index: Int) {
        val state = controller.state.value
        val chapter = state.chapters.getOrNull(index) ?: return
        controller.seekTo(chapter.startSeconds)
        if (!state.isPlaying) controller.play()
    }
}
