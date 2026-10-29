package com.omnibus.omnibus.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BooksPageQuery
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibraryPageResult
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.LibrarySortPreference
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.data.library.local.LibrarySortPreferenceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the Library tab's books, paging and the category, sort and direction the books are queried
 * with. The screen reads one [LibraryUIState] and sends intents back; every reload is tagged so a
 * superseded request cannot overwrite newer results.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val sortPreferenceStore: LibrarySortPreferenceStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUIState(isInitialLoading = true))

    /** The screen's state. Shows the first page's loading state until the first result arrives. */
    val uiState: StateFlow<LibraryUIState> = _uiState.asStateFlow()

    /** Bumped by every reload, so results from an older reload are ignored. */
    private var reloadToken = 0
    private var reloadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        viewModelScope.launch {
            // Restored before the first request, so page 1 already carries the saved sort and direction.
            // The category is never restored, so every launch starts on All.
            val saved = sortPreferenceStore.preference.first()
            _uiState.update { it.copy(sort = saved.sort, direction = saved.direction) }
            reload(refreshing = false)
        }
    }

    /** Reloads the first page after a full-screen error. */
    fun retry() = reload(refreshing = false)

    /** Reloads the first page for pull to refresh. The result replaces the list wholesale. */
    fun refresh() = reload(refreshing = true)

    /**
     * Fetches the next page when [lastVisibleIndex] is close enough to the end of the loaded books.
     * Does nothing while another load is in flight or when there is no next cursor.
     */
    fun loadMoreIfNeeded(lastVisibleIndex: Int) {
        val state = _uiState.value
        val cursor = state.nextCursor ?: return
        if (state.isInitialLoading || state.isRefreshing || state.isLoadingMore) return
        if (lastVisibleIndex < state.books.size - LOAD_MORE_THRESHOLD) return

        val token = reloadToken
        // The query that produced the cursor, so the next page continues the same list.
        val query = state.toQuery()
        _uiState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            // The repository always emits at least once, so the last emission is the result.
            val result = libraryRepository.getBooksPageFlow(query, cursor).last()
            if (token == reloadToken) _uiState.update { it.applyLoadMore(result) }
        }
    }

    /** Clears the one-shot [LibraryUIState.transientError] once the screen has shown it. */
    fun transientErrorShown() {
        _uiState.update { it.copy(transientError = null) }
    }

    /**
     * Shows [category] and reloads from the first page. Does nothing when it is already selected,
     * and is never saved.
     */
    fun selectCategory(category: LibraryCategory) {
        if (category == _uiState.value.category) return
        _uiState.update { it.copy(category = category, nextCursor = null) }
        reload(refreshing = false, resetScroll = true)
    }

    /**
     * Orders the books by [sort], reloads from the first page and saves the choice. Does nothing when
     * it is already selected.
     */
    fun selectSort(sort: LibrarySort) {
        if (sort == _uiState.value.sort) return
        _uiState.update { it.copy(sort = sort, nextCursor = null) }
        persistSort()
        reload(refreshing = false, resetScroll = true)
    }

    /** Flips the sort order, reloads from the first page and saves the choice. */
    fun toggleDirection() {
        _uiState.update { it.copy(direction = it.direction.toggled(), nextCursor = null) }
        persistSort()
        reload(refreshing = false, resetScroll = true)
    }

    /** Clears the one-shot [LibraryUIState.scrollToTop] once the screen has scrolled the grid. */
    fun scrolledToTop() {
        _uiState.update { it.copy(scrollToTop = false) }
    }

    /**
     * Reloads the first page. The old books stay until the new first page arrives. When [resetScroll]
     * is set, the first successful page of this reload raises [LibraryUIState.scrollToTop] once.
     */
    private fun reload(refreshing: Boolean, resetScroll: Boolean = false) {
        val token = ++reloadToken
        loadMoreJob?.cancel()
        loadMoreJob = null
        reloadJob?.cancel()
        _uiState.update {
            if (refreshing) {
                it.copy(isRefreshing = true, isLoadingMore = false)
            } else {
                it.copy(isInitialLoading = true, isLoadingMore = false, error = null)
            }
        }
        // Captured before launching, so the request is the query that was current when it started.
        val query = _uiState.value.toQuery()
        // Launched in viewModelScope so a recomposition cancelling its collector cannot cancel the reload.
        reloadJob = viewModelScope.launch {
            var scrollPending = resetScroll
            libraryRepository.getBooksPageFlow(query).collect { result ->
                if (token != reloadToken) return@collect
                // Only the first successful page of a query change scrolls to the top.
                val scroll = scrollPending && result is LibraryPageResult.Success
                if (scroll) scrollPending = false
                _uiState.update { state ->
                    state.applyFirstPage(result).let { if (scroll) it.copy(scrollToTop = true) else it }
                }
            }
            if (token == reloadToken) {
                _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false) }
            }
        }
    }

    /** Saves the current sort and direction. The category is never saved. */
    private fun persistSort() {
        val state = _uiState.value
        viewModelScope.launch {
            sortPreferenceStore.save(LibrarySortPreference(state.sort, state.direction))
        }
    }

    private companion object {
        /** How many books from the end the last visible one may be before the next page is requested. */
        const val LOAD_MORE_THRESHOLD = 12
    }

    /** A first-page result replaces the books. A failure is a full-screen error only when nothing is shown. */
    private fun LibraryUIState.applyFirstPage(result: LibraryPageResult): LibraryUIState = when (result) {
        is LibraryPageResult.Success -> copy(
            books = result.page.books.distinctBy { it.id },
            // An empty page ends paging even if the server sent a cursor.
            nextCursor = result.page.nextCursor.takeIf { result.page.books.isNotEmpty() },
            error = null,
            hasLoaded = true,
        )

        is LibraryPageResult.NoServer -> withFirstPageFailure(LibraryUIError.NoServer)
        is LibraryPageResult.Failure -> withFirstPageFailure(LibraryUIError.Failed(result.error))
    }

    private fun LibraryUIState.withFirstPageFailure(uiError: LibraryUIError): LibraryUIState =
        if (books.isEmpty()) {
            copy(error = uiError, hasLoaded = true)
        } else {
            copy(transientError = uiError, hasLoaded = true)
        }

    /** A failed page keeps the cursor, so pagination is not ended and the next scroll retries it. */
    private fun LibraryUIState.applyLoadMore(result: LibraryPageResult): LibraryUIState = when (result) {
        is LibraryPageResult.Success -> copy(
            books = (books + result.page.books).distinctBy { it.id },
            nextCursor = result.page.nextCursor.takeIf { result.page.books.isNotEmpty() },
            isLoadingMore = false,
        )

        is LibraryPageResult.NoServer -> copy(isLoadingMore = false, transientError = LibraryUIError.NoServer)
        is LibraryPageResult.Failure -> copy(isLoadingMore = false, transientError = LibraryUIError.Failed(result.error))
    }
}

