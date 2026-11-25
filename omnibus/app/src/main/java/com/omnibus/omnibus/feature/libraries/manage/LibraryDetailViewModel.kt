package com.omnibus.omnibus.feature.libraries.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.LibraryHost
import com.omnibus.omnibus.di.ApplicationScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What a library's detail screen shows.
 *
 * @property library The library, or null until the list has been read.
 * @property name The name field's current value.
 * @property isMissing Whether the list was read and has no such library, so the screen should leave.
 */
data class LibraryDetailUIState(
    val library: Library? = null,
    val name: String = "",
    val isMissing: Boolean = false,
) {
    /** The title: the field trimmed, or the host (with its port) when that is blank. Empty until the library is read. */
    val title: String
        get() = name.trim().ifBlank { library?.let { LibraryHost.of(it.address) }.orEmpty() }
}

/**
 * Owns one library's detail screen: its name field, saved when the reader leaves. Saving runs on the application
 * scope, so a save outlives the screen.
 *
 * @property persistScope Runs the saves, so a save finishes even after this view model is cleared.
 */
@HiltViewModel(assistedFactory = LibraryDetailViewModel.Factory::class)
class LibraryDetailViewModel @AssistedInject constructor(
    @Assisted private val libraryId: String,
    private val libraries: LibrariesRepository,
    @param:ApplicationScope private val persistScope: CoroutineScope,
) : ViewModel() {

    /** Creates the view model for one library. */
    @AssistedFactory
    interface Factory {
        fun create(libraryId: String): LibraryDetailViewModel
    }

    private val _uiState = MutableStateFlow(LibraryDetailUIState())

    /** The screen's state. */
    val uiState: StateFlow<LibraryDetailUIState> = _uiState.asStateFlow()

    /** The name as stored, or as last saved. Null until the library is first read. */
    private var savedName: String? = null

    init {
        viewModelScope.launch {
            // A failed read keeps what is shown; only a readable list without the library marks it missing.
            libraries.getLibrariesFlow().catch { }.collect { list ->
                val library = list.firstOrNull { it.id == libraryId }
                if (library == null) {
                    _uiState.update { it.copy(isMissing = true) }
                    return@collect
                }
                if (savedName == null) {
                    savedName = library.name
                    _uiState.update { it.copy(library = library, name = library.name) }
                } else {
                    // Never overwrite what the reader typed.
                    _uiState.update { it.copy(library = library) }
                }
            }
        }
    }

    /** Sets the name field, cut to the name limit. */
    fun nameChanged(name: String) {
        _uiState.update { it.copy(name = name.capLibraryName()) }
    }

    /** Saves the name field when it differs from the stored name. Repeated calls with no change do nothing. */
    fun save() {
        val stored = savedName ?: return
        val state = _uiState.value
        if (state.isMissing) return
        val cleaned = state.name.trim().capLibraryName()
        if (cleaned == stored) return
        savedName = cleaned
        persistScope.launch { libraries.rename(libraryId, cleaned) }
    }

    override fun onCleared() = save()
}
