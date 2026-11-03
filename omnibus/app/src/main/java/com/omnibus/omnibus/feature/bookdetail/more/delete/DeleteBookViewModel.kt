package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.deletion.DeleteItemsResult
import com.omnibus.omnibus.data.deletion.DeletionError
import com.omnibus.omnibus.data.deletion.DeletionManifest
import com.omnibus.omnibus.data.deletion.DeletionManifestResult
import com.omnibus.omnibus.data.deletion.DeletionRepository
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The delete sheet's step. [Closed] means no sheet. [LoadFailed] offers a retry. */
sealed interface DeleteSheet {
    data object Closed : DeleteSheet

    /** The manifest is being read. */
    data object Loading : DeleteSheet

    /** The manifest could not be read. */
    data object LoadFailed : DeleteSheet

    /** The reader picks files and copies to delete. */
    data class Choosing(val manifest: DeletionManifest) : DeleteSheet

    /** The reader has picked, and confirms the delete. A book with nothing to pick opens here. */
    data class Confirming(val manifest: DeletionManifest) : DeleteSheet
}

/** Why a confirmed delete failed. [Unreachable] reads differently from the rest. */
enum class DeleteFailure { Unreachable, Other }

/**
 * A finished delete, reported once. [eventId] increases with each delete, so the page reports each one once.
 *
 * @property bookDeleted True when the server also deleted the book, so the page leaves.
 * @property deletedFiles The number of files the server deleted.
 * @property deletedCopies The number of physical copies the server un-recorded.
 */
data class DeleteOutcome(val eventId: Long, val bookDeleted: Boolean, val deletedFiles: Int, val deletedCopies: Int)

/**
 * The delete sheet's state for one book.
 *
 * @property isAdmin Whether the signed-in user is an admin. Only an admin sees the row.
 * @property isOnline Whether the device is online. Offline, the row is disabled.
 * @property pickedFiles The file ids picked in the choose step.
 * @property pickedCopies The copy ids picked in the choose step.
 * @property deleting True while a delete request is in flight.
 * @property deleteError Why the last delete failed, shown inline on the confirm step.
 * @property outcome The latest finished delete. Cleared by [DeleteBookViewModel.outcomeShown].
 */
data class DeleteBookUIState(
    val isAdmin: Boolean = false,
    val isOnline: Boolean = true,
    val sheet: DeleteSheet = DeleteSheet.Closed,
    val pickedFiles: Set<Long> = emptySet(),
    val pickedCopies: Set<Long> = emptySet(),
    val deleting: Boolean = false,
    val deleteError: DeleteFailure? = null,
    val outcome: DeleteOutcome? = null,
)

/**
 * Owns the Delete entry's sheet for one book: reading the manifest, the picks, the confirmation, and the delete. The
 * state lives here, so it survives a configuration change. The delete runs in [viewModelScope], and the repository runs
 * the request in its own scope, so leaving the page does not cancel it.
 *
 * @property bookUuid The book the delete is for.
 */
