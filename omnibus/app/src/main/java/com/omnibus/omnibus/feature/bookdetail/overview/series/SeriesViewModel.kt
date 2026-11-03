package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.data.series.SeriesRepository
import com.omnibus.omnibus.data.series.SeriesResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the series fan and the stage line show. [members] is null until a load succeeds, so the
 * section and the line both render nothing while loading or after a failed first load.
 */
data class SeriesUIState(val members: List<SeriesMember>? = null)

/**
 * Loads one series for the Book Detail page. The stage line and the Series section share one
 * instance, so a page makes one request per reload key.
 *
 * @property seriesId The series this ViewModel reads.
 */
@HiltViewModel(assistedFactory = SeriesViewModel.Factory::class)
class SeriesViewModel @AssistedInject constructor(
    @Assisted private val seriesId: Long,
    private val seriesRepository: SeriesRepository,
) : ViewModel() {

    /** Creates a [SeriesViewModel] for the series with [seriesId]. */
    @AssistedFactory
    interface Factory {
        fun create(seriesId: Long): SeriesViewModel
    }

    private val _uiState = MutableStateFlow(SeriesUIState())

    /** The members once a load has succeeded. */
    val uiState: StateFlow<SeriesUIState> = _uiState.asStateFlow()

    private var lastLoadedKey: Int? = null
    private var loadJob: Job? = null

    /**
     * Fetches the series for [reloadKey] unless that key was already requested. A newer key cancels a
     * load still in flight. A failed load keeps whatever is shown: nothing on a first load, the last
     * good members on a refresh.
     */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val result = seriesRepository.getSeries(seriesId)
            if (result is SeriesResult.Success) _uiState.value = SeriesUIState(result.members)
        }
    }
}
