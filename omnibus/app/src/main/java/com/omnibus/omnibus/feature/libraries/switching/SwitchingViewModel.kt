package com.omnibus.omnibus.feature.libraries.switching

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.LibrarySwitch
import com.omnibus.omnibus.data.libraries.LibrarySync
import com.omnibus.omnibus.data.libraries.LibrarySyncResult
import com.omnibus.omnibus.data.libraries.SwitchKind
import com.omnibus.omnibus.di.ApplicationScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where a switch is. Null in [SwitchingUIState.stage] means it has not started yet. */
enum class SwitchingStage {
    /** Sending the current library's pending changes. */
    Syncing,

    /** The sync failed or timed out; the reader chooses to stay or to switch anyway. */
    SyncFailed,

    /** Leaving the current library: the switch is being committed. */
    Lifting,

    /** The app is restarting for the target library, or reopening on it. */
    Opening,
}

/**
 * What the switching screen shows.
 *
 * @property stage The stage, or null before the switch starts.
 * @property current The active library when the switch started, or null when none is.
 * @property target The library being switched to, or null until it is read.
 * @property left Set once the reader has stayed on the current library, so the screen should close.
 * @property opened Set once the app has opened on the target library, so the screen should hand over to the shell.
 */
data class SwitchingUIState(
    val stage: SwitchingStage? = null,
    val current: Library? = null,
    val target: Library? = null,
    val left: Boolean = false,
    val opened: Boolean = false,
)

/**
 * Runs a switch from the sheet, or the Opening stage after a restart. A switch syncs the current library (when it has
 * unsent changes), then commits it. The commit runs on [persistScope], so leaving the screen cannot cancel it.
 *
 * @property persistScope Runs the commit, so it finishes even after this view model is cleared.
 */
@HiltViewModel(assistedFactory = SwitchingViewModel.Factory::class)
class SwitchingViewModel @AssistedInject constructor(
    @Assisted private val route: SwitchingRoute,
    private val libraries: LibrariesRepository,
    private val sync: LibrarySync,
    private val librarySwitch: LibrarySwitch,
    private val auth: AuthRepository,
    @param:ApplicationScope private val persistScope: CoroutineScope,
) : ViewModel() {

    /** Creates the view model for one switching route. */
    @AssistedFactory
    interface Factory {
        fun create(route: SwitchingRoute): SwitchingViewModel
    }

    private val _uiState = MutableStateFlow(SwitchingUIState())

    /** The screen's state. */
    val uiState: StateFlow<SwitchingUIState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (route.atOpening) {
                open()
            } else {
                when (route.kind) {
                    SwitchKind.Switch -> switch()
                }
            }
        }
    }

    /** The user chose to stay on the current library. Only offered from [SwitchingStage.SyncFailed]. */
    fun onStay() {
        if (_uiState.value.stage == SwitchingStage.SyncFailed) {
            _uiState.update { it.copy(left = true) }
        }
    }

    /** The user chose to switch with the changes unsent. Only offered from [SwitchingStage.SyncFailed]; never syncs again. */
    fun onSwitchAnyway() {
        if (_uiState.value.stage == SwitchingStage.SyncFailed) lift()
    }

    private suspend fun switch() {
        val all = libraries.getLibrariesFlow().first()
        val target = all.firstOrNull { it.id == route.toLibraryId }
        if (target == null) {
            _uiState.update { it.copy(left = true) }
            return
        }
        _uiState.update { it.copy(current = all.firstOrNull { library -> library.isActive }, target = target) }

        if (!sync.hasUnsyncedChanges()) {
            lift()
            return
        }
        _uiState.update { it.copy(stage = SwitchingStage.Syncing) }
        when (sync.syncActive()) {
            LibrarySyncResult.Synced -> lift()
            LibrarySyncResult.Failed -> _uiState.update { it.copy(stage = SwitchingStage.SyncFailed) }
        }
    }

    private fun lift() {
        _uiState.update { it.copy(stage = SwitchingStage.Lifting) }
        _uiState.update { it.copy(stage = SwitchingStage.Opening) }
        persistScope.launch { librarySwitch.commit(route.toLibraryId, route.kind) }
    }

    /** After the restart: waits for the target library and the session, then clears the pending opening. */
    private suspend fun open() {
        _uiState.update { it.copy(stage = SwitchingStage.Opening) }
        val target = libraries.getLibrariesFlow()
            .map { list -> list.firstOrNull { it.id == route.toLibraryId } }
            .filterNotNull()
            .first()
        _uiState.update { it.copy(target = target) }
        auth.isSignedIn.first()
        librarySwitch.clearPendingOpening()
        _uiState.update { it.copy(opened = true) }
    }
}
