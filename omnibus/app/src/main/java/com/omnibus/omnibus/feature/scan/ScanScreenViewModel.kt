package com.omnibus.omnibus.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.data.scan.ScanRepository
import com.omnibus.omnibus.data.scan.ScanResult
import com.omnibus.omnibus.di.TimeSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the scanner's stage and drives the scan repository. Each intent is named for what the reader did. A write
 * ignores every other intent until it settles, so a double tap saves once.
 */
@HiltViewModel
class ScanScreenViewModel @Inject constructor(
    private val scanRepository: ScanRepository,
    private val timeSource: TimeSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUIState())

    /** The screen's state. */
    val uiState: StateFlow<ScanUIState> = _uiState.asStateFlow()

    private val _effects = Channel<ScanEffect>(Channel.BUFFERED)

    /** One-shot effects, such as the haptic. */
    val effects: Flow<ScanEffect> = _effects.receiveAsFlow()

    private val gate = BarcodeReadGate()

    /** Bumped by every lookup and by leaving a stage, so an answer that arrives late is dropped. */
    private var requestToken = 0L
    private var noticeJob: Job? = null

    /** Feeds the barcodes of one analysed frame. A book barcode starts a lookup; anything else shows a notice. */
    fun barcodesSeen(values: List<String>) {
        val events = gate.onFrame(values, timeSource.nowMillis())
        if (_uiState.value.stage !is ScanStage.Viewfinder) return
        val lookup = events.filterIsInstance<GateEvent.Lookup>().firstOrNull()
        if (lookup != null) {
            lookUp(lookup.isbn, FoundVia.Scan, haptic = true)
            return
        }
        events.filterIsInstance<GateEvent.Rejected>().lastOrNull()?.let { showNotice(RejectedNotice(it.value, it.announce)) }
    }

    /** Opens the typed-ISBN stage. */
    fun enterIsbnTapped() {
        _uiState.update { it.copy(stage = ScanStage.Manual()) }
    }

    /** Replaces the typed ISBN, while the manual stage is shown. */
    fun isbnChanged(text: String) {
        _uiState.update { state ->
            val stage = state.stage
            if (stage is ScanStage.Manual) state.copy(stage = stage.copy(text = text)) else state
        }
    }

    /** Looks up the typed ISBN, or shows its error when it isn't valid. */
    fun findBookTapped() {
        val stage = _uiState.value.stage as? ScanStage.Manual ?: return
        if (stage.canFind) {
            lookUp(stage.cleaned, FoundVia.Manual, haptic = false)
        } else {
            _uiState.update { state -> state.copy(stage = stage.copy(submitted = true)) }
        }
    }

    /** Leaves the typed-ISBN stage for the camera. */
    fun scanInsteadTapped() {
        _uiState.update { it.copy(stage = ScanStage.Viewfinder()) }
    }

    /** Repeats the lookup that failed. */
    fun tryAgainTapped() {
        val failure = (_uiState.value.stage as? ScanStage.Viewfinder)?.lookupFailed ?: return
        lookUp(failure.isbn, failure.via, haptic = false)
    }

    /** Turns the torch on or off. */
    fun torchToggled() {
        _uiState.update { it.copy(torchOn = !it.torchOn) }
    }

    /** Replaces the edition note of an outcome, unless a save is in flight. */
    fun noteChanged(note: String) {
        _uiState.update { state ->
            val stage = state.stage
            if (stage is ScanStage.Outcome && stage.writing == null) state.copy(stage = stage.copy(note = note)) else state
        }
    }

    /** Checks a copy of the outcome's book in. A second tap while the save is in flight does nothing. */
    fun checkInTapped() {
        val stage = _uiState.value.stage as? ScanStage.Outcome ?: return
        if (stage.writing != null) return
        val outcome = stage.outcome
        val book = when (outcome) {
            is ScanOutcome.InLibraryUnowned -> outcome.book
            is ScanOutcome.OnWishlist -> outcome.book
            else -> return
        }
        _uiState.update { state -> state.copy(stage = stage.copy(writing = WritingAction.CheckIn, writeFailed = false)) }
        val note = stage.note.trim().ifBlank { null }
        viewModelScope.launch {
            when (val result = scanRepository.checkIn(book.uuid, stage.isbn, note)) {
                is ScanResult.Success -> {
                    val kind = if (outcome is ScanOutcome.OnWishlist) SuccessKind.OffWishlist else SuccessKind.CheckedIn
                    _uiState.update { state ->
                        state.copy(stage = ScanStage.Success(book.title, SuccessCover.Library(book), result.value, kind))
                    }
                    _effects.trySend(ScanEffect.Confirm)
                }
                else -> _uiState.update { state ->
                    val current = state.stage
                    if (current is ScanStage.Outcome && current.writing == WritingAction.CheckIn) {
                        state.copy(stage = current.copy(writing = null, writeFailed = true))
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Returns to the camera from an outcome or a success. Ignored while a save is in flight. */
    fun scanAnotherTapped() {
        returnToCamera()
    }

    /** Returns to the camera from an outcome, as the back arrow does. Ignored while a save is in flight. */
    fun backTapped() {
        returnToCamera()
    }

    private fun returnToCamera() {
        val stage = _uiState.value.stage
        if (stage is ScanStage.Outcome && stage.writing != null) return
        requestToken++
        noticeJob?.cancel()
        _uiState.update { it.copy(stage = ScanStage.Viewfinder()) }
    }

    /** Starts a lookup of [isbn]. Its answer is applied only if no newer lookup or leave has happened since. */
    private fun lookUp(isbn: String, via: FoundVia, haptic: Boolean) {
        noticeJob?.cancel()
        val token = ++requestToken
        _uiState.update { it.copy(stage = ScanStage.Resolving(isbn)) }
        if (haptic) _effects.trySend(ScanEffect.Confirm)
        viewModelScope.launch {
            val result = scanRepository.resolve(isbn)
            if (token != requestToken) return@launch
            _uiState.update { state ->
                state.copy(
                    stage = when (result) {
                        is ScanResult.Success -> ScanStage.Outcome(result.value, isbn, via)
                        else -> ScanStage.Viewfinder(lookupFailed = LookupFailure(isbn, via))
                    },
                )
            }
        }
    }

    /** Shows [notice] on the camera for [NOTICE_MILLIS], unless a newer notice or a lookup replaces it first. */
    private fun showNotice(notice: RejectedNotice) {
        noticeJob?.cancel()
        _uiState.update { state ->
            val stage = state.stage
            if (stage is ScanStage.Viewfinder) state.copy(stage = stage.copy(rejected = notice)) else state
        }
        noticeJob = viewModelScope.launch {
            delay(NOTICE_MILLIS)
            _uiState.update { state ->
                val stage = state.stage
                if (stage is ScanStage.Viewfinder && stage.rejected == notice) {
                    state.copy(stage = stage.copy(rejected = null))
                } else {
                    state
                }
            }
        }
    }

    private companion object {
        /** How long a not-an-ISBN notice stays on the camera. */
        const val NOTICE_MILLIS = 3_000L
    }
}
