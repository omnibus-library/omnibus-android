package com.omnibus.omnibus.feature.libraries.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * What the Manage libraries list shows.
 *
 * @property libraries Every saved library, in the order they were added.
 */
data class ManageLibrariesUIState(val libraries: List<Library> = emptyList())

/** Owns the Manage libraries list. */
@HiltViewModel
class ManageLibrariesViewModel @Inject constructor(libraries: LibrariesRepository) : ViewModel() {

    /** The list's state. A list that can't be read shows no rows. */
    val uiState: StateFlow<ManageLibrariesUIState> = libraries.getLibrariesFlow()
        .map { list -> ManageLibrariesUIState(list.sortedBy { it.addedAt }) }
        .catch { emit(ManageLibrariesUIState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ManageLibrariesUIState())
}

private const val STOP_TIMEOUT_MS = 5_000L
