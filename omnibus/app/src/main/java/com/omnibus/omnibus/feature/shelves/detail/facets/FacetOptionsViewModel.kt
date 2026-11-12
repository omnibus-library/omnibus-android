package com.omnibus.omnibus.feature.shelves.detail.facets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.authors.AuthorListResult
import com.omnibus.omnibus.data.authors.AuthorsRepository
import com.omnibus.omnibus.data.library.LibraryCategory
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.ShelfPreviewsResult
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.MetadataRepository
import com.omnibus.omnibus.data.metadata.VocabularyEntry
import com.omnibus.omnibus.data.metadata.VocabularyResult
import com.omnibus.omnibus.data.series.SeriesListResult
import com.omnibus.omnibus.data.series.SeriesRepository
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
 * The values one facet can be set to, read when its sheet opens. Tags and genres come from the library's vocabulary,
 * authors and series from their lists, shelves from the shelves, and formats are fixed.
 */
@HiltViewModel(assistedFactory = FacetOptionsViewModel.Factory::class)
class FacetOptionsViewModel @AssistedInject constructor(
    @Assisted private val field: FilterField,
    private val metadataRepository: MetadataRepository,
    private val authorsRepository: AuthorsRepository,
    private val seriesRepository: SeriesRepository,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    /** Creates the view model for one facet. */
    @AssistedFactory
    interface Factory {
        fun create(field: FilterField): FacetOptionsViewModel
    }

    private val _uiState = MutableStateFlow<FacetOptionsState>(FacetOptionsState.Loading)

    /** The facet's options, or that they are loading or failed to load. */
    val uiState: StateFlow<FacetOptionsState> = _uiState.asStateFlow()

    init {
        load()
    }

    /** Reads the options again after a failure. */
    fun retry() {
        _uiState.value = FacetOptionsState.Loading
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = readOptions()
        }
    }

    private suspend fun readOptions(): FacetOptionsState = when (field) {
        FilterField.Tag -> vocabulary(MetadataListKind.Tags)
        FilterField.Genre -> vocabulary(MetadataListKind.Genres)
        FilterField.Author -> when (val result = authorsRepository.getAuthors()) {
            is AuthorListResult.Success -> loaded(result.entries)
            else -> FacetOptionsState.Failed
        }
        FilterField.Series -> when (val result = seriesRepository.getSeriesList()) {
            is SeriesListResult.Success -> loaded(result.entries)
            else -> FacetOptionsState.Failed
        }
        FilterField.Shelf -> when (val result = libraryRepository.getShelfPreviewsFlow().last()) {
            is ShelfPreviewsResult.Success -> FacetOptionsState.Loaded(
                result.previews.map { FacetOption(value = it.shelf.id.toString(), label = it.shelf.name, count = it.shelf.bookCount) },
            )
            else -> FacetOptionsState.Failed
        }
        FilterField.Format -> FacetOptionsState.Loaded(
            (LibraryCategory.Ebooks.formats + LibraryCategory.Audiobooks.formats).map {
                FacetOption(value = it, label = it.uppercase(), count = null)
            },
        )
    }

    private suspend fun vocabulary(kind: MetadataListKind): FacetOptionsState =
        when (val result = metadataRepository.getVocabulary(kind)) {
            is VocabularyResult.Success -> loaded(result.entries)
            else -> FacetOptionsState.Failed
        }

    private fun loaded(entries: List<VocabularyEntry>): FacetOptionsState.Loaded =
        FacetOptionsState.Loaded(entries.map { FacetOption(value = it.name, label = it.name, count = it.count) })
}

/** One value a facet can be set to: the value sent to the server, its label, and how many books carry it. */
data class FacetOption(val value: String, val label: String, val count: Long?)

/** What a facet's sheet shows for its options. */
sealed interface FacetOptionsState {

    data object Loading : FacetOptionsState

    data class Loaded(val options: List<FacetOption>) : FacetOptionsState

    data object Failed : FacetOptionsState
}
