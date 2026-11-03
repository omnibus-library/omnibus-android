package com.omnibus.omnibus.feature.bookdetail.overview.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.household.HouseholdReader
import com.omnibus.omnibus.data.household.HouseholdRepository
import com.omnibus.omnibus.data.household.HouseholdResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Loads who else in the household has read one book. The list is empty until the first answer, and a failed or
 * unknown-book answer leaves the last list in place, so the section stays out of the way of the page.
 *
 * @property bookUuid The book this section shows.
 */
@HiltViewModel(assistedFactory = HouseholdActivityViewModel.Factory::class)
class HouseholdActivityViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val householdRepository: HouseholdRepository,
) : ViewModel() {

    /** Creates a [HouseholdActivityViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): HouseholdActivityViewModel
    }

    private val _readers = MutableStateFlow<List<HouseholdReader>>(emptyList())

    /** The readers to list. Empty means the section renders nothing. */
    val readers: StateFlow<List<HouseholdReader>> = _readers

    /** The reload key of the last load, so re-entering composition with the same key does nothing. */
    private var lastLoadedKey: Int? = null

    /** Loads the household once for each new [reloadKey]. */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        viewModelScope.launch {
            when (val result = householdRepository.getHousehold(bookUuid)) {
                is HouseholdResult.Success -> _readers.value = result.readers.filter { it.seconds > 0 }
                HouseholdResult.NotFound, HouseholdResult.Failed -> Unit
            }
        }
    }
}
