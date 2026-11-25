package com.omnibus.omnibus.feature.libraries.switcher

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
 * What the switcher sheet shows.
 *
 * @property libraries Every saved library, in the order they were added. The open one is the one with
 * [Library.isActive], and it keeps its place in the list.
 */
data class LibrarySwitcherUIState(val libraries: List<Library> = emptyList())

/** Owns the switcher sheet's list of libraries. */
@HiltViewModel
class LibrarySwitcherViewModel @Inject constructor(libraries: LibrariesRepository) : ViewModel() {

    /** The sheet's state. A list that can't be read shows no rows. */
    val uiState: StateFlow<LibrarySwitcherUIState> = libraries.getLibrariesFlow()
        .map { list -> LibrarySwitcherUIState(list.sortedBy { it.addedAt }) }
        .catch { emit(LibrarySwitcherUIState()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibrarySwitcherUIState())
}

private const val STOP_TIMEOUT_MS = 5_000L
