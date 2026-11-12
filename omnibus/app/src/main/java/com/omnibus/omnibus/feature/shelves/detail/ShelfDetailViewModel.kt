package com.omnibus.omnibus.feature.shelves.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfPreviewsResult
import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.ShelfBooksResult
import com.omnibus.omnibus.data.shelves.ShelfDetail
import com.omnibus.omnibus.data.shelves.ShelfDetailResult
import com.omnibus.omnibus.data.shelves.ShelvesError
import com.omnibus.omnibus.data.shelves.ShelvesRepository
import com.omnibus.omnibus.feature.library.LibraryGridItem
import com.omnibus.omnibus.feature.library.libraryGridItems
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns one shelf's detail screen. The screen reads one [ShelfDetailUIState] and sends intents back. Each load is
 * tagged, so a superseded load never overwrites a newer one, and every emission is applied, since a cached value
 * can arrive before the network one.
 */
@HiltViewModel(assistedFactory = ShelfDetailViewModel.Factory::class)
class ShelfDetailViewModel @AssistedInject constructor(
    @Assisted private val shelfId: Long,
    private val shelvesRepository: ShelvesRepository,
    private val libraryRepository: LibraryRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    /** Creates the view model for one shelf. */
    @AssistedFactory
    interface Factory {
        fun create(shelfId: Long): ShelfDetailViewModel
    }

    private val _uiState = MutableStateFlow(ShelfDetailUIState(shelfId = shelfId))

    /** The screen's state. */
    val uiState: StateFlow<ShelfDetailUIState> = _uiState.asStateFlow()

    /** Bumped by every load, so results from an older load are ignored. */
    private var loadToken = 0
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(viewer = user?.let { current -> ShelfViewer(current.id, current.isAdmin) }) }
            }
        }
    }

    /** Loads the shelf. Called each time the screen is shown. */
    fun screenShown() = load(refreshing = false)

    /** Reloads the shelf for pull to refresh. */
    fun refresh() = load(refreshing = true)

    /** Reloads the shelf after the full-screen error. */
    fun retry() = load(refreshing = false)

    /** Opens the stack of series [seriesId] in place. */
    fun unfoldSeries(seriesId: Long) {
        _uiState.update { it.copy(unfoldedSeries = it.unfoldedSeries + seriesId) }
    }

    /** Folds the unfolded series [seriesId] back into a stack. */
    fun foldUpSeries(seriesId: Long) {
        _uiState.update { it.copy(unfoldedSeries = it.unfoldedSeries - seriesId) }
    }

    /** Clears the one-shot transient failure once the screen has shown it. */
    fun transientErrorShown() {
        _uiState.update { it.copy(transientError = null, transientNoServer = false) }
    }

    private fun load(refreshing: Boolean) {
        val token = ++loadToken
        loadJob?.cancel()
        _uiState.update { if (refreshing) it.copy(isRefreshing = true) else it }
        loadJob = viewModelScope.launch {
            coroutineScope {
                launch { readPreview(token) }
                launch { readDetail(token, refreshing) }
                launch { readBooks(token, refreshing) }
            }
            if (token == loadToken) _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun readPreview(token: Int) {
        libraryRepository.getShelfPreviewsFlow().collect { result ->
            if (token != loadToken) return@collect
            // A failed shelves read is ignored here: the detail read reports its own failure.
            if (result is ShelfPreviewsResult.Success) {
                _uiState.update { state ->
                    state.copy(preview = result.previews.firstOrNull { it.shelf.id == shelfId } ?: state.preview)
                }
            }
        }
    }

    private suspend fun readDetail(token: Int, refreshing: Boolean) {
        shelvesRepository.getShelfFlow(shelfId).collect { result ->
            if (token != loadToken) return@collect
            _uiState.update { it.applyDetail(result, refreshing) }
        }
    }

    private suspend fun readBooks(token: Int, refreshing: Boolean) {
        val clauses = _uiState.value.clauses
        shelvesRepository.getShelfBooksFlow(shelfId, clauses).collect { result ->
            if (token != loadToken) return@collect
            _uiState.update { it.applyBooks(result, clauses, refreshing) }
        }
    }

    private fun ShelfDetailUIState.applyDetail(result: ShelfDetailResult, refreshing: Boolean): ShelfDetailUIState =
        when (result) {
            is ShelfDetailResult.Success -> copy(detail = result.detail, detailError = null, detailNoServer = false)
            ShelfDetailResult.Gone -> copy(isGone = true)
            ShelfDetailResult.NoServer -> withDetailFailure(error = null, noServer = true, refreshing = refreshing)
            is ShelfDetailResult.Failure -> withDetailFailure(result.error, noServer = false, refreshing = refreshing)
        }

    /** With no identity to show, a failure is the full-screen error. With one, it is a snackbar on a refresh. */
    private fun ShelfDetailUIState.withDetailFailure(
        error: ShelvesError?,
        noServer: Boolean,
        refreshing: Boolean,
    ): ShelfDetailUIState = when {
        detail == null && preview == null -> copy(detailError = error, detailNoServer = noServer)
        refreshing -> copy(transientError = error, transientNoServer = noServer)
        else -> this
    }

    private fun ShelfDetailUIState.applyBooks(
        result: ShelfBooksResult,
        clauses: List<FilterClause>,
        refreshing: Boolean,
    ): ShelfDetailUIState = when (result) {
        is ShelfBooksResult.Success -> copy(
            books = result.books,
            pageError = null,
            pageNoServer = false,
            unfilteredBooks = if (clauses.isEmpty()) result.books else unfilteredBooks,
        )
        ShelfBooksResult.Gone -> copy(isGone = true)
        ShelfBooksResult.NoServer -> withPageFailure(error = null, noServer = true, refreshing = refreshing)
        is ShelfBooksResult.Failure -> withPageFailure(result.error, noServer = false, refreshing = refreshing)
    }

    /** Failed books with nothing cached are the page's error. Books already on screen stay, with a snackbar on a refresh. */
    private fun ShelfDetailUIState.withPageFailure(
        error: ShelvesError?,
        noServer: Boolean,
        refreshing: Boolean,
    ): ShelfDetailUIState = when {
        books == null -> copy(pageError = error, pageNoServer = noServer)
        refreshing -> copy(transientError = error, transientNoServer = noServer)
        else -> this
    }
}

/**
 * What the shelf detail screen shows. Each field is what was last read, so a cached value and a network value are
 * the same shape. [screen], [content] and the derived values are pure functions of these fields.
 *
 * @property detail The shelf from the detail read, once it has answered.
 * @property detailError Why the detail read failed, when it did and there is no identity to show. Null with
 * [detailNoServer] set means no server is saved.
 * @property books The books of the current clauses, or null before any answer. Books from an earlier answer stay here
 * when a later read fails.
 * @property unfilteredBooks The books of the last unfiltered read. Only an unfiltered read sets it.
 * @property transientError A one-shot failure for a refresh that found content on screen. Null with
 * [transientNoServer] set means no server is saved.
 */
data class ShelfDetailUIState(
    val shelfId: Long,
    val preview: ShelfPreview? = null,
    val detail: ShelfDetail? = null,
    val detailError: ShelvesError? = null,
    val detailNoServer: Boolean = false,
    val isGone: Boolean = false,
    val books: List<Book>? = null,
    val unfilteredBooks: List<Book>? = null,
    val pageError: ShelvesError? = null,
    val pageNoServer: Boolean = false,
    val clauses: List<FilterClause> = emptyList(),
    val isRefreshing: Boolean = false,
    val unfoldedSeries: Set<Long> = emptySet(),
    val viewer: ShelfViewer? = null,
    val transientError: ShelvesError? = null,
    val transientNoServer: Boolean = false,
) {

    /** The shelf's identity: the detail once it has landed, else the cached preview's. */
    val identity: Shelf? get() = detail?.shelf ?: preview?.shelf

    /** What the viewer may do to the shelf. */
    val actions: ShelfActions get() = shelfActionsOf(identity, viewer)

    /** Whether the facet row shows: not for the unreachable state, nor for an unfiltered empty shelf. */
    val showsFacets: Boolean
        get() = screen == ShelfDetailScreenState.Content &&
            content !is ShelfContentState.Unreachable &&
            !(content is ShelfContentState.Empty && clauses.isEmpty())

    /** The covers of the hero's mosaic. */
    val mosaicCovers: List<String> get() = mosaicCoversOf(unfilteredBooks, preview)

    /** The grid's cells for [books]. */
    val gridItems: List<LibraryGridItem>
        get() = libraryGridItems(shelfEntries(books.orEmpty()), unfoldedSeries)

    /** The state the whole screen shows. */
    val screen: ShelfDetailScreenState
        get() = when {
            isGone -> ShelfDetailScreenState.Gone
            identity == null && (detailError != null || detailNoServer) -> ShelfDetailScreenState.Error(detailError)
            identity == null -> ShelfDetailScreenState.Loading
            else -> ShelfDetailScreenState.Content
        }

    /** What fills the area under the hero, decided by the first state that applies. */
    val content: ShelfContentState
        get() {
            val count = identity?.bookCount ?: 0L
            val current = books ?: return when {
                pageError == null && !pageNoServer -> ShelfContentState.Loading
                clauses.isNotEmpty() -> ShelfContentState.FilterFailed(
                    unreachable = pageError == ShelvesError.Unreachable,
                    error = pageError,
                )
                pageError == ShelvesError.Unreachable && count > 0 -> ShelfContentState.Unreachable(count)
                else -> ShelfContentState.Failed(pageError)
            }
            return when {
                clauses.isNotEmpty() && current.isEmpty() -> ShelfContentState.FilteredEmpty
                current.isEmpty() -> identity?.let { ShelfContentState.Empty(it.kind) } ?: ShelfContentState.Loading
                else -> ShelfContentState.Books
            }
        }
}
