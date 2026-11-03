package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.merge.MergeBooksResult
import com.omnibus.omnibus.data.merge.MergeCandidatesResult
import com.omnibus.omnibus.data.merge.MergeError
import com.omnibus.omnibus.data.merge.MergeRepository
import com.omnibus.omnibus.data.merge.UndoMergeResult
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Merge with… state for one book: the admin gate, the sheet and its two steps, the debounced search, the merge, and
 * the undo handles of this page's merges with their one-shot outcomes.
 *
 * @property isAdmin Whether the signed-in user is an admin. Null while the user is unknown, which hides the menu row.
 * @property isOnline Whether the device is online. Offline, the row is disabled and no request is sent.
 * @property sheetOpen Whether the sheet is shown.
 * @property step The sheet's step: the search, or the confirmation of one candidate.
 * @property query The search text as typed. Its trimmed form is searched.
 * @property candidates The results for the latest search, without this book.
 * @property searching True while a search is debouncing or in flight.
 * @property searchFailed True when the latest search failed. Editing the query or retrying clears it.
 * @property merging True while a merge request is in flight.
 * @property mergeError Why the last merge failed, or null.
 * @property outcome The latest finished merge or undo, for the page to report once. Cleared by
 * [MergeViewModel.outcomeShown].
 */
data class MergeUIState(
    val isAdmin: Boolean? = null,
    val isOnline: Boolean = true,
    val sheetOpen: Boolean = false,
    val step: MergeStep = MergeStep.Search,
    val query: String = "",
    val candidates: List<Book> = emptyList(),
    val searching: Boolean = false,
    val searchFailed: Boolean = false,
    val merging: Boolean = false,
    val mergeError: MergeFailure? = null,
    val outcome: MergeOutcome? = null,
)

/** The sheet's two steps. */
sealed interface MergeStep {

    /** Searching for the other copy. */
    data object Search : MergeStep

    /** Confirming that [candidate] merges into this book. */
    data class Confirm(val candidate: Book) : MergeStep
}

/** Why a merge did not happen. The UI layer maps each to its copy. */
enum class MergeFailure {
    /** One of the two books no longer exists. */
    Gone,

    /** Anything else. */
    Generic,
}

/**
 * A finished merge or undo, reported once. [eventId] increases with each outcome, so the page reports each one once.
 */
sealed interface MergeOutcome {
    val eventId: Long

    /** The merge succeeded. [mergeLogId] is the handle for undoing it. */
    data class Merged(override val eventId: Long, val mergeLogId: Long) : MergeOutcome

    /** The merge identified by [mergeLogId] was undone. */
    data class Undone(override val eventId: Long, val mergeLogId: Long) : MergeOutcome

    /**
     * The undo of [mergeLogId] failed. When [retryable] is false the handle is released and cannot be undone any more.
     */
    data class UndoFailed(override val eventId: Long, val mergeLogId: Long, val retryable: Boolean) : MergeOutcome
}

/**
 * Owns the Merge with… sheet for the book [bookUuid] (#120). The state lives in the Book Detail nav entry's store, so it
 * survives the sheet closing, a silent reload and rotation, and the undo handles end when the page is left. Writes run
 * in [viewModelScope], so closing the sheet does not cancel a merge.
 *
 * @property bookUuid The target: the book on the page. It always keeps its metadata.
 */
