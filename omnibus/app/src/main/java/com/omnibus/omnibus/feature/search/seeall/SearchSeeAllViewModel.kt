package com.omnibus.omnibus.feature.search.seeall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.search.LibrarySearchRepository
import com.omnibus.omnibus.data.search.PaletteSearchResult
import com.omnibus.omnibus.data.search.SearchMatches
import com.omnibus.omnibus.feature.search.SearchFailure
import com.omnibus.omnibus.feature.search.section
import com.omnibus.omnibus.feature.search.toSearchFailure
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

/** Most rows a See all list shows: the server's largest `limit` (spec "See all cap"). */
const val SEE_ALL_CAP = 500

/** What the See all list shows. */
data class SearchSeeAllUIState(val body: SeeAllBody = SeeAllBody.Loading)

/** The body of a See all list. */
sealed interface SeeAllBody {
    /** The answer is on its way. */
    data object Loading : SeeAllBody

    /** [matches] holds at most [SEE_ALL_CAP] items; its total may be larger. */
    data class Loaded(val matches: SearchMatches<*>) : SeeAllBody

    /** The answer has no items for this section. */
    data object NoMatches : SeeAllBody

    /** The search failed. [failure] is what the reader is told. */
    data class Failed(val failure: SearchFailure) : SeeAllBody

    /** The session ended or no server is saved: render nothing, the app is leaving for sign-in. */
    data object Ended : SeeAllBody
}

/**
 * Loads every match of one section of a search for the See all list. It asks for up to [SEE_ALL_CAP] rows once on open,
 * and [retry] asks again.
 */
@HiltViewModel(assistedFactory = SearchSeeAllViewModel.Factory::class)
class SearchSeeAllViewModel @AssistedInject constructor(
    @Assisted private val route: SearchSeeAllRoute,
    private val repository: LibrarySearchRepository,
) : ViewModel() {

    /** Creates the view model for one See all route. */
    @AssistedFactory
    interface Factory {
        fun create(route: SearchSeeAllRoute): SearchSeeAllViewModel
    }

    private val _uiState = MutableStateFlow(SearchSeeAllUIState())

    /** The See all list's state. */
    val uiState: StateFlow<SearchSeeAllUIState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    /** "Try again": asks the server again for the same query and section. */
    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        _uiState.update { it.copy(body = SeeAllBody.Loading) }
        loadJob = viewModelScope.launch {
            val body = when (val result = repository.searchLibrary(route.query, limit = SEE_ALL_CAP)) {
                is PaletteSearchResult.Success -> {
                    val matches = result.results.section(route.section)
                    if (matches.items.isEmpty()) {
                        SeeAllBody.NoMatches
                    } else {
                        SeeAllBody.Loaded(SearchMatches(matches.items.take(SEE_ALL_CAP), matches.total))
                    }
                }
                is PaletteSearchResult.Failure -> result.error.toSearchFailure()
                    ?.let { SeeAllBody.Failed(it) }
                    ?: SeeAllBody.Ended
                PaletteSearchResult.NoServer -> SeeAllBody.Ended
            }
            _uiState.update { it.copy(body = body) }
        }
    }
}
