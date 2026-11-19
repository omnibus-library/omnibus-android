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

/** Where the latest Sync here tap stands (P-BMK-4). */
enum class SyncHereStatus {
    /** No tap yet in this player entry. */
    Idle,
    /** A request is out; taps are ignored until it answers. */
    Syncing,
    /** The server accepted the latest tap. */
    Synced,
    /** The server did not accept the latest tap, for any reason. */
    Failed,
}

/**
 * The Sync here cell's state. [visible] needs this book loaded with an ebook (P-BMK-4). [status] lasts for the
 * ViewModel's life, which is the player's entry. [announceFailure] is true from a failure until the UI calls
 * [SyncHereViewModel.onFailureAnnounced].
 */
data class SyncHereUIState(
    val visible: Boolean = false,
    val online: Boolean = true,
    val status: SyncHereStatus = SyncHereStatus.Idle,
    val announceFailure: Boolean = false,
)

/**
 * Owns the Sync here cell for one book (P-BMK-4, I-BMK-3). A tap reads Syncing until the server answers, then Synced or
 * Failed; the latest tap's answer is shown. A failure asks the UI for one screen-reader announcement. The VM is scoped to
 * the player's route, so the label survives rotation and the sheet opening.
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

    private val status = MutableStateFlow(SyncHereStatus.Idle)
    private val announceFailure = MutableStateFlow(false)
    private var inFlight: Job? = null

    /** The cell's state. It is collected only while the player composes the cell. */
    val uiState: StateFlow<SyncHereUIState> = combine(controller.state, connectivity.isOnline, status, announceFailure) { state, online, current, announce ->
        uiStateOf(state, online, current, announce)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        uiStateOf(controller.state.value, connectivity.isOnline.value, status.value, announceFailure.value),
    )

    /**
     * Declares that the second the player is at matches the reader's place. It is ignored while offline, while a request is
     * in flight (Syncing), while this book is not loaded or has no ebook, and while its file is still loading. It is accepted
     * from Idle, Synced and Failed alike. Each request is sent once, with no retry.
     */
    fun onSyncHere() {
        if (status.value == SyncHereStatus.Syncing || inFlight?.isActive == true || !connectivity.isOnline.value) return
        val state = controller.state.value
        val book = state.book ?: return
        if (book.bookUuid != bookUuid || !book.hasEbook || state.isLoading) return
        val fileId = book.bookFileId
        val seconds = state.positionSeconds
        status.value = SyncHereStatus.Syncing
        inFlight = viewModelScope.launch {
            when (syncPointRepository.declareAudioSyncPoint(bookUuid, fileId, seconds)) {
                SyncPointResult.Declared -> status.value = SyncHereStatus.Synced
                is SyncPointResult.Failed -> {
                    status.value = SyncHereStatus.Failed
                    announceFailure.value = true
                }
            }
        }
    }

    /** Called once the UI has announced "Sync failed", so it is not announced again. */
    fun onFailureAnnounced() {
        announceFailure.value = false
    }

    private fun uiStateOf(state: PlaybackState, online: Boolean, current: SyncHereStatus, announce: Boolean) = SyncHereUIState(
        visible = state.book?.let { it.bookUuid == bookUuid && it.hasEbook } == true,
        online = online,
        status = current,
        announceFailure = announce,
    )

    /** Creates a [SyncHereViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): SyncHereViewModel
    }
}
