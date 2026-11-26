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
import com.omnibus.omnibus.feature.libraries.displayName
import com.omnibus.omnibus.feature.libraries.monogramOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where a switch is. Null in [SwitchingUIState.stage] means it has not started yet, or an added library is dropping in.
 */
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
 * @property stage The stage, or null before the switch starts and while an added library's spine drops in.
 * @property current The active library when the switch started, or null when none is.
 * @property target The library being switched to, or null until it is read.
 * @property left Set once the reader has stayed on the current library, so the screen should close.
 * @property opened Set once the app has opened on the target library, so the screen should hand over to the shell.
 * @property bookcase The shelf of spines and what it is doing.
 */
data class SwitchingUIState(
    val stage: SwitchingStage? = null,
    val current: Library? = null,
    val target: Library? = null,
    val left: Boolean = false,
    val opened: Boolean = false,
    val bookcase: BookcaseState = BookcaseState(emptyList(), null, BookcaseTransition.Still),
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

    // #371
    /** Every library on the shelf, in list order, as the switch started. An added library drops onto its end. */
    private var shelf: List<Library> = emptyList()

    /** The screen's state. */
    val uiState: StateFlow<SwitchingUIState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (route.atOpening) {
                open()
            } else {
                when (route.kind) {
                    SwitchKind.Switch -> switch()
                    // #371
                    SwitchKind.Add -> add()
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
        if (_uiState.value.stage == SwitchingStage.SyncFailed) viewModelScope.launch { lift() }
    }

    private suspend fun switch() {
        val all = libraries.getLibrariesFlow().first()
        val target = all.firstOrNull { it.id == route.toLibraryId }
        if (target == null) {
            _uiState.update { it.copy(left = true) }
            return
        }
        val current = all.firstOrNull { library -> library.isActive }
        _uiState.update {
            it.copy(
                current = current,
                target = target,
                bookcase = bookcase(all, raisedId = current?.id, transition = BookcaseTransition.Still),
            )
        }

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

    // #371
    /**
     * The start of a switch to a library that was just added: [switch], except the new library's spine stays off the
     * shelf until it drops in, so syncing and Sync failed show the shelf as the reader left it.
     */
    private suspend fun add() {
        val all = libraries.getLibrariesFlow().first()
        shelf = all
        val target = all.firstOrNull { it.id == route.toLibraryId }
        if (target == null) {
            _uiState.update { it.copy(left = true) }
            return
        }
        val current = all.firstOrNull { library -> library.isActive }
        _uiState.update {
            it.copy(
                current = current,
                target = target,
                bookcase = bookcase(
                    all.filterNot { library -> library.id == target.id },
                    raisedId = current?.id,
                    transition = BookcaseTransition.Still,
                ),
            )
        }

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

    // #371
    /**
     * Drops the added library's spine onto its slot at the end of the shelf, from above, and waits for it to land. The
     * stage is null meanwhile, so the screen keeps the current library's name with no progress bar.
     */
    private suspend fun dropIn() {
        _uiState.update {
            it.copy(
                stage = null,
                bookcase = bookcase(
                    shelf,
                    raisedId = it.current?.id,
                    transition = BookcaseTransition.DropIn(route.toLibraryId),
                ),
            )
        }
        delay(BOOKCASE_DROP_IN)
    }

    /**
     * Lifts the new spine for [BookcaseGeometry.LIFT], then opens on the target and commits the switch. The wait runs on
     * [viewModelScope]; the commit runs on [persistScope], so once it has started, clearing this view model cannot stop it.
     */
    private suspend fun lift() {
        // #371
        if (route.kind == SwitchKind.Add) dropIn()
        val from = _uiState.value.current?.id
        val transition = if (from == null) BookcaseTransition.Still else BookcaseTransition.Lift(from, route.toLibraryId)
        _uiState.update {
            it.copy(
                stage = SwitchingStage.Lifting,
                bookcase = it.bookcase.copy(raisedLibraryId = route.toLibraryId, transition = transition),
            )
        }
        delay(BookcaseGeometry.LIFT)
        _uiState.update {
            it.copy(
                stage = SwitchingStage.Opening,
                bookcase = it.bookcase.copy(transition = BookcaseTransition.Still),
            )
        }
        persistScope.launch { librarySwitch.commit(route.toLibraryId, route.kind) }
    }

    /** After the restart: waits for the target library and the session, then clears the pending opening. */
    private suspend fun open() {
        _uiState.update { it.copy(stage = SwitchingStage.Opening) }
        val all = libraries.getLibrariesFlow()
            .first { list -> list.any { it.id == route.toLibraryId } }
        val target = all.first { it.id == route.toLibraryId }
        _uiState.update {
            it.copy(
                target = target,
                bookcase = bookcase(all, raisedId = target.id, transition = BookcaseTransition.Still),
            )
        }
        auth.isSignedIn.first()
        librarySwitch.clearPendingOpening()
        _uiState.update { it.copy(opened = true) }
    }

    /** The shelf for [libraries] in list order, with [raisedId] raised. */
    private fun bookcase(libraries: List<Library>, raisedId: String?, transition: BookcaseTransition) = BookcaseState(
        spines = libraries.map { BookcaseSpine(it.id, monogramOf(it.displayName()), SpineHeights.of(it.id)) },
        raisedLibraryId = raisedId,
        transition = transition,
    )
}
