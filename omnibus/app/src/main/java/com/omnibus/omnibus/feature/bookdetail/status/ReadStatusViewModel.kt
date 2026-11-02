package com.omnibus.omnibus.feature.bookdetail.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusError
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.ReadStatusWriteResult
import com.omnibus.omnibus.di.TimeSource
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the pill and menu show. [status] is null while the status is unknown or the last read failed, so the pill reads
 * "Reading status". [finishedAtMillis] is set only for a known Finished status. [nowMillis] is the clock when the state
 * was mapped, for formatting the date.
 */
data class ReadStatusUIState(
    val status: ReadStatus? = null,
    val finishedAtMillis: Long? = null,
    val nowMillis: Long = 0L,
    val saveFailure: ReadStatusSaveFailure? = null,
)

/** Why a choice could not be saved, as the page words it. */
sealed interface ReadStatusSaveFailure {
    /** No connection or no server, so the write never reached the server. */
    data object Offline : ReadStatusSaveFailure

    /** Any other failure. */
    data object Other : ReadStatusSaveFailure
}

/**
 * Owns the read status for one book on the Book Detail page. The repository holds the shared state and does the
 * optimistic write and rollback. This ViewModel skips a choice of the current status, forwards the rest, and turns a
 * failed write into a one-shot message.
 *
 * @property bookUuid The book this section shows.
 */
@HiltViewModel(assistedFactory = ReadStatusViewModel.Factory::class)
class ReadStatusViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val readStatusRepository: ReadStatusRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    /** Creates a [ReadStatusViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ReadStatusViewModel
    }

    private val saveFailure = MutableStateFlow<ReadStatusSaveFailure?>(null)

    /** The section's state. It is kept while something collects it. */
    val uiState: StateFlow<ReadStatusUIState> = combine(
        readStatusRepository.getReadStatusFlow(bookUuid),
        saveFailure,
    ) { state, failure ->
        val record = (state as? ReadStatusState.Known)?.record
        ReadStatusUIState(
            status = record?.status,
            finishedAtMillis = record?.finishedAtMillis?.takeIf { record.status == ReadStatus.Finished },
            nowMillis = timeSource.nowMillis(),
            saveFailure = failure,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReadStatusUIState())

    /** The reload key of the last refresh, so re-entering composition with the same key does nothing. */
    private var lastLoadedKey: Int? = null

    /** Refreshes the status once for each new [reloadKey]. */
    fun reload(reloadKey: Int) {
        if (reloadKey == lastLoadedKey) return
        lastLoadedKey = reloadKey
        viewModelScope.launch { readStatusRepository.refresh(bookUuid) }
    }

    /** Saves [status], unless it is the status already shown. */
    fun selectStatus(status: ReadStatus) {
        if (uiState.value.status == status) return
        viewModelScope.launch {
            when (val result = readStatusRepository.setStatus(bookUuid, status)) {
                is ReadStatusWriteResult.Failed -> saveFailure.update { result.error.toUi() }
                else -> Unit
            }
        }
    }

    /** Clears the failure after its message has been shown. A later failure shows again. */
    fun saveFailureShown() {
        saveFailure.value = null
    }
}

/** The page's wording for a failed write: no connection, or anything else. */
internal fun ReadStatusError.toUi(): ReadStatusSaveFailure = when (this) {
    ReadStatusError.NoServer, ReadStatusError.Unreachable -> ReadStatusSaveFailure.Offline
    else -> ReadStatusSaveFailure.Other
}