@HiltViewModel(assistedFactory = DeleteBookViewModel.Factory::class)
class DeleteBookViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val repository: DeletionRepository,
    authRepository: AuthRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeleteBookUIState())

    /** The row and sheet state. It changes only through the intents below. */
    val uiState: StateFlow<DeleteBookUIState> = _uiState.asStateFlow()

    private var nextEventId = 1L

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user -> _uiState.update { it.copy(isAdmin = user?.isAdmin == true) } }
        }
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    /**
     * Opens the sheet and reads the manifest. Ignored unless the row is enabled, so a stale tap cannot open it. Every
     * open reads the manifest again and starts with nothing picked.
     *
     * @param hasFiles Whether the book has files, which picks the row's label.
     */
    fun openSheet(hasFiles: Boolean) {
        val current = _uiState.value
        if (deleteRow(current.isAdmin, current.isOnline, hasFiles) !is DeleteRow.Enabled) return
        _uiState.update {
            it.copy(pickedFiles = emptySet(), pickedCopies = emptySet(), deleting = false, deleteError = null)
        }
        load()
    }

    /** Reads the manifest again after a failed read. Ignored from any other step. */
    fun retryLoad() {
        if (_uiState.value.sheet != DeleteSheet.LoadFailed) return
        load()
    }

    /** Picks or un-picks a file. Ignored outside the choose step, and for an id that is not in the manifest. */
    fun toggleFile(id: Long) {
        _uiState.update { state ->
            val manifest = (state.sheet as? DeleteSheet.Choosing)?.manifest ?: return@update state
            if (manifest.files.none { it.id == id }) return@update state
            state.copy(pickedFiles = state.pickedFiles.toggled(id))
        }
    }

    /** Picks or un-picks a physical copy. Ignored outside the choose step, and for an id that is not in the manifest. */
    fun toggleCopy(id: Long) {
        _uiState.update { state ->
            val manifest = (state.sheet as? DeleteSheet.Choosing)?.manifest ?: return@update state
            if (manifest.copies.none { it.id == id }) return@update state
            state.copy(pickedCopies = state.pickedCopies.toggled(id))
        }
    }

    /** Picks everything, or clears every pick when everything is already picked. Ignored outside the choose step. */
    fun toggleAll() {
        _uiState.update { state ->
            val manifest = (state.sheet as? DeleteSheet.Choosing)?.manifest ?: return@update state
            val allFiles = manifest.files.map { it.id }.toSet()
            val allCopies = manifest.copies.map { it.id }.toSet()
            val everything = state.pickedFiles.containsAll(allFiles) && state.pickedCopies.containsAll(allCopies)
            if (everything) {
                state.copy(pickedFiles = emptySet(), pickedCopies = emptySet())
            } else {
                state.copy(pickedFiles = allFiles, pickedCopies = allCopies)
            }
        }
    }

    /** Moves to the confirm step, but only with something picked. */
    fun continueToConfirm() {
        _uiState.update { state ->
            val choosing = state.sheet as? DeleteSheet.Choosing ?: return@update state
            if (state.pickedFiles.isEmpty() && state.pickedCopies.isEmpty()) return@update state
            state.copy(sheet = DeleteSheet.Confirming(choosing.manifest))
        }
    }

    /**
     * Goes back from the confirm step to the choose step, with the picks kept. A book with nothing to pick has no choose
     * step, so it closes instead. Ignored while a delete is in flight.
     */
    fun back() {
        _uiState.update { state ->
            val confirming = state.sheet as? DeleteSheet.Confirming ?: return@update state
            if (state.deleting) return@update state
            if (confirming.manifest.itemCount == 0) {
                closed(state)
            } else {
                state.copy(sheet = DeleteSheet.Choosing(confirming.manifest), deleteError = null)
            }
        }
    }

    /** Closes the sheet and clears the picks. Ignored while a delete is in flight, so the sheet cannot be dismissed. */
    fun closeSheet() {
        _uiState.update { state -> if (state.deleting) state else closed(state) }
    }

    /**
     * Deletes the picked files and copies. Ignored outside the confirm step, and while a delete is in flight, so a second
     * tap sends nothing. A success closes the sheet and reports its outcome. A failure keeps the sheet and the picks.
     */
    fun confirmDelete() {
        val current = _uiState.value
        if (current.deleting || current.sheet !is DeleteSheet.Confirming) return
        val files = current.pickedFiles
        val copies = current.pickedCopies
        _uiState.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            when (val result = repository.deleteItems(bookUuid, files, copies)) {
                is DeleteItemsResult.Deleted -> {
                    val id = nextEventId++
                    _uiState.update {
                        closed(it).copy(
                            deleting = false,
                            outcome = DeleteOutcome(
                                eventId = id,
                                bookDeleted = result.bookDeleted,
                                deletedFiles = result.deletedFileIds.size,
                                deletedCopies = result.deletedCopyIds.size,
                            ),
                        )
                    }
                }
                is DeleteItemsResult.Failed -> _uiState.update {
                    it.copy(deleting = false, deleteError = failureOf(result.error))
                }
            }
        }
    }

    /** Clears [DeleteBookUIState.outcome] if it is the outcome [eventId]. A newer outcome stays. */
    fun outcomeShown(eventId: Long) {
        _uiState.update { if (it.outcome?.eventId == eventId) it.copy(outcome = null) else it }
    }

    /** Sets the sheet to Loading and reads the manifest. The result is dropped if the sheet was closed meanwhile. */
    private fun load() {
        _uiState.update { it.copy(sheet = DeleteSheet.Loading) }
        viewModelScope.launch {
            val result = repository.fetchManifest(bookUuid)
            _uiState.update { state ->
                if (state.sheet != DeleteSheet.Loading) return@update state
                when (result) {
                    is DeletionManifestResult.Success -> {
                        val manifest = result.manifest
                        val sheet = if (opensOnConfirm(manifest)) {
                            DeleteSheet.Confirming(manifest)
                        } else {
                            DeleteSheet.Choosing(manifest)
                        }
                        state.copy(sheet = sheet)
                    }
                    is DeletionManifestResult.Failed -> state.copy(sheet = DeleteSheet.LoadFailed)
                }
            }
        }
    }

    /** Creates a [DeleteBookViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): DeleteBookViewModel
    }
}

/** The state with the sheet closed and nothing picked. */
private fun closed(state: DeleteBookUIState): DeleteBookUIState =
    state.copy(sheet = DeleteSheet.Closed, pickedFiles = emptySet(), pickedCopies = emptySet(), deleteError = null)

private fun Set<Long>.toggled(id: Long): Set<Long> = if (id in this) this - id else this + id

/** The inline failure for a delete error. Only an unreachable server reads differently. */
private fun failureOf(error: DeletionError): DeleteFailure =
    if (error == DeletionError.Unreachable) DeleteFailure.Unreachable else DeleteFailure.Other
