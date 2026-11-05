package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.library.BookAvailability
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.feature.bookdetail.progress.BookReadingProgress
import com.omnibus.omnibus.feature.bookdetail.progress.bookReadingProgress
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the action bar reads: the book's read [status] and its saved [progress], which is null when there is no place
 * yet. [listenAvailable] is false when the device is offline and the audiobook is not downloaded, so Listen is disabled.
 */
data class ActionBarUIState(
    val status: ReadStatusState = ReadStatusState.Unknown,
    val progress: BookReadingProgress? = null,
    val listenAvailable: Boolean = true,
)

/**
 * Owns the action bar's status and progress for one book. The status comes from the shared read status, the progress
 * from [BookProgressRepository]. Each reload reads the progress again and the chapters once until they load. Nothing
 * is read until the first reload, and the status alone can update the bar before the progress arrives.
 *
 * Listen availability is re-checked on every reload and whenever connectivity changes; online, no lookup is made. The
 * immersive read starts the audiobook in the background, as spec P-MINI-6 describes.
 *
 * @property bookUuid The book this bar is for.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ActionBarViewModel.Factory::class)
class ActionBarViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val progressRepository: BookProgressRepository,
    readStatusRepository: ReadStatusRepository,
    connectivity: ConnectivityMonitor,
    private val availability: BookAvailability,
    private val audiobookController: AudiobookController,
) : ViewModel() {

    /** Creates an [ActionBarViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ActionBarViewModel
    }

    /** Counts the reloads, so 0 means nothing has loaded yet. */
    private val reloads = MutableStateFlow(0)
    private val chapters = MutableStateFlow<BookChaptersResult.Success?>(null)

    /** The reload key of the last load, so re-entering composition with the same key does nothing. */
    private var lastLoadedKey: Int? = null
    private var chaptersJob: Job? = null

    private val records: Flow<List<ProgressRecord>> = reloads.filter { it > 0 }
        .flatMapLatest { progressRepository.getBookProgressFlow(bookUuid) }
        .map { result ->
            when (result) {
                is BookProgressResult.Success -> result.records
                is BookProgressResult.Failed -> result.records
            }
        }

    /** Re-checked on every reload, so a download made meanwhile is seen. Online, no lookup is made. */
    private val listenAvailable: Flow<Boolean> = combine(connectivity.isOnline, reloads) { online, _ -> online }
        .mapLatest { online -> online || availability.canOpen(bookUuid, ProgressFormat.Listening) }
        .distinctUntilChanged()

    /** The bar's state. It is kept while something collects it. */
    val uiState: StateFlow<ActionBarUIState> = combine(
        readStatusRepository.getReadStatusFlow(bookUuid),
        records.onStart { emit(emptyList()) },
        chapters,
        listenAvailable,
    ) { status, recs, chs, available ->
        ActionBarUIState(status, bookReadingProgress(recs, chs), listenAvailable = available)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ActionBarUIState(listenAvailable = connectivity.isOnline.value),
    )

    /** Loads for [reloadKey] once. A new key reads the progress again, and the chapters until they load. */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        reloads.update { it + 1 }
        if (chapters.value == null && chaptersJob?.isActive != true) {
            chaptersJob = viewModelScope.launch {
                (progressRepository.getChapters(bookUuid) as? BookChaptersResult.Success)?.let { chapters.value = it }
            }
        }
    }

    /**
     * Starts the audiobook in the background for the immersive read, on [audioFileId] or the server's choice. The
     * controller queues the load until its media session connects, and the reader's mini player shows it (spec P-MINI-6).
     */
    fun onImmersiveRead(audioFileId: Long?) {
        audiobookController.load(bookUuid, audioFileId, positionSeconds = null, autoplay = true)
    }
}
