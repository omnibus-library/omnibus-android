package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.shelves.BookShelves
import com.omnibus.omnibus.data.shelves.BookShelvesResult
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.data.shelves.ShelfRulesResult
import com.omnibus.omnibus.data.shelves.ShelfWriteResult
import com.omnibus.omnibus.data.shelves.ShelvesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The More menu's Add to shelf state for one book: whether the menu row is enabled, whether the sheet is open, and
 * what it shows.
 *
 * @property isOnline Whether the device is online. Offline, the menu row is disabled.
 * @property failure The latest failed write, for the page to report once. Cleared by [AddToShelfViewModel.failureShown].
 */
data class AddToShelfUIState(
    val isOnline: Boolean = true,
    val sheetOpen: Boolean = false,
    val content: ShelfSheetContent = ShelfSheetContent.Loading,
    val failure: ShelfToggleFailure? = null,
)

/**
 * A write that did not stick and was reverted.
 *
 * @property eventId Increases with each failure, so the page reports each one once.
 */
data class ShelfToggleFailure(val eventId: Long, val shelfName: String, val wasAdding: Boolean)

/**
 * Owns the Add to shelf sheet for one book. Toggles are optimistic: a write that fails reverts the row and sets
 * [AddToShelfUIState.failure]. Writes run in [viewModelScope], so closing the sheet does not cancel them.
 *
 * @property bookUuid The book the sheet is for.
 */
@HiltViewModel(assistedFactory = AddToShelfViewModel.Factory::class)
class AddToShelfViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val shelvesRepository: ShelvesRepository,
    private val authRepository: AuthRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddToShelfUIState())

    /** The sheet's state. It changes only through the intents below and the reads and writes they start. */
    val uiState: StateFlow<AddToShelfUIState> = _uiState.asStateFlow()

    private var shelves: BookShelves? = null
    private var user: CurrentUser? = null

    /** The hand-picked shelves the server last said the book is on. */
    private var serverMember: Set<Long> = emptySet()

    /** The hand-picked shelves the book is on now, including optimistic changes. */
    private var member: Set<Long> = emptySet()

    /** The shelves with a write in flight. Taps on them are ignored. */
    private var pending: Set<Long> = emptySet()
    private var rules: Map<Long, ShelfRules> = emptyMap()
    private var loadJob: Job? = null
    private var lastReloadKey: Int? = null
    private var nextEventId = 1L

    init {
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    /** Opens the sheet in Loading and reads its shelves. A read already in flight is superseded. */
    fun openSheet() {
        _uiState.update { it.copy(sheetOpen = true, content = ShelfSheetContent.Loading) }
        load()
    }

    /** Closes the sheet and stops reading. Writes already sent keep running. */
    fun closeSheet() {
        _uiState.update { it.copy(sheetOpen = false) }
        loadJob?.cancel()
    }

    /** Reads the shelves again after a failed read. */
    fun retry() {
        _uiState.update { it.copy(content = ShelfSheetContent.Loading) }
        load()
    }

    /** Adds the book to, or removes it from, the manual shelf [shelfId]. Ignored while that shelf has a write in flight. */
    fun toggle(shelfId: Long) {
        val content = _uiState.value.content as? ShelfSheetContent.Loaded ?: return
        if (shelfId in pending || content.manual.none { it.id == shelfId }) return
        val adding = shelfId !in member
        val name = nameOf(shelfId)

        member = if (adding) member + shelfId else member - shelfId
        pending = pending + shelfId
        rebuild()

        viewModelScope.launch {
            val result = if (adding) {
                shelvesRepository.addBook(shelfId, bookUuid)
            } else {
                shelvesRepository.removeBook(shelfId, bookUuid)
            }
            pending = pending - shelfId
            if (result != ShelfWriteResult.Success) {
                member = if (adding) member - shelfId else member + shelfId
                _uiState.update {
                    it.copy(failure = ShelfToggleFailure(nextEventId++, name, wasAdding = adding))
                }
            }
            publish()
        }
    }

    /** Clears [AddToShelfUIState.failure] if it is the failure [eventId]. A newer failure stays. */
    fun failureShown(eventId: Long) {
        _uiState.update { if (it.failure?.eventId == eventId) it.copy(failure = null) else it }
    }

    /**
     * Reloads the shelves when the page reloads while the sheet is open. The first key seen is only recorded.
     */
    fun pageReloaded(reloadKey: Int) {
        val previous = lastReloadKey
        lastReloadKey = reloadKey
        if (previous != null && previous != reloadKey && _uiState.value.sheetOpen) load()
    }

    /** Reads the shelves and the rules of the matching smart shelves. Supersedes any read in flight. */
    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val who = authRepository.currentUser.first()
            if (who == null) {
                _uiState.update { it.copy(content = ShelfSheetContent.Failed) }
                return@launch
            }
            user = who
            when (val result = shelvesRepository.getBookShelves(bookUuid)) {
                is BookShelvesResult.Success -> {
                    val book = result.shelves
                    shelves = book
                    serverMember = book.memberShelfIds
                    member = (serverMember - pending) + (member intersect pending)
                    rules = emptyMap()
                    rebuild()
                    matchingOwnSmartShelves(book, who).forEach { shelf ->
                        launch {
                            val read = shelvesRepository.getShelfRules(shelf.id)
                            if (read is ShelfRulesResult.Success) {
                                rules = rules + (shelf.id to read.rules)
                                publish()
                            }
                        }
                    }
                }
                else -> _uiState.update { it.copy(content = ShelfSheetContent.Failed) }
            }
        }
    }

    /** Rebuilds the loaded sheet from the current sets. Ignored unless the sheet is Loaded, so a late write never overwrites Loading. */
    private fun publish() {
        if (_uiState.value.content is ShelfSheetContent.Loaded) rebuild()
    }

    /** Rebuilds the sheet content from the current sets, whatever its state. */
    private fun rebuild() {
        val book = shelves ?: return
        val who = user ?: return
        _uiState.update {
            it.copy(content = buildShelfSheet(book, who, bookUuid, member, serverMember, pending, rules))
        }
    }

    private fun nameOf(shelfId: Long): String =
        shelves?.shelves?.firstOrNull { it.id == shelfId }?.name.orEmpty()

    /** Creates an [AddToShelfViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): AddToShelfViewModel
    }
}
