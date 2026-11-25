package com.omnibus.omnibus.feature.search.facet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BooksFacet
import com.omnibus.omnibus.data.library.BooksPageQuery
import com.omnibus.omnibus.data.library.LibraryPageResult
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.feature.search.SearchFailure
import com.omnibus.omnibus.feature.search.toSearchFailure
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How close to the end (in rows) the last visible row may be before the next page is asked for. */
internal const val FACET_LOAD_MORE_THRESHOLD = 10

/** What the facet list shows. */
data class FacetBooksUIState(val body: FacetBody = FacetBody.Loading)

/** The body of a facet list. */
sealed interface FacetBody {
    /** The first page is on its way. */
    data object Loading : FacetBody

    /**
     * The books shown so far. [total] is `X-Total-Count` as sent, or null when the header is missing. [nextCursor] is null
     * on the last page. [nextPage] says whether a later page is on its way, or has failed.
     */
    data class Loaded(
        val books: List<Book>,
        val total: Long?,
        val nextCursor: String?,
        val nextPage: NextPageState,
    ) : FacetBody

    /** The first page had no books. */
    data object Empty : FacetBody

    /** The first page failed. [failure] is what the reader is told. */
    data class Failed(val failure: SearchFailure) : FacetBody

    /** The session ended or no server is saved: render nothing, the app is leaving for sign-in. */
    data object Ended : FacetBody
}

/** Where paging through a facet list stands. */
enum class NextPageState { Idle, Loading, Failed }

/**
 * Loads the books filed under one facet, title order, a page at a time. The first page loads on open and [retry] asks
 * again. [loadMoreIfNeeded] asks for the next page as the reader nears the end; a page that fails keeps the books shown
 * and waits for [retryNextPage].
 */
@HiltViewModel(assistedFactory = FacetBooksViewModel.Factory::class)
class FacetBooksViewModel @AssistedInject constructor(
    @Assisted private val route: FacetBooksRoute,
    private val repository: LibraryRepository,
) : ViewModel() {

    /** Creates the view model for one facet route. */
    @AssistedFactory
    interface Factory {
        fun create(route: FacetBooksRoute): FacetBooksViewModel
    }

    private val query = BooksPageQuery(
        sort = LibrarySort.Title,
        dir = SortDirection.Asc,
        facet = BooksFacet(route.kind, route.name),
    )

    private val _uiState = MutableStateFlow(FacetBooksUIState())

    /** The facet list's state. */
    val uiState: StateFlow<FacetBooksUIState> = _uiState.asStateFlow()

    private var pageJob: Job? = null

    init {
        loadFirstPage()
    }

    /** "Try again" on a failed first page: asks the server for the first page again. */
    fun retry() = loadFirstPage()

    /** Asks for the next page when the reader is within [FACET_LOAD_MORE_THRESHOLD] rows of the end and none is pending. */
    fun loadMoreIfNeeded(itemsAfterLastVisible: Int) {
        val loaded = _uiState.value.body as? FacetBody.Loaded ?: return
        val cursor = loaded.nextCursor ?: return
        if (loaded.nextPage != NextPageState.Idle) return
        if (itemsAfterLastVisible >= FACET_LOAD_MORE_THRESHOLD) return
        loadPage(cursor)
    }

    /** "Try again" under a failed next page: asks for the same page again. */
    fun retryNextPage() {
        val loaded = _uiState.value.body as? FacetBody.Loaded ?: return
        val cursor = loaded.nextCursor ?: return
        if (loaded.nextPage != NextPageState.Failed) return
        loadPage(cursor)
    }

    private fun loadFirstPage() {
        pageJob?.cancel()
        _uiState.update { it.copy(body = FacetBody.Loading) }
        pageJob = viewModelScope.launch {
            val body = when (val result = repository.getBooksPageFlow(query).last()) {
                is LibraryPageResult.Success -> if (result.page.books.isEmpty()) {
                    FacetBody.Empty
                } else {
                    FacetBody.Loaded(
                        books = result.page.books.distinctBy { it.id },
                        total = result.page.total,
                        nextCursor = result.page.nextCursor,
                        nextPage = NextPageState.Idle,
                    )
                }
                is LibraryPageResult.Failure -> result.error.toSearchFailure()
                    ?.let { FacetBody.Failed(it) }
                    ?: FacetBody.Ended
                LibraryPageResult.NoServer -> FacetBody.Ended
            }
            _uiState.update { it.copy(body = body) }
        }
    }

    private fun loadPage(cursor: String) {
        _uiState.update { state ->
            val loaded = state.body as? FacetBody.Loaded ?: return@update state
            state.copy(body = loaded.copy(nextPage = NextPageState.Loading))
        }
        pageJob = viewModelScope.launch {
            val result = repository.getBooksPageFlow(query, cursor).last()
            _uiState.update { state ->
                val loaded = state.body as? FacetBody.Loaded ?: return@update state
                state.copy(body = loaded.afterPage(result))
            }
        }
    }

    /** The list after [result] answers the page that was requested: the books appended, or the paging stopped or held. */
    private fun FacetBody.Loaded.afterPage(result: LibraryPageResult): FacetBody.Loaded = when (result) {
        is LibraryPageResult.Success -> copy(
            books = (books + result.page.books).distinctBy { it.id },
            // An empty page ends paging, even when the server sent a cursor.
            nextCursor = result.page.nextCursor.takeIf { result.page.books.isNotEmpty() },
            nextPage = NextPageState.Idle,
        )
        is LibraryPageResult.Failure -> if (result.error.toSearchFailure() == null) {
            // The session ended, so the app is leaving for sign-in: stop paging and show nothing more.
            copy(nextCursor = null, nextPage = NextPageState.Idle)
        } else {
            copy(nextPage = NextPageState.Failed)
        }
        LibraryPageResult.NoServer -> copy(nextCursor = null, nextPage = NextPageState.Idle)
    }
}
