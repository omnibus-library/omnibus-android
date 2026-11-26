package com.omnibus.omnibus.feature.libraries.manage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.LibraryHost
import com.omnibus.omnibus.data.libraries.LibraryRemoval
import com.omnibus.omnibus.data.libraries.LibrarySyncResult
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.feature.libraries.remove.RemoveLibraryState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.coroutines.cancellation.CancellationException
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
 * @property removeState Where removing the library has got to.
 */
data class LibraryDetailUIState(
    val library: Library? = null,
    val name: String = "",
    val isMissing: Boolean = false,
    val removeState: RemoveLibraryState = RemoveLibraryState.Hidden,
) {
    /** Whether Remove library is offered. The active library can't be removed here yet (#374). */
    val canRemove: Boolean
        get() = library != null && !library.isActive

    /** The title: the field trimmed, or the host (with its port) when that is blank. Empty until the library is read. */
    val title: String
        get() = name.trim().ifBlank { library?.let { LibraryHost.of(it.address) }.orEmpty() }
}

/**
 * Owns one library's detail screen: its name field, saved when the reader leaves. Saving runs on the application
 * scope, so a save outlives the screen.
 *
 * @property persistScope Runs the saves and a removal, so they finish even after this view model is cleared.
 */
@HiltViewModel(assistedFactory = LibraryDetailViewModel.Factory::class)
class LibraryDetailViewModel @AssistedInject constructor(
    @Assisted private val libraryId: String,
    private val libraries: LibrariesRepository,
    @param:ApplicationScope private val persistScope: CoroutineScope,
    private val removal: LibraryRemoval,
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

    /** Opens the confirm dialog, for a library that can be removed. */
    fun removeClicked() {
        val state = _uiState.value
        if (!state.canRemove || state.removeState != RemoveLibraryState.Hidden) return
        setRemoveState(RemoveLibraryState.Confirming)
    }

    /** Closes the confirm or failure dialog without removing anything. Ignored while a removal runs. */
    fun cancelRemove() {
        val state = _uiState.value.removeState
        if (state == RemoveLibraryState.Confirming || state == RemoveLibraryState.SyncFailed) {
            setRemoveState(RemoveLibraryState.Hidden)
        }
    }

    /** Syncs the library for the confirmed removal, then removes it, or offers Remove anyway when the sync fails. */
    fun confirmRemove() {
        if (_uiState.value.removeState != RemoveLibraryState.Confirming) return
        setRemoveState(RemoveLibraryState.Syncing)
        persistScope.launch {
            when (removal.sync(libraryId)) {
                LibrarySyncResult.Synced -> remove()
                LibrarySyncResult.Failed -> setRemoveState(RemoveLibraryState.SyncFailed)
            }
        }
    }

    /** Removes the library without its sync, after the sync failed and the reader chose to. */
    fun removeAnyway() {
        if (_uiState.value.removeState != RemoveLibraryState.SyncFailed) return
        setRemoveState(RemoveLibraryState.RemovingAnyway)
        persistScope.launch { remove() }
    }

    /** Removes the library. The libraries flow then drops it, and the screen leaves. A failure closes the dialog. */
    private suspend fun remove() {
        try {
            removal.remove(libraryId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            setRemoveState(RemoveLibraryState.Hidden)
        }
    }

    private fun setRemoveState(state: RemoveLibraryState) {
        _uiState.update { it.copy(removeState = state) }
    }

    override fun onCleared() = save()
}
