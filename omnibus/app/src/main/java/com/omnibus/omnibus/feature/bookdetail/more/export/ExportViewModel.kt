package com.omnibus.omnibus.feature.bookdetail.more.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.export.ExportRepository
import com.omnibus.omnibus.data.export.ExportSaveResult
import com.omnibus.omnibus.data.export.ExportedFile
import com.omnibus.omnibus.data.export.KindleSendResult
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Why a download failed, for the sheet's error line. */
enum class ExportSaveError { Forbidden, NotFound, Unreachable, NoSpace, Failed }

/**
 * Files a finished download asks the page to share.
 *
 * @property eventId Increases with each request, so the page shares each one once.
 */
data class ExportShareRequest(val eventId: Long, val files: List<ExportedFile>)

/**
 * The Export sheet's state for one book.
 *
 * @property user The signed-in user, or null while it loads.
 * @property isOnline Whether the device is online.
 * @property saving The download that is running, if any.
 * @property saveError The latest failed download, cleared when the next one starts.
 * @property share The files of the latest finished download, for the page to hand to the share sheet.
 */
data class ExportUIState(
    val user: CurrentUser? = null,
    val isOnline: Boolean = true,
    val sheetOpen: Boolean = false,
    val kindle: KindlePhase = KindlePhase.Idle,
    val saving: ExportSave? = null,
    val saveError: ExportSaveError? = null,
    val share: ExportShareRequest? = null,
)

/**
 * Owns the Export sheet for one book: the Kindle send, the three downloads and their results. A send or download
 * runs in [viewModelScope], so it outlives a closed sheet, except that closing the sheet cancels a download.
 *
 * @property bookUuid The book the sheet is for.
 */
@HiltViewModel(assistedFactory = ExportViewModel.Factory::class)
class ExportViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val exportRepository: ExportRepository,
    authRepository: AuthRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExportUIState())

    /** The sheet's state. It changes only through the intents below and the sends and downloads they start. */
    val uiState: StateFlow<ExportUIState> = _uiState.asStateFlow()

    private var saveJob: Job? = null
    private var nextEventId = 1L

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user -> _uiState.update { it.copy(user = user) } }
        }
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    /** Opens the sheet. A send still in flight keeps showing; any other phase and the last error start afresh. */
    fun openSheet() {
        _uiState.update {
            it.copy(
                sheetOpen = true,
                saveError = null,
                kindle = if (it.kindle == KindlePhase.Sending) KindlePhase.Sending else KindlePhase.Idle,
            )
        }
    }

    /** Closes the sheet and cancels a download in flight. Ignored while a Kindle send is in flight. */
    fun closeSheet() {
        if (_uiState.value.kindle == KindlePhase.Sending) return
        saveJob?.cancel()
        saveJob = null
        _uiState.update { it.copy(sheetOpen = false, saving = null) }
    }

    /** Closes the sheet, as the Kindle row's Settings action does. The page navigates to Settings itself. */
    fun kindleSettingsTapped() {
        closeSheet()
    }

    /** Sends the book to the Kindle. Ignored unless the device is online and no send is sent or in flight. */
    fun sendToKindle() {
        val state = _uiState.value
        val canSend = when (state.kindle) {
            KindlePhase.Idle, is KindlePhase.Failed -> true
            KindlePhase.Sending, KindlePhase.Sent -> false
        }
        if (!state.isOnline || !canSend) return
        _uiState.update { it.copy(kindle = KindlePhase.Sending) }
        viewModelScope.launch {
            val phase = exportRepository.sendToKindle(bookUuid).toPhase()
            _uiState.update { it.copy(kindle = phase) }
        }
    }

    /**
     * Downloads the Kobo KEPUB, the ebook or the audiobook of [book], as [kind] names. Ignored while another download runs
     * or the device is offline, and when the book has no file for [kind].
     */
    fun save(kind: ExportSave, book: Book) {
        val state = _uiState.value
        if (state.saving != null || !state.isOnline) return
        val title = book.displayTitle
        when (kind) {
            ExportSave.Kobo -> startSave(kind) { exportRepository.saveKepub(bookUuid, title) }
            ExportSave.Ebook -> ebookTarget(book)?.let { target ->
                startSave(kind) {
                    exportRepository.saveEbook(bookUuid, title, target.fallbackExtension, target.sizeBytes)
                }
            }
            ExportSave.Audio -> audioTarget(book)?.let { target ->
                startSave(kind) {
                    exportRepository.saveAudiobook(bookUuid, title, target.fallbackExtension, target.sizeBytes)
                }
            }
        }
    }

    /** Clears [ExportUIState.share] if it is the request [eventId]. A newer request stays. */
    fun shareShown(eventId: Long) {
        _uiState.update { if (it.share?.eventId == eventId) it.copy(share = null) else it }
    }

    /** Reports that the share sheet could not be opened, as a failed download. */
    fun shareFailed() {
        _uiState.update { it.copy(saveError = ExportSaveError.Failed) }
    }

    /** Marks [kind] as saving and runs [download]. A finished download either requests a share or sets the error. */
    private fun startSave(kind: ExportSave, download: suspend () -> ExportSaveResult) {
        _uiState.update { it.copy(saving = kind, saveError = null) }
        saveJob = viewModelScope.launch {
            when (val result = download()) {
                is ExportSaveResult.Saved -> {
                    val request = ExportShareRequest(nextEventId++, result.files)
                    _uiState.update { it.copy(saving = null, share = request) }
                }
                ExportSaveResult.Forbidden -> saveFailed(ExportSaveError.Forbidden)
                ExportSaveResult.NotFound -> saveFailed(ExportSaveError.NotFound)
                ExportSaveResult.Unreachable -> saveFailed(ExportSaveError.Unreachable)
                ExportSaveResult.NoSpace -> saveFailed(ExportSaveError.NoSpace)
                ExportSaveResult.Failed, ExportSaveResult.NoServer -> saveFailed(ExportSaveError.Failed)
            }
        }
    }

    private fun saveFailed(error: ExportSaveError) {
        _uiState.update { it.copy(saving = null, saveError = error) }
    }

    /** Creates an [ExportViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ExportViewModel
    }
}

/** Maps a send's outcome to the Kindle row's phase, keeping the server's message when it sent one. */
private fun KindleSendResult.toPhase(): KindlePhase = when (this) {
    KindleSendResult.Sent -> KindlePhase.Sent
    is KindleSendResult.Rejected -> KindlePhase.Failed(KindleFailure.Undelivered, message)
    KindleSendResult.Unconfirmed -> KindlePhase.Failed(KindleFailure.Unconfirmed)
    KindleSendResult.NoAddress -> KindlePhase.Failed(KindleFailure.NoAddress)
    KindleSendResult.NotConfigured -> KindlePhase.Failed(KindleFailure.NotConfigured)
    KindleSendResult.NotFound -> KindlePhase.Failed(KindleFailure.NotFound)
    KindleSendResult.Unreachable -> KindlePhase.Failed(KindleFailure.Unreachable)
    KindleSendResult.Failed, KindleSendResult.NoServer -> KindlePhase.Failed(KindleFailure.Unknown)
}
