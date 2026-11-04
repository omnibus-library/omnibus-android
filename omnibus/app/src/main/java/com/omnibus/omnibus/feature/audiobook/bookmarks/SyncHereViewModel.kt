package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.syncpoint.SyncPointRepository
import com.omnibus.omnibus.data.audiobook.syncpoint.SyncPointResult
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Sync here cell's state. [visible] needs this book loaded with an ebook (P-BMK-4). [synced] lasts for the
 * ViewModel's life, which is the player's entry.
 */
data class SyncHereUIState(
    val visible: Boolean = false,
    val online: Boolean = true,
    val synced: Boolean = false,
)

/**
 * Owns the Sync here cell for one book (P-BMK-4, I-BMK-3). A successful declaration turns the label to "Synced ✓" until
 * the player's entry is popped. A failure changes nothing. The VM is scoped to the player's route, so the label survives
 * rotation and the sheet opening.
 *
 * @property bookUuid The book the player is showing.
 */
@HiltViewModel(assistedFactory = SyncHereViewModel.Factory::class)
class SyncHereViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val controller: AudiobookController,
    private val connectivity: ConnectivityMonitor,
    private val syncPointRepository: SyncPointRepository,
) : ViewModel() {

    private val synced = MutableStateFlow(false)
    private var inFlight: Job? = null

    /** The cell's state. It is collected only while the player composes the cell. */
    val uiState: StateFlow<SyncHereUIState> = combine(controller.state, connectivity.isOnline, synced) { state, online, done ->
        uiStateOf(state, online, done)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        uiStateOf(controller.state.value, connectivity.isOnline.value, synced.value),
    )

    /**
     * Declares that the second the player is at matches the reader's place. It is ignored while offline, while a request is
     * in flight, while this book is not loaded or has no ebook, and while its file is still loading. Each request is sent
     * once, with no retry.
     */
    fun onSyncHere() {
        if (inFlight?.isActive == true || !connectivity.isOnline.value) return
        val state = controller.state.value
        val book = state.book ?: return
        if (book.bookUuid != bookUuid || !book.hasEbook || state.isLoading) return
        val fileId = book.bookFileId
        val seconds = state.positionSeconds
        inFlight = viewModelScope.launch {
            if (syncPointRepository.declareAudioSyncPoint(bookUuid, fileId, seconds) is SyncPointResult.Declared) {
                synced.value = true
            }
        }
    }

    private fun uiStateOf(state: PlaybackState, online: Boolean, done: Boolean) = SyncHereUIState(
        visible = state.book?.let { it.bookUuid == bookUuid && it.hasEbook } == true,
        online = online,
        synced = done,
    )

    /** Creates a [SyncHereViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): SyncHereViewModel
    }
}
