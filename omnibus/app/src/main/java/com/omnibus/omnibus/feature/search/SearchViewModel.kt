package com.omnibus.omnibus.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.search.LibrarySearchResults
import com.omnibus.omnibus.data.search.LibrarySearchRepository
import com.omnibus.omnibus.data.search.PaletteSearchResult
import com.omnibus.omnibus.data.search.SearchMatches
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Wait after the last change to the query before searching (spec "Debounce"). */
const val SEARCH_DEBOUNCE_MILLIS = 200L

/** What the Search screen shows: the field's text, the body under it, and a one-shot snackbar. */
data class SearchUIState(
    /** The field's text, exactly as typed. */
    val query: String = "",
    val body: SearchBody = SearchBody.Prompt,
    /** A failure to show in a snackbar while results stay on screen. Cleared once shown. */
    val notice: SearchNotice? = null,
)

/** The body under the field. */
sealed interface SearchBody {
    /** Nothing searched yet: the query is blank. */
    data object Prompt : SearchBody

    /** A search is pending and no results are on screen. */
    data object Searching : SearchBody

    /** The answer for [query] (trimmed) has at least one section. */
    data class Results(val query: String, val sections: List<SearchSection>) : SearchBody

    /** The answer for [query] (trimmed) has no items. */
    data class NoMatches(val query: String) : SearchBody

    /** The search failed with nothing on screen to keep. */
    data class Failed(val failure: SearchFailure) : SearchBody

    /** The session ended or no server is saved: render nothing, the app is leaving for sign-in. */
    data object Ended : SearchBody
}

/** One section of the results, with the matches it holds. */
data class SearchSection(val kind: SearchSectionKind, val matches: SearchMatches<*>)

/** A failure to tell the reader about in a snackbar. [id] tells a new notice from one already shown. */
data class SearchNotice(val id: Long, val failure: SearchFailure)

/** The matches of [kind]. One branch per kind; never add an `else`. */
internal fun LibrarySearchResults.section(kind: SearchSectionKind): SearchMatches<*> = when (kind) {
    SearchSectionKind.Books -> books
}

/** The non-empty sections, in [SearchSectionKind] order. */
internal fun LibrarySearchResults.sections(): List<SearchSection> =
    SearchSectionKind.entries.map { SearchSection(it, section(it)) }.filter { it.matches.items.isNotEmpty() }

/**
 * Drives the Search tab: searches the library as the reader types, after a short pause, and drops answers for queries the
 * reader has moved past. Results on screen stay until a newer search answers.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: LibrarySearchRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUIState())

    /** The Search screen's state. */
    val uiState: StateFlow<SearchUIState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    /** Bumped whenever a search is cancelled or scheduled, so an answer for an older search is dropped. */
    private var searchToken = 0L

    /** The trimmed query of the pending or last search, so an edit that leaves it unchanged sends nothing. */
    private var scheduledQuery: String? = null
    private var nextNoticeId = 0L

    /** The last re-selection count seen. Kept here, not in a remember, so it survives Book Details being pushed and popped. */
    private var seenReselections: Int? = null

    /** The field's text changed to [text]. A blank query shows the Prompt and sends nothing. */
    fun updateQuery(text: String) {
        _uiState.update { it.copy(query = text) }
        val trimmed = text.trim()
        when {
            trimmed.isEmpty() -> {
                cancelSearch()
                scheduledQuery = null
                _uiState.update { it.copy(body = SearchBody.Prompt, notice = null) }
            }
            trimmed == scheduledQuery -> Unit
            else -> schedule(trimmed, debounce = true)
        }
    }

    /** The clear button: empties the field and returns to the Prompt. */
    fun clearQuery() = updateQuery("")

    /** "Try again" and the snackbar's "Retry": searches the current query at once, even if it was just searched. */
    fun retry() {
        val trimmed = _uiState.value.query.trim()
        if (trimmed.isNotEmpty()) schedule(trimmed, debounce = false)
    }

    /** The snackbar for the current notice has been shown, so it is not shown again. */
    fun noticeShown() = _uiState.update { it.copy(notice = null) }

    /** The Search tab was re-selected [count] times so far (C11). A change after the first call clears the query. */
    fun tabReselected(count: Int) {
        val seen = seenReselections
        seenReselections = count
        if (seen != null && seen != count) clearQuery()
    }

    private fun cancelSearch() {
        searchJob?.cancel()
        searchJob = null
        searchToken++
    }

    private fun schedule(query: String, debounce: Boolean) {
        cancelSearch()
        val token = searchToken
        scheduledQuery = query
        // Results already on screen stay until the newer search answers (R12).
        _uiState.update { if (it.body is SearchBody.Results) it else it.copy(body = SearchBody.Searching) }
        searchJob = viewModelScope.launch {
            if (debounce) delay(SEARCH_DEBOUNCE_MILLIS)
            val result = repository.searchLibrary(query)
            // The blocking OkHttp call can finish after cancellation, so the token is what drops a stale answer.
            if (token != searchToken) return@launch
            answer(query, result)
        }
    }

    private fun answer(query: String, result: PaletteSearchResult) {
        val noticeId = nextNoticeId++
        _uiState.update { state ->
            val showsResults = state.body is SearchBody.Results
            when (result) {
                is PaletteSearchResult.Success -> {
                    val sections = result.results.sections()
                    state.copy(
                        body = if (sections.isEmpty()) SearchBody.NoMatches(query) else SearchBody.Results(query, sections),
                        notice = null,
                    )
                }
                is PaletteSearchResult.Failure -> when (val failure = result.error.toSearchFailure()) {
                    null -> if (showsResults) state else state.copy(body = SearchBody.Ended)
                    else -> if (showsResults) {
                        state.copy(notice = SearchNotice(noticeId, failure))
                    } else {
                        state.copy(body = SearchBody.Failed(failure))
                    }
                }
                PaletteSearchResult.NoServer -> if (showsResults) state else state.copy(body = SearchBody.Ended)
            }
        }
    }
}
