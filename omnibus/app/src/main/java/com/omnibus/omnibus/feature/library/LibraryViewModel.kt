package com.omnibus.omnibus.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BooksPageQuery
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibraryPageResult
import com.omnibus.omnibus.data.library.LibraryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the Library tab's books and paging. The screen reads one [LibraryUIState] and sends intents
 * back; every reload is tagged so a superseded request cannot overwrite newer results.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUIState(isInitialLoading = true))

    /** The screen's state. Shows the first page's loading state until the first result arrives. */
    val uiState: StateFlow<LibraryUIState> = _uiState.asStateFlow()

    /** Bumped by every reload, so results from an older reload are ignored. */
    private var reloadToken = 0
    private var reloadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        reload(refreshing = false)
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
        _uiState.update { it.copy(isLoadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            // The repository always emits at least once, so the last emission is the result.
            val result = libraryRepository.getBooksPageFlow(DEFAULT_QUERY, cursor).last()
            if (token == reloadToken) _uiState.update { it.applyLoadMore(result) }
        }
    }

    /** Clears the one-shot [LibraryUIState.transientError] once the screen has shown it. */
    fun transientErrorShown() {
        _uiState.update { it.copy(transientError = null) }
    }

    private fun reload(refreshing: Boolean) {
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
        // Launched in viewModelScope so a recomposition cancelling its collector cannot cancel the reload.
        reloadJob = viewModelScope.launch {
            libraryRepository.getBooksPageFlow(DEFAULT_QUERY).collect { result ->
                if (token == reloadToken) _uiState.update { it.applyFirstPage(result) }
            }
            if (token == reloadToken) {
                _uiState.update { it.copy(isInitialLoading = false, isRefreshing = false) }
            }
        }
    }

    private companion object {
        val DEFAULT_QUERY = BooksPageQuery()

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
) {

    /** The state to show. Loading beats Error, Error beats Empty, and Content is the fallback once books exist. */
    val screen: LibraryScreenState
        get() = when {
            isInitialLoading && books.isEmpty() -> LibraryScreenState.Loading
            error != null && books.isEmpty() -> LibraryScreenState.Error(error)
            books.isEmpty() -> LibraryScreenState.Empty
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
