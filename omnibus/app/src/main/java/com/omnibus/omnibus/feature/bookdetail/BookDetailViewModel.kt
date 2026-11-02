package com.omnibus.omnibus.feature.bookdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibraryRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the Book Detail page's state for one book. The screen reads [uiState] and sends intents back.
 *
 * `screenShown()` also fires on rotation and on shell tab switches. Those reloads are silent and harmless.
 *
 * @property bookUuid The book this page shows, as [Book.uuid].
 */
@HiltViewModel(assistedFactory = BookDetailViewModel.Factory::class)
class BookDetailViewModel @AssistedInject constructor(
    @Assisted val bookUuid: String,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUIState())

    /** The page's state. It changes only through the intents below and the book load. */
    val uiState: StateFlow<BookDetailUIState> = _uiState.asStateFlow()

    /** Identifies the newest load, so a superseded load's results are dropped. */
    private var reloadToken = 0

    private var loadJob: Job? = null

    /** Whether the screen has been shown once. The first [screenShown] is the initial load's. */
    private var hasBeenShown = false

    init {
        load(LoadMode.FullScreen, bumpKey = false)
    }

    /** Creates a [BookDetailViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): BookDetailViewModel
    }

    /** Tries the full-screen load again after an error. */
    fun retry() = load(LoadMode.FullScreen, bumpKey = true)

    /** Reloads for a pull to refresh. The content stays on screen. */
    fun refresh() = load(LoadMode.Refresh, bumpKey = true)

    /** Reloads silently, for a section's or the page's own request. */
    fun reloadBook() = load(LoadMode.Silent, bumpKey = true)

    /** Called each time the screen enters composition. The first call is the initial load's; later ones are returns. */
    fun screenShown() {
        if (!hasBeenShown) {
            hasBeenShown = true
            return
        }
        load(LoadMode.Silent, bumpKey = true)
    }

    /** Replaces the shown book without a reload, for a change a section made. Other books are ignored. */
    fun bookChanged(book: Book) {
        if (book.uuid == bookUuid) _uiState.update { it.copy(book = book) }
    }

    /** Clears the one-shot error after the snackbar has been shown. */
    fun transientErrorShown() = _uiState.update { it.copy(transientError = null) }

    private fun load(mode: LoadMode, bumpKey: Boolean) {
        val token = ++reloadToken
        loadJob?.cancel()
        _uiState.update {
            val keyed = if (bumpKey) it.copy(reloadKey = it.reloadKey + 1) else it
            when (mode) {
                LoadMode.FullScreen -> keyed.copy(isLoading = true, error = null)
                LoadMode.Refresh -> keyed.copy(isRefreshing = true)
                LoadMode.Silent -> keyed
            }
        }
        // viewModelScope, so a recomposition cancelling a collector can't cancel the load (as LibraryViewModel.reload).
        loadJob = viewModelScope.launch {
            libraryRepository.getBookFlow(bookUuid).collect { result ->
                if (token != reloadToken) return@collect
                _uiState.update { it.applyResult(result, mode) }
            }
            if (token == reloadToken) _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
        }
    }

    private fun BookDetailUIState.applyResult(result: BookResult, mode: LoadMode): BookDetailUIState {
        val failure = when (result) {
            is BookResult.Success -> return copy(book = result.book, error = null, isLoading = false)
            BookResult.NotFound -> BookDetailError.NotFound
            BookResult.NoServer -> BookDetailError.NoServer
            is BookResult.Failure -> BookDetailError.Failed(result.error)
        }
        return when {
            book == null -> copy(error = failure, isLoading = false) // full-screen error, also for a silent reload
            mode == LoadMode.Refresh -> copy(transientError = failure) // pull to refresh: snackbar, content kept
            else -> this // silent reload or Retry over content: nothing
        }
    }

    private enum class LoadMode { FullScreen, Refresh, Silent }
}

/** Why the page's book could not be shown. */
sealed interface BookDetailError {

    /** The server has no book with that uuid. */
    data object NotFound : BookDetailError

    /** No server is saved, so no request was made. */
    data object NoServer : BookDetailError

    /** The read failed; [error] says why. */
    data class Failed(val error: LibraryError) : BookDetailError
}

/** What the page shows: its loading view, an error, or the book. */
sealed interface BookDetailScreenState {

    data object Loading : BookDetailScreenState

    data class Error(val error: BookDetailError) : BookDetailScreenState

    data class Content(val book: Book) : BookDetailScreenState
}

/**
 * The page's state.
 *
 * @property book The book to show, once a load has succeeded.
 * @property isLoading True while a full-screen load (the first load, or Retry) is in flight.
 * @property isRefreshing True while a pull to refresh is in flight.
 * @property error A full-screen error. Only meaningful while [book] is null.
 * @property transientError A one-shot error for a failed refresh, cleared by `transientErrorShown()`.
 * @property reloadKey Counts the reloads, so sections can reload when it changes.
 */
data class BookDetailUIState(
    val book: Book? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: BookDetailError? = null,
    val transientError: BookDetailError? = null,
    val reloadKey: Int = 0,
) {

    /** The screen to show: the book when there is one, else the error, else loading. */
    val screen: BookDetailScreenState
        get() = when {
            book != null -> BookDetailScreenState.Content(book)
            error != null -> BookDetailScreenState.Error(error)
            else -> BookDetailScreenState.Loading
        }
}
