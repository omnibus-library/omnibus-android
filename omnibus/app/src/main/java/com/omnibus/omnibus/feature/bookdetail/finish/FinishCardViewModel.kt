package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.highlights.HighlightsRepository
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.reviews.ReviewsRepository
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the card knows. [sessions] is null until a read succeeds; [finishedMembers] holds the series members known to
 * be finished.
 */
data class FinishCardUIState(
    val status: ReadStatusState = ReadStatusState.Unknown,
    val sessions: List<ReadingSession>? = null,
    val highlightCount: Int = 0,
    val reviews: ReviewsState = ReviewsState.Loading,
    val finishedMembers: Set<String> = emptySet(),
) {
    /** Whether the book is known to be Finished. */
    val finished: Boolean get() = status.isFinished()

    /** When the book was finished, or null. */
    val finishedAtMillis: Long? get() = (status as? ReadStatusState.Known)?.record?.finishedAtMillis

    /** Whether the row may point at the next book, so the series members are worth reading. */
    val wantsSeries: Boolean get() = reviews.isRated()
}

/** How many times the card has risen and the burst has been thrown on this page. 0 means never. */
data class FinishCelebration(val rises: Int = 0, val bursts: Int = 0)

/**
 * Owns the finish card for one book. It collects the read status for its whole life, so a celebration is counted
 * even while nothing shows it, and reads the sessions, highlights, reviews and series statuses only while the book is
 * Finished. The celebration counters and the play latches keep each rise and burst to one play per number.
 *
 * @property bookUuid The book this card shows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = FinishCardViewModel.Factory::class)
class FinishCardViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val readStatusRepository: ReadStatusRepository,
    private val progressRepository: BookProgressRepository,
    reviewsRepository: ReviewsRepository,
    private val highlightsRepository: HighlightsRepository,
) : ViewModel() {

    /** Creates a [FinishCardViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        /** The ViewModel for the book with [bookUuid]. */
        fun create(bookUuid: String): FinishCardViewModel
    }

    /** The latest read status. */
    private val status = MutableStateFlow<ReadStatusState>(ReadStatusState.Unknown)

    /** The sessions of the last successful read, or null before one. */
    private val sessions = MutableStateFlow<List<ReadingSession>?>(null)

    /** The series members known to be Finished. */
    private val finishedMembers = MutableStateFlow<Set<String>>(emptySet())

    /** The reload key the sessions were last asked for, or null before [reload]. */
    private val reloadKey = MutableStateFlow<Int?>(null)

    /** The backing store of [celebration]. */
    private val _celebration = MutableStateFlow(FinishCelebration())

    /** The celebration counters, shared by the card and the cover overlay. */
    val celebration: StateFlow<FinishCelebration> = _celebration.asStateFlow()

    /** Plays each rise once. */
    val riseLatch = PlayLatch()

    /** Plays each burst once. */
    val burstLatch = PlayLatch()

    /** The reload key the sessions were last read for, or null. */
    private var sessionsKey: Int? = null

    /** The member list and reload key of the last series status read, so the same request is not made twice. */
    private var seriesRequest: Pair<Int, List<String>>? = null

    /** The series status read in flight, which a newer request cancels. */
    private var seriesJob: Job? = null

    /** The card's state. Sessions, highlights and reviews are only read while the book is Finished. */
    val uiState: StateFlow<FinishCardUIState> = status.flatMapLatest { current ->
        if (!current.isFinished()) {
            flowOf(FinishCardUIState(status = current))
        } else {
            combine(
                sessions,
                highlightsRepository.getHighlightsFlow(bookUuid),
                reviewsRepository.getReviewsFlow(bookUuid),
                finishedMembers,
            ) { sess, highlights, reviews, members ->
                FinishCardUIState(
                    status = current,
                    sessions = sess,
                    highlightCount = (highlights as? HighlightsState.Loaded)?.highlights?.size ?: 0,
                    reviews = reviews,
                    finishedMembers = members,
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FinishCardUIState())

    init {
        viewModelScope.launch {
            var previous: ReadStatusState? = null
            readStatusRepository.getReadStatusFlow(bookUuid).collect { next ->
                if (celebratesOnChange(previous, next)) {
                    _celebration.update { FinishCelebration(rises = it.rises + 1, bursts = it.bursts + 1) }
                }
                previous = next
                status.value = next
            }
        }
        viewModelScope.launch {
            combine(reloadKey.filterNotNull(), status.map { it.isFinished() }.distinctUntilChanged()) { key, finished ->
                key to finished
            }.collectLatest { (key, finished) -> if (finished) loadSessions(key) }
        }
    }

    /** Loads for [reloadKey]: the sessions are read again while the book is Finished. */
    fun reload(reloadKey: Int) {
        this.reloadKey.value = reloadKey
    }

    /** Throws the burst again, only once a celebration has happened. */
    fun replayBurst() {
        _celebration.update { if (it.bursts == 0) it else it.copy(bursts = it.bursts + 1) }
    }

    /**
     * Reads every member's status except this book's, in parallel, once per [reloadKey] and member list, and keeps the
     * finished ones in one update. A failed read counts as unfinished.
     */
    fun loadSeriesStatuses(members: List<SeriesMember>, reloadKey: Int) {
        val others = members.map { it.uuid }.filter { it != bookUuid }
        val request = reloadKey to others
        if (request == seriesRequest) return
        seriesRequest = request
        seriesJob?.cancel()
        seriesJob = viewModelScope.launch {
            val answers = others.map { uuid -> async { uuid to readStatusRepository.fetchStatus(uuid) } }.awaitAll()
            finishedMembers.value = answers.filter { (_, state) -> state.isFinished() }.map { it.first }.toSet()
        }
    }

    /** Reads the sessions for [key], and refreshes the highlights when an earlier key already loaded them. */
    private suspend fun loadSessions(key: Int) {
        val refreshShared = sessionsKey != null && sessionsKey != key
        sessionsKey = key
        if (refreshShared) viewModelScope.launch { highlightsRepository.refresh(bookUuid) }
        when (val result = progressRepository.getSessions(bookUuid)) {
            is ReadingSessionsResult.Success -> sessions.value = result.sessions
            is ReadingSessionsResult.Failed -> Unit
        }
    }
}
