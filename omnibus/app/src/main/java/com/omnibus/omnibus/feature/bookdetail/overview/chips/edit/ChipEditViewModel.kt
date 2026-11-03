package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.MetadataRepository
import com.omnibus.omnibus.data.metadata.MetadataWriteResult
import com.omnibus.omnibus.data.metadata.VocabularyEntry
import com.omnibus.omnibus.data.metadata.VocabularyResult
import com.omnibus.omnibus.data.metadata.valuesOf
import com.omnibus.omnibus.data.metadata.withValues
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.feature.bookdetail.overview.chips.chipValues
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The library's values for one kind, once they are loaded for the sheet. */
sealed interface PoolState {

    data object Loading : PoolState

    /** The deduplicated, sorted values. */
    data class Loaded(val entries: List<VocabularyEntry>) : PoolState

    /** The values could not load. The sheet still adds new values. */
    data object Failed : PoolState
}

/**
 * A save that did not stick. The list of [kind] is back at the last confirmed list.
 *
 * @property eventId Increases with each failure, so the page reports each one once.
 */
data class ChipEditFailure(val eventId: Long, val kind: MetadataListKind)

/**
 * The server's merged record after the newest save for a kind, for the page to adopt once.
 *
 * @property eventId Increases with each update, so the page adopts each one once.
 */
data class ChipEditBookUpdate(val eventId: Long, val book: Book)

/**
 * The chip editor's state for one book.
 *
 * @property canEdit Whether the signed-in user may edit chips.
 * @property isOnline Whether the device is online. Offline, nothing can change.
 * @property bound False until the first bind, when the section still shows the book's own lists.
 * @property genres The displayed genres, including optimistic changes.
 * @property tags The displayed tags, including optimistic changes.
 * @property openKind The list the sheet is open for, or null when it is closed.
 * @property entry The text in the sheet's field.
 * @property pools The library's values per kind.
 */
data class ChipEditUIState(
    val canEdit: Boolean = false,
    val isOnline: Boolean = true,
    val bound: Boolean = false,
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val openKind: MetadataListKind? = null,
    val entry: String = "",
    val pools: Map<MetadataListKind, PoolState> = emptyMap(),
    val failure: ChipEditFailure? = null,
    val bookUpdate: ChipEditBookUpdate? = null,
) {
    /** The displayed list for [kind]. */
    fun valuesOf(kind: MetadataListKind): List<String> = if (kind == MetadataListKind.Genres) genres else tags

    /** A copy with [kind]'s displayed list replaced by [values]. */
    fun withValues(kind: MetadataListKind, values: List<String>): ChipEditUIState =
        if (kind == MetadataListKind.Genres) copy(genres = values) else copy(tags = values)

    /** Whether an edit may start now: the user may edit, and the device is online. */
    val canChange: Boolean get() = canEdit && isOnline
}

/**
 * Owns the chip editor for one book: the optimistic genre and tag lists, the sheet, the library's suggestion pools,
 * and the saves. Saves run through [MetadataRepository], whose write chain keeps them in order; this class decides
 * which answer is shown.
 *
 * @property bookUuid The book the editor is for.
 */