@HiltViewModel(assistedFactory = MergeViewModel.Factory::class)
class MergeViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val repository: MergeRepository,
    authRepository: AuthRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MergeUIState())

    /** The menu and sheet state. It changes only through the intents below and the reads and writes they start. */
    val uiState: StateFlow<MergeUIState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /** Bumped whenever a search is cancelled or scheduled, so a response for an older search is dropped. */
    private var searchToken = 0L
    private var nextEventId = 1L

    /** Undo handles this page may still use, mapped to whether an undo for each is in flight. */
    private val held = mutableMapOf<Long, Boolean>()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user -> _uiState.update { it.copy(isAdmin = user?.isAdmin) } }
        }
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online ->
                val wasOnline = _uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                val state = _uiState.value
                val liveSearch = state.sheetOpen && state.step is MergeStep.Search && !state.merging &&
                    state.query.isNotBlank()
                if (!liveSearch || online == wasOnline) return@collect
                if (online) {
                    schedule(debounce = true)
                } else {
                    cancelSearch()
                    _uiState.update { it.copy(searching = false) }
                }
            }
        }
    }

    /** Opens the sheet on the search step with nothing carried over. Ignored unless the user is an admin and online. */
    fun openSheet() {
        val state = _uiState.value
        if (state.isAdmin != true || !state.isOnline) return
        cancelSearch()
        _uiState.update {
            it.copy(
                sheetOpen = true,
                step = MergeStep.Search,
                query = "",
                candidates = emptyList(),
                searching = false,
                searchFailed = false,
                merging = false,
                mergeError = null,
            )
        }
    }

    /** Closes the sheet and stops its search. Ignored while a merge is in flight. */
    fun closeSheet() {
        if (_uiState.value.merging) return
        cancelSearch()
        _uiState.update { it.copy(sheetOpen = false, searching = false) }
    }

    /** Sets the search text and searches it after the debounce. Ignored while merging, off the search step, or over the cap. */
    fun onQueryChange(query: String) {
        val state = _uiState.value
        if (state.merging || state.step !is MergeStep.Search || !acceptsQuery(query)) return
        _uiState.update { it.copy(query = query, searchFailed = false) }
        schedule(debounce = true)
    }

    /** Searches the current query again at once, after a failed search. */
    fun retrySearch() {
        val state = _uiState.value
        if (state.merging || state.step !is MergeStep.Search) return
        _uiState.update { it.copy(searchFailed = false) }
        schedule(debounce = false)
    }

    /** Moves to the confirmation of [candidate]. */
    fun choose(candidate: Book) {
        val state = _uiState.value
        if (state.merging || state.step !is MergeStep.Search) return
        _uiState.update { it.copy(step = MergeStep.Confirm(candidate), mergeError = null) }
    }

    /** Returns from the confirmation to the search, which keeps its query and results. Ignored while merging. */
    fun back() {
        val state = _uiState.value
        if (state.merging || state.step !is MergeStep.Confirm) return
        _uiState.update { it.copy(step = MergeStep.Search, mergeError = null) }
    }

    /** Merges the chosen candidate into this book. Ignored while merging, offline, or off the confirmation step. */
    fun confirmMerge() {
        val state = _uiState.value
        val step = state.step as? MergeStep.Confirm ?: return
        if (state.merging || !state.isOnline) return
        _uiState.update { it.copy(merging = true, mergeError = null) }

        viewModelScope.launch {
            when (val result = repository.mergeBooks(step.candidate.uuid, bookUuid)) {
                is MergeBooksResult.Success -> {
                    val mergeLogId = result.receipt.mergeLogId
                    held[mergeLogId] = false
                    _uiState.update {
                        it.copy(
                            sheetOpen = false,
                            merging = false,
                            step = MergeStep.Search,
                            outcome = MergeOutcome.Merged(nextEventId++, mergeLogId),
                        )
                    }
                }
                is MergeBooksResult.Failure -> {
                    val failure = if (result.error == MergeError.NotFound) MergeFailure.Gone else MergeFailure.Generic
                    _uiState.update { it.copy(merging = false, mergeError = failure) }
                }
                MergeBooksResult.NoServer -> _uiState.update { it.copy(merging = false, mergeError = MergeFailure.Generic) }
            }
        }
    }

    /**
     * Reverses the merge [mergeLogId]. A handle this page does not hold, or one already released, sends nothing. A second
     * call while an undo of the same handle is in flight is ignored.
     */
    fun undo(mergeLogId: Long) {
        val inFlight = held[mergeLogId] ?: return
        if (inFlight) return
        held[mergeLogId] = true

        viewModelScope.launch {
            val result = repository.undoMerge(mergeLogId)
            val outcome = when {
                result is UndoMergeResult.Success -> {
                    held.remove(mergeLogId)
                    MergeOutcome.Undone(nextEventId++, mergeLogId)
                }
                result is UndoMergeResult.Failure && undoFailureIsTerminal(result.error) -> {
                    held.remove(mergeLogId)
                    MergeOutcome.UndoFailed(nextEventId++, mergeLogId, retryable = false)
                }
                else -> {
                    held[mergeLogId] = false
                    MergeOutcome.UndoFailed(nextEventId++, mergeLogId, retryable = true)
                }
            }
            _uiState.update { it.copy(outcome = outcome) }
        }
    }

    /** Clears [MergeUIState.outcome] if it is the outcome [eventId]. A newer outcome stays. */
    fun outcomeShown(eventId: Long) {
        _uiState.update { if (it.outcome?.eventId == eventId) it.copy(outcome = null) else it }
    }

    /** Cancels the pending search and makes any response still on its way stale. */
    private fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        searchToken++
    }

    /**
     * Starts a search for the current query, cancelling any other. A blank query, or an offline one, clears the candidates
     * and sends nothing.
     *
     * @param debounce Whether to wait [MERGE_SEARCH_DEBOUNCE_MILLIS] before sending.
     */
    private fun schedule(debounce: Boolean) {
        cancelSearch()
        val token = searchToken
        val state = _uiState.value
        val trimmed = state.query.trim()
        if (trimmed.isEmpty() || !state.isOnline) {
            _uiState.update { it.copy(candidates = emptyList(), searching = false) }
            return
        }

        _uiState.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            if (debounce) delay(MERGE_SEARCH_DEBOUNCE_MILLIS)
            val result = repository.searchCandidates(trimmed)
            if (token != searchToken) return@launch
            _uiState.update {
                when (result) {
                    is MergeCandidatesResult.Success -> it.copy(
                        searching = false,
                        searchFailed = false,
                        candidates = mergeCandidates(result.books, bookUuid),
                    )
                    else -> it.copy(searching = false, searchFailed = true, candidates = emptyList())
                }
            }
        }
    }

    /** Creates a [MergeViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): MergeViewModel
    }
}
