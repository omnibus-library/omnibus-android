package com.omnibus.omnibus.feature.bookdetail.overview.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.bookprogress.BookProgressResult
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
import com.omnibus.omnibus.di.TimeSource
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
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the card shows. [nowMillis] is the clock the dates are read against.
 */
data class ReadingCardUIState(
    val card: ReadingCardModel = ReadingCardModel(line = null, rows = emptyList()),
    val nowMillis: Long = 0L,
)

/**
 * Owns the Your reading card for one book. The status comes from the shared read status, the sessions are read once per
 * reload key, and the fraction and saved position come from the progress flow, which is collected again on every reload.
 * A later failed read keeps what loaded, so the card does not drop back to its hold state.
 *
 * @property bookUuid The book this card shows.
 * @property hasFile Whether the book has an ebook or an audiobook, which decides the empty-state line.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ReadingCardViewModel.Factory::class)
class ReadingCardViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    @Assisted private val hasFile: Boolean,
    private val progressRepository: BookProgressRepository,
    readStatusRepository: ReadStatusRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    /** Creates a [ReadingCardViewModel] for the book with [bookUuid] and [hasFile]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String, hasFile: Boolean): ReadingCardViewModel
    }

    /** Counts the reloads, so 0 means nothing has loaded yet. */
    private val reloads = MutableStateFlow(0)
    private val sessions = MutableStateFlow<SessionsState>(SessionsState.Loading)

    /** The reload key of the last load, so re-entering composition with the same key does nothing. */
    private var lastLoadedKey: Int? = null
    private var sessionsJob: Job? = null

    /** The fraction and saved position, emitted as null first so the card never waits for progress. */
    private val progress: Flow<CardProgress?> = reloads.filter { it > 0 }
        .flatMapLatest { progressRepository.getBookProgressFlow(bookUuid) }
        .map<BookProgressResult, CardProgress?> { result ->
            val records = when (result) {
                is BookProgressResult.Success -> result.records
                is BookProgressResult.Failed -> result.records
            }
            bookReadingProgress(records, null).let { CardProgress(it?.fraction, hasPosition = it != null) }
        }
        .onStart { emit(null) }

    /** The card's state. It is kept while something collects it. */
    val uiState: StateFlow<ReadingCardUIState> = combine(
        readStatusRepository.getReadStatusFlow(bookUuid),
        sessions,
        progress,
    ) { status, sess, prog ->
        ReadingCardUIState(
            card = readingCardModel(wishlistOnly = false, hasFile = hasFile, status = status, sessions = sess, progress = prog),
            nowMillis = timeSource.nowMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadingCardUIState())

    /**
     * Loads for [reloadKey] once. A new key reads the sessions again and the progress is collected again. A read in
     * flight is cancelled by a newer key, and the sessions already loaded stay on a failed read.
     */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        reloads.update { it + 1 }
        sessionsJob?.cancel()
        sessionsJob = viewModelScope.launch {
            when (val result = progressRepository.getSessions(bookUuid)) {
                is ReadingSessionsResult.Success -> sessions.value = SessionsState.Loaded(result.sessions)
                is ReadingSessionsResult.Failed -> if (sessions.value !is SessionsState.Loaded) {
                    sessions.value = SessionsState.Failed
                }
            }
        }
    }
}
