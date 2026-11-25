package com.omnibus.omnibus.feature.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.series.SeriesDetailsResult
import com.omnibus.omnibus.data.series.SeriesError
import com.omnibus.omnibus.data.series.SeriesRepository
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
 * Owns one series' Series Details page. The page loads once when it opens (spec R10); pull to refresh and the
 * retry buttons load again. Each load is tagged, so a superseded load never overwrites a newer one.
 */
@HiltViewModel(assistedFactory = SeriesDetailsViewModel.Factory::class)
class SeriesDetailsViewModel @AssistedInject constructor(
    @Assisted private val seriesId: Long,
    @Assisted name: String,
    private val seriesRepository: SeriesRepository,
) : ViewModel() {

    /** Creates the view model for one series page. */
    @AssistedFactory
    interface Factory {
        fun create(seriesId: Long, name: String): SeriesDetailsViewModel
    }

    private val _uiState = MutableStateFlow(SeriesDetailsUIState(seriesId = seriesId, name = name))

    /** The page's state. */
    val uiState: StateFlow<SeriesDetailsUIState> = _uiState.asStateFlow()

    /** Bumped by every load, so results from an older load are ignored. */
    private var loadToken = 0
    private var loadJob: Job? = null

    init {
        load(refreshing = false)
    }

    /** Reloads the series for pull to refresh or the snackbar's Retry. */
    fun refresh() = load(refreshing = true)

    /** Reloads the series after a full-screen error. */
    fun retry() = load(refreshing = false)

    /** Clears the one-shot [SeriesDetailsUIState.refreshFailure] once the snackbar has been shown. */
    fun refreshFailureShown() {
        _uiState.update { it.copy(refreshFailure = null) }
    }

    private fun load(refreshing: Boolean) {
        val token = ++loadToken
        loadJob?.cancel()
        _uiState.update { if (refreshing) it.copy(isRefreshing = true) else it.copy(loadFailure = null) }
        loadJob = viewModelScope.launch {
            val result = seriesRepository.getSeriesDetails(seriesId)
            if (token != loadToken) return@launch
            _uiState.update { it.applied(result, refreshing).copy(isRefreshing = false) }
        }
    }

    private fun SeriesDetailsUIState.applied(result: SeriesDetailsResult, refreshing: Boolean): SeriesDetailsUIState =
        when (result) {
            is SeriesDetailsResult.Success -> copy(
                name = result.details.name.ifBlank { name },
                books = result.details.books,
                loadFailure = null,
                notFound = false,
            )
            SeriesDetailsResult.NoServer -> withFailure(SeriesLoadFailure.SessionExpired, refreshing)
            is SeriesDetailsResult.Failure -> when (result.error) {
                SeriesError.NotFound -> copy(notFound = true, books = null)
                SeriesError.Unreachable -> withFailure(SeriesLoadFailure.Unreachable, refreshing)
                SeriesError.SessionExpired -> withFailure(SeriesLoadFailure.SessionExpired, refreshing)
                SeriesError.UnexpectedResponse, is SeriesError.Http -> withFailure(SeriesLoadFailure.WentWrong, refreshing)
            }
        }

    /**
     * With no books to show, a failure is the full-screen state. With books on screen, a refresh's failure is a snackbar,
     * and an expired session is left to the shell, which returns to sign-in.
     */
    private fun SeriesDetailsUIState.withFailure(failure: SeriesLoadFailure, refreshing: Boolean): SeriesDetailsUIState {
        val refreshKind = when (failure) {
            SeriesLoadFailure.Unreachable -> SeriesRefreshFailure.Unreachable
            SeriesLoadFailure.WentWrong -> SeriesRefreshFailure.WentWrong
            SeriesLoadFailure.SessionExpired -> null
        }
        return when {
            books == null -> copy(loadFailure = failure)
            refreshing && refreshKind != null -> copy(refreshFailure = refreshKind)
            else -> this
        }
    }
}

/** What fills the page under the top bar. */
enum class SeriesDetailsScreenState { Loading, Loaded, Empty, NotFound, Unreachable, WentWrong, SessionExpired }

/** Why a refresh with books on screen failed, for the one-shot snackbar. */
enum class SeriesRefreshFailure { Unreachable, WentWrong }

/** Why a load with no books on screen failed. */
enum class SeriesLoadFailure { Unreachable, WentWrong, SessionExpired }

/**
 * What the Series Details page shows. [books] is what the last successful read returned, kept through failed refreshes.
 * [screen], [entries] and [countAuthor] are pure functions of these fields.
 *
 * @property name The route's name until a read succeeds, then the server's.
 * @property books The series' books in the server's order, or null before a read has succeeded.
 * @property loadFailure Why the page has no books, when a load failed with none on screen.
 * @property notFound Whether the server said the series is gone.
 * @property refreshFailure A one-shot failure of a refresh that kept the books on screen.
 */
data class SeriesDetailsUIState(
    val seriesId: Long,
    val name: String,
    val books: List<Book>? = null,
    val loadFailure: SeriesLoadFailure? = null,
    val notFound: Boolean = false,
    val isRefreshing: Boolean = false,
    val refreshFailure: SeriesRefreshFailure? = null,
) {

    /** The full-screen state, decided by the first state that applies. */
    val screen: SeriesDetailsScreenState
        get() = when {
            notFound -> SeriesDetailsScreenState.NotFound
            books == null -> when (loadFailure) {
                null -> SeriesDetailsScreenState.Loading
                SeriesLoadFailure.Unreachable -> SeriesDetailsScreenState.Unreachable
                SeriesLoadFailure.WentWrong -> SeriesDetailsScreenState.WentWrong
                SeriesLoadFailure.SessionExpired -> SeriesDetailsScreenState.SessionExpired
            }
            books.isEmpty() -> SeriesDetailsScreenState.Empty
            else -> SeriesDetailsScreenState.Loaded
        }

    /** The list's rows: the books with placeholders for the numbers they leave out. */
    val entries: List<SeriesEntry> get() = seriesEntries(books.orEmpty())

    /** The author named on the count line, or null when there is none. */
    val countAuthor: String? get() = books?.let(::seriesCountAuthor)
}
