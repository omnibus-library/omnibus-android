package com.omnibus.omnibus.feature.bookdetail.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.library.BookAvailability
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the progress section shows. [showOfflineNote] is true only while the status is Reading, the device is offline and
 * the book has no downloaded copy.
 */
data class ReadingProgressUIState(
    val display: ProgressDisplay = ProgressDisplay.Hidden,
    val showOfflineNote: Boolean = false,
)

/**
 * Owns the progress section for one book. Progress and chapters come from [BookProgressRepository], the status from the
 * shared read status, and the offline note from connectivity and downloads. Each reload reads the progress again, reads
 * the chapters once until they load, and reads the sessions every time.
 *
 * @property bookUuid The book this section shows.
 * @property hasEbook Whether the book has an ebook, which decides the bar's segments when the reader is on an ebook.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ReadingProgressViewModel.Factory::class)
class ReadingProgressViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    @Assisted private val hasEbook: Boolean,
    private val progressRepository: BookProgressRepository,
    readStatusRepository: ReadStatusRepository,
    connectivity: ConnectivityMonitor,
    private val availability: BookAvailability,
) : ViewModel() {

    /** Creates a [ReadingProgressViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String, hasEbook: Boolean): ReadingProgressViewModel
    }

    /** Counts the reloads, so 0 means nothing has loaded yet. */
    private val reloads = MutableStateFlow(0)
    private val chapters = MutableStateFlow<BookChaptersResult.Success?>(null)
    private val sessions = MutableStateFlow<List<ReadingSession>?>(null)

    /** The reload key of the last load, so re-entering composition with the same key does nothing. */
    private var lastLoadedKey: Int? = null
    private var chaptersJob: Job? = null
    private var sessionsJob: Job? = null

    private val records: Flow<List<ProgressRecord>> = reloads.filter { it > 0 }
        .flatMapLatest { progressRepository.getBookProgressFlow(bookUuid) }
        .map { result ->
            when (result) {
                is BookProgressResult.Success -> result.records
                is BookProgressResult.Failed -> result.records
            }
        }

    /** Re-checked on every reload, so a download made meanwhile is seen. Online, no lookup is made. */
    private val offlineNotDownloaded: Flow<Boolean> = combine(connectivity.isOnline, reloads) { online, _ -> online }
        .mapLatest { online -> !online && !availability.canOpen(bookUuid, ProgressFormat.Reading) }

    /** The section's state. It is kept while something collects it. */
    val uiState: StateFlow<ReadingProgressUIState> = combine(
        readStatusRepository.getReadStatusFlow(bookUuid),
        records,
        chapters,
        sessions,
        offlineNotDownloaded,
    ) { status, recs, chs, sess, offline ->
        ReadingProgressUIState(
            display = progressDisplay(status, hasEbook, recs, chs, sess),
            showOfflineNote = showsOfflineNote(status, offline),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingProgressUIState())

    /** Loads for [reloadKey] once. A new key reads the progress and the sessions again. */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        reloads.update { it + 1 }
        if (chapters.value == null && chaptersJob?.isActive != true) {
            chaptersJob = viewModelScope.launch {
                (progressRepository.getChapters(bookUuid) as? BookChaptersResult.Success)?.let { chapters.value = it }
            }
        }
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            (progressRepository.getSessions(bookUuid) as? ReadingSessionsResult.Success)?.let { sessions.value = it.sessions }
        }
    }
}
