package com.omnibus.omnibus.feature.shelves.detail.facets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.ShelfPreviewsResult
import com.omnibus.omnibus.data.shelves.FilterField
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch

/**
 * The values one facet can be set to. Tags, genres, formats, authors and series are those the shelf's own [books]
 * carry, so the sheet never offers a value that would match nothing; shelves are read from the shelves, when the
 * sheet opens.
 */
@HiltViewModel(assistedFactory = FacetOptionsViewModel.Factory::class)
class FacetOptionsViewModel @AssistedInject constructor(
    @Assisted private val field: FilterField,
    @Assisted books: List<Book>,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    /** Creates the view model for one facet of a shelf with [books]. */
    @AssistedFactory
    interface Factory {
        fun create(field: FilterField, books: List<Book>): FacetOptionsViewModel
    }

    private val _uiState = MutableStateFlow<FacetOptionsState>(
        if (field == FilterField.Shelf) FacetOptionsState.Loading else FacetOptionsState.Loaded(shelfFacetOptions(field, books)),
    )

    /** The facet's options, or that they are loading or failed to load. */
    val uiState: StateFlow<FacetOptionsState> = _uiState.asStateFlow()

    init {
        if (field == FilterField.Shelf) load()
    }

    /** Reads the options again after a failure. */
    fun retry() {
        if (field != FilterField.Shelf) return
        _uiState.value = FacetOptionsState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = when (val result = libraryRepository.getShelfPreviewsFlow().last()) {
                is ShelfPreviewsResult.Success -> FacetOptionsState.Loaded(
                    result.previews.map { FacetOption(value = it.shelf.id.toString(), label = it.shelf.name, count = it.shelf.bookCount) },
                )
                else -> FacetOptionsState.Failed
            }
        }
    }
}

/** One value a facet can be set to: the value sent to the server, its label, and how many books carry it. */
data class FacetOption(val value: String, val label: String, val count: Long?)

/** What a facet's sheet shows for its options. */
sealed interface FacetOptionsState {

    data object Loading : FacetOptionsState

    data class Loaded(val options: List<FacetOption>) : FacetOptionsState

    data object Failed : FacetOptionsState
}