@HiltViewModel(assistedFactory = ChipEditViewModel.Factory::class)
class ChipEditViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val repository: MetadataRepository,
    authRepository: AuthRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChipEditUIState())

    /** The editor's state. It changes only through the intents below and the saves and reads they start. */
    val uiState: StateFlow<ChipEditUIState> = _uiState.asStateFlow()

    /** The last list the server confirmed per kind. A failed save goes back to it. */
    private val confirmed = mutableMapOf<MetadataListKind, List<String>>()

    /** The id of the newest save per kind. Only that save's answer is shown. */
    private val latest = mutableMapOf<MetadataListKind, Long>()

    /** Saves sent or queued per kind that have not answered yet. */
    private val inFlight = mutableMapOf<MetadataListKind, Int>()

    private var nextRequestId = 0L
    private var nextEventId = 1L

    init {
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(canEdit = canEditChips(user)) }
            }
        }
    }

    /**
     * Seeds both lists from [book]. A kind with a save in flight keeps its list, so a reload never undoes a change
     * the user just made.
     */
    fun bind(book: Book) {
        var next = _uiState.value
        for (kind in MetadataListKind.entries) {
            if ((inFlight[kind] ?: 0) > 0) continue
            val values = chipValues(book.valuesOf(kind))
            confirmed[kind] = values
            next = next.withValues(kind, values)
        }
        _uiState.value = next.copy(bound = true)
    }

    /** Adds [value] to [kind]'s list when it is a new, valid chip. The field is cleared either way. */
    fun add(kind: MetadataListKind, value: String) {
        val state = _uiState.value
        if (!state.canChange || !state.bound) return
        _uiState.update { it.copy(entry = "") }
        val chip = SuggestionPool.committedChip(value, state.valuesOf(kind)) ?: return
        save(kind, state.valuesOf(kind) + chip)
    }

    /** Removes [value] from [kind]'s list. Removing the last chip saves an empty list. */
    fun remove(kind: MetadataListKind, value: String) {
        val state = _uiState.value
        if (!state.canChange || !state.bound) return
        val next = state.valuesOf(kind).filterNot { it == value }
        if (next.size != state.valuesOf(kind).size) save(kind, next)
    }

    /** Opens the sheet for [kind] and loads that kind's values, once per kind unless the load failed. */
    fun open(kind: MetadataListKind) {
        if (!_uiState.value.canChange) return
        _uiState.update { it.copy(openKind = kind, entry = "") }
        val pool = _uiState.value.pools[kind]
        if (pool != null && pool != PoolState.Failed) return
        _uiState.update { it.copy(pools = it.pools + (kind to PoolState.Loading)) }
        viewModelScope.launch {
            val next = when (val result = repository.getVocabulary(kind)) {
                is VocabularyResult.Success -> PoolState.Loaded(SuggestionPool.collect(result.entries))
                VocabularyResult.NoServer, is VocabularyResult.Failure -> PoolState.Failed
            }
            _uiState.update { it.copy(pools = it.pools + (kind to next)) }
        }
    }

    /** Closes the sheet. Saves already sent keep running. */
    fun close() {
        _uiState.update { it.copy(openKind = null, entry = "") }
    }

    /** Sets the sheet's field text, capped at [MAX_CHIP_LENGTH] characters. */
    fun updateEntry(text: String) {
        _uiState.update { it.copy(entry = text.take(MAX_CHIP_LENGTH)) }
    }

    /** Clears [ChipEditUIState.failure] if it is the failure [eventId]. A newer failure stays. */
    fun failureShown(eventId: Long) {
        _uiState.update { if (it.failure?.eventId == eventId) it.copy(failure = null) else it }
    }

    /** Clears [ChipEditUIState.bookUpdate] if it is the update [eventId]. A newer update stays. */
    fun bookUpdateApplied(eventId: Long) {
        _uiState.update { if (it.bookUpdate?.eventId == eventId) it.copy(bookUpdate = null) else it }
    }

    /** Shows [next] for [kind] at once, then saves the full list. The answer is applied when it arrives. */
    private fun save(kind: MetadataListKind, next: List<String>) {
        val id = ++nextRequestId
        latest[kind] = id
        inFlight[kind] = (inFlight[kind] ?: 0) + 1
        _uiState.update { it.withValues(kind, next) }

        viewModelScope.launch {
            val result = repository.replaceList(bookUuid, kind, next)
            inFlight[kind] = (inFlight[kind] ?: 1) - 1
            val newest = latest[kind] == id
            when (result) {
                is MetadataWriteResult.Saved -> {
                    val server = chipValues(result.book.valuesOf(kind))
                    confirmed[kind] = server
                    if (newest) {
                        _uiState.update { state ->
                            state.withValues(kind, server).copy(
                                bookUpdate = ChipEditBookUpdate(nextEventId++, merged(result.book, kind, state)),
                            )
                        }
                    }
                }
                MetadataWriteResult.Superseded -> Unit
                MetadataWriteResult.NoServer, is MetadataWriteResult.Failure -> if (newest) {
                    _uiState.update {
                        it.withValues(kind, confirmed[kind].orEmpty())
                            .copy(failure = ChipEditFailure(nextEventId++, kind))
                    }
                }
            }
        }
    }

    /** The merged record, keeping the displayed list of the other kind while it still has a save in flight. */
    private fun merged(book: Book, kind: MetadataListKind, state: ChipEditUIState): Book {
        val other = if (kind == MetadataListKind.Genres) MetadataListKind.Tags else MetadataListKind.Genres
        return if ((inFlight[other] ?: 0) > 0) book.withValues(other, state.valuesOf(other)) else book
    }

    /** Creates a [ChipEditViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ChipEditViewModel
    }
}