/**
 * What the Library screen shows. [screen] picks one of the four states by first match, so the rule
 * can be tested without Compose.
 *
 * @property error A full-screen failure. Only shown when there are no [books]; otherwise it's [transientError].
 * @property hasLoaded Whether a first-page result has arrived, successful or not.
 * @property transientError A one-shot error for a failed refresh or load-more, shown as a snackbar and cleared by [LibraryViewModel.transientErrorShown].
 * @property category The category the books are filtered to. Not restored on launch.
 * @property sort The sort key the books are ordered by.
 * @property direction The order the sort key runs in.
 * @property scrollToTop A one-shot request to scroll the grid to its first item after a query change. Cleared by [LibraryViewModel.scrolledToTop].
 */
data class LibraryUIState(
    val books: List<Book> = emptyList(),
    val nextCursor: String? = null,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: LibraryUIError? = null,
    val hasLoaded: Boolean = false,
    val transientError: LibraryUIError? = null,
    val category: LibraryCategory = LibraryCategory.All,
    val sort: LibrarySort = LibrarySort.NewestAdded,
    val direction: SortDirection = SortDirection.Desc,
    val scrollToTop: Boolean = false,
) {

    /** Whether the books are narrowed or reordered from the default view. Hidden formats never count. */
    val isFiltered: Boolean
        get() = category != LibraryCategory.All ||
            sort != LibrarySort.NewestAdded ||
            direction != SortDirection.Desc

    /** The page query for the current category, sort and direction. */
    fun toQuery(): BooksPageQuery = category.toQuery(sort, direction)

    /**
     * The state to show. Loading beats Error, Error beats Empty, and Content is the fallback once books
     * exist. A filtered result with no books is Content, so the heading and filter button stay on screen.
     */
    val screen: LibraryScreenState
        get() = when {
            isInitialLoading && books.isEmpty() -> LibraryScreenState.Loading
            error != null && books.isEmpty() -> LibraryScreenState.Error(error)
            books.isEmpty() && !isFiltered -> LibraryScreenState.Empty
            else -> LibraryScreenState.Content
        }
}

/** The screen-level state that [LibraryUIState.screen] resolves to. */
sealed interface LibraryScreenState {

    data object Loading : LibraryScreenState

    /** [error] is resolved to text by the UI and shown under the error title. */
    data class Error(val error: LibraryUIError) : LibraryScreenState

    data object Empty : LibraryScreenState

    data object Content : LibraryScreenState
}

/** Why the Library could not load. The data layer reports the cause; the UI layer picks the text. */
sealed interface LibraryUIError {

    /** No server address is saved yet. */
    data object NoServer : LibraryUIError

    /** The repository reported [error]. */
    data class Failed(val error: LibraryError) : LibraryUIError
}
