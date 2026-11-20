package com.omnibus.omnibus.feature.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.scan.ProviderRecord
import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.data.scan.ScanRepository
import com.omnibus.omnibus.data.scan.ScanResult
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.data.search.LibrarySearchRepository
import com.omnibus.omnibus.data.search.LibrarySearchResult
import com.omnibus.omnibus.data.wishlist.WishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.data.wishlist.WishlistState
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
import kotlinx.coroutines.flow.combine
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
    private val wishlistRepository: WishlistRepository,
    private val librarySearchRepository: LibrarySearchRepository,
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

    /** Bumped by every title or library search and by every change of stage, so a search answer that arrives late is dropped. */
    private var searchToken = 0L
    private var noticeJob: Job? = null

    /** Watches which candidates of the close match on screen are on the wishlist. */
    private var candidatesJob: Job? = null

    /** The library search of the picker, after its debounce. */
    private var pickerJob: Job? = null

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
        show(ScanStage.Viewfinder())
    }

    /** Repeats the lookup that failed: a picked search result is matched again, and an ISBN is looked up again. */
    fun tryAgainTapped() {
        val failure = (_uiState.value.stage as? ScanStage.Viewfinder)?.lookupFailed ?: return
        val record = failure.record
        if (record != null) {
            lookUpRecord(record)
        } else {
            lookUp(failure.isbn, failure.via, haptic = false)
        }
    }

    /** Turns the torch on or off. */
    fun torchToggled() {
        _uiState.update { it.copy(torchOn = !it.torchOn) }
    }

    /** Adds the provider's record as a physical book, with its first copy checked in. A second tap while saving does nothing. */
    fun addPhysicalTapped() {
        val stage = _uiState.value.stage as? ScanStage.Outcome ?: return
        val record = (stage.outcome as? ScanOutcome.NotInLibrary)?.online ?: return
        if (stage.writing != null) return
        _uiState.update { state -> state.copy(stage = stage.copy(writing = WritingAction.Physical, writeFailed = false)) }
        viewModelScope.launch {
            when (val result = scanRepository.addPhysicalOnly(record)) {
                is ScanResult.Success -> {
                    _uiState.update { state ->
                        state.copy(stage = ScanStage.Success(record.title, SuccessCover.Provider(record), result.value, SuccessKind.CheckedIn))
                    }
                    _effects.trySend(ScanEffect.Confirm)
                }
                else -> _uiState.update { state ->
                    val current = state.stage
                    if (current is ScanStage.Outcome && current.writing == WritingAction.Physical) {
                        state.copy(stage = current.copy(writing = null, writeFailed = true))
                    } else {
                        state
                    }
                }
            }
        }
    }

    /** Puts the provider's record on the reader's wishlist, from a camera read or a typed ISBN. A second tap while saving does nothing. */
    fun addToWishlistTapped() {
        val stage = _uiState.value.stage as? ScanStage.Outcome ?: return
        val record = (stage.outcome as? ScanOutcome.NotInLibrary)?.online ?: return
        if (stage.writing != null) return
        _uiState.update { state -> state.copy(stage = stage.copy(writing = WritingAction.Wishlist, writeFailed = false)) }
        viewModelScope.launch {
            when (val result = scanRepository.addToWishlist(record, stage.via.toWishlistSource())) {
                is ScanResult.Success -> {
                    _uiState.update { state ->
                        state.copy(stage = ScanStage.Success(record.title, SuccessCover.Provider(record), result.value, SuccessKind.Wishlisted))
                    }
                    _effects.trySend(ScanEffect.Confirm)
                }
                else -> _uiState.update { state ->
                    val current = state.stage
                    if (current is ScanStage.Outcome && current.writing == WritingAction.Wishlist) {
                        state.copy(stage = current.copy(writing = null, writeFailed = true))
                    } else {
                        state
                    }
                }
            }
        }
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

    /** Opens the title search from the camera, or from an unresolved ISBN, carrying its inline title into the search. */
    fun searchByTitleTapped() {
        val stage = _uiState.value.stage
        val draft = (stage as? ScanStage.Outcome)?.takeIf { it.outcome is ScanOutcome.Unresolved }?.titleDraft.orEmpty()
        show(ScanStage.TitleSearch(query = draft))
        if (draft.isNotBlank()) titleSearchSubmitted()
    }

    /** Replaces the title being searched for, or the inline title of an unresolved ISBN. */
    fun titleQueryChanged(query: String) {
        when (val stage = _uiState.value.stage) {
            is ScanStage.TitleSearch -> {
                searchToken++
                _uiState.update { it.copy(stage = stage.copy(query = query, loading = false)) }
            }

            is ScanStage.Outcome -> if (stage.outcome is ScanOutcome.Unresolved) {
                _uiState.update { it.copy(stage = stage.copy(titleDraft = query)) }
            }

            else -> Unit
        }
    }

    /** Searches the providers for the trimmed title. A blank title does nothing, and an answer that arrives late is dropped. */
    fun titleSearchSubmitted() {
        val stage = _uiState.value.stage as? ScanStage.TitleSearch ?: return
        val query = stage.query.trim()
        if (query.isEmpty()) return
        val token = ++searchToken
        _uiState.update { it.copy(stage = stage.copy(loading = true, failed = false)) }
        viewModelScope.launch {
            val result = scanRepository.searchByTitle(query)
            if (token != searchToken) return@launch
            _uiState.update { state ->
                val current = state.stage
                if (current !is ScanStage.TitleSearch) {
                    state
                } else {
                    state.copy(
                        stage = when (result) {
                            is ScanResult.Success -> current.copy(results = result.value, loading = false, failed = false)
                            else -> current.copy(results = null, loading = false, failed = true)
                        },
                    )
                }
            }
        }
    }

    /** Matches a title-search result the reader picked, and opens the outcome a scan would. */
    fun searchResultPicked(record: ProviderRecord) {
        if (_uiState.value.stage !is ScanStage.TitleSearch) return
        lookUpRecord(record)
    }

    /** Checks the candidate the reader picked in: a wishlisted one goes through the wishlist confirm, any other the library one. */
    fun candidatePicked(book: ScanBook) {
        val stage = _uiState.value.stage as? ScanStage.Outcome ?: return
        val closeMatch = stage.outcome as? ScanOutcome.CloseMatch ?: return
        if (stage.writing != null) return
        val outcome = if (book.uuid in stage.wishlistedCandidates) {
            ScanOutcome.OnWishlist(book)
        } else {
            ScanOutcome.InLibraryUnowned(book)
        }
        show(ScanStage.Outcome(outcome, closeMatch.scanned.isbn13, stage.via))
    }

    /** Says the close match isn't the book, which offers adding the scanned record as a new physical book. */
    fun closeMatchDeclined() {
        val stage = _uiState.value.stage as? ScanStage.Outcome ?: return
        val closeMatch = stage.outcome as? ScanOutcome.CloseMatch ?: return
        if (stage.writing != null) return
        show(ScanStage.Outcome(ScanOutcome.NotInLibrary(closeMatch.scanned), stage.isbn, stage.via, declined = true))
    }

    /** Opens the library picker, to say which library book the ISBN in hand is. Back returns to this outcome. */
    fun alreadyHaveTapped() {
        val stage = _uiState.value.stage as? ScanStage.Outcome ?: return
        if (stage.writing != null) return
        when (stage.outcome) {
            is ScanOutcome.CloseMatch, is ScanOutcome.NotInLibrary, ScanOutcome.Unresolved -> show(ScanStage.LibraryPicker(returnTo = stage))
            else -> Unit
        }
    }

    /** Replaces the picker's query. A blank query clears the hits; otherwise the library is searched after a short pause. */
    fun libraryQueryChanged(query: String) {
        val stage = _uiState.value.stage as? ScanStage.LibraryPicker ?: return
        val token = ++searchToken
        pickerJob?.cancel()
        val text = query.trim()
        if (text.isEmpty()) {
            _uiState.update { it.copy(stage = stage.copy(query = query, hits = emptyList(), total = 0, loading = false, failed = false)) }
            return
        }
        _uiState.update { it.copy(stage = stage.copy(query = query, loading = false)) }
        pickerJob = viewModelScope.launch {
            delay(PICKER_DEBOUNCE_MILLIS)
            _uiState.update { state ->
                val current = state.stage
                if (token == searchToken && current is ScanStage.LibraryPicker) {
                    state.copy(stage = current.copy(loading = true, failed = false))
                } else {
                    state
                }
            }
            val result = librarySearchRepository.searchBooks(text)
            if (token != searchToken) return@launch
            _uiState.update { state ->
                val current = state.stage
                if (current !is ScanStage.LibraryPicker) {
                    state
                } else {
                    state.copy(
                        stage = when (result) {
                            is LibrarySearchResult.Success -> current.copy(
                                hits = result.matches.books.take(PICKER_HIT_LIMIT),
                                total = result.matches.total,
                                loading = false,
                            )
                            else -> current.copy(hits = emptyList(), total = 0, loading = false, failed = true)
                        },
                    )
                }
            }
        }
    }

    /** Checks the ISBN in hand under the library book the reader picked, which is shown as an ordinary copy to check in. */
    fun libraryHitPicked(hit: LibraryHit) {
        val picker = _uiState.value.stage as? ScanStage.LibraryPicker ?: return
        show(ScanStage.Outcome(ScanOutcome.InLibraryUnowned(hit.toScanBook()), picker.returnTo.isbn, picker.returnTo.via))
    }

    /** Returns to the camera from an outcome or a success. Ignored while a save is in flight. */
    fun scanAnotherTapped() {
        returnToCamera()
    }

    /** Goes back one step: from a picker to the outcome it came from, from a title search or an outcome to the camera. Ignored while a save is in flight. */
    fun backTapped() {
        val stage = _uiState.value.stage
        if (stage is ScanStage.LibraryPicker) {
            show(stage.returnTo)
            watchCandidates(stage.returnTo.outcome)
        } else {
            returnToCamera()
        }
    }

    private fun returnToCamera() {
        val stage = _uiState.value.stage
        if (stage is ScanStage.Outcome && stage.writing != null) return
        requestToken++
        noticeJob?.cancel()
        show(ScanStage.Viewfinder())
    }

    /**
     * Shows [stage] and drops every answer still in flight for the stage before it: a search, a picker query or the
     * candidates' wishlist watch.
     */
    private fun show(stage: ScanStage) {
        searchToken++
        pickerJob?.cancel()
        candidatesJob?.cancel()
        _uiState.update { it.copy(stage = stage) }
    }

    /** Starts a lookup of [isbn]. Its answer is applied only if no newer lookup or leave has happened since. */
    private fun lookUp(isbn: String, via: FoundVia, haptic: Boolean) {
        startLookup(isbn, via, haptic, record = null) { scanRepository.resolve(isbn) }
    }

    /** Starts the lookup of a title-search result the reader picked. The record is matched without a second provider lookup. */
    private fun lookUpRecord(record: ProviderRecord) {
        startLookup(record.isbn13, FoundVia.Search, haptic = false, record = record) { scanRepository.resolveRecord(record) }
    }

    /**
     * Shows the lookup of [isbn] in flight, then the outcome [answer] gives, or the failure banner. A lookup that a newer
     * one or a leave has overtaken is dropped.
     */
    private fun startLookup(
        isbn: String,
        via: FoundVia,
        haptic: Boolean,
        record: ProviderRecord?,
        answer: suspend () -> ScanResult<ScanOutcome>,
    ) {
        noticeJob?.cancel()
        val token = ++requestToken
        show(ScanStage.Resolving(isbn))
        if (haptic) _effects.trySend(ScanEffect.Confirm)
        viewModelScope.launch {
            val result = answer()
            if (token != requestToken) return@launch
            when (result) {
                is ScanResult.Success -> {
                    show(ScanStage.Outcome(result.value, isbn, via))
                    watchCandidates(result.value)
                }

                else -> show(ScanStage.Viewfinder(lookupFailed = LookupFailure(isbn, via, record)))
            }
        }
    }

    /** Keeps [ScanStage.Outcome.wishlistedCandidates] in step with the wishlist, while [outcome] is a close match on screen. */
    private fun watchCandidates(outcome: ScanOutcome) {
        candidatesJob?.cancel()
        if (outcome !is ScanOutcome.CloseMatch) return
        candidatesJob = viewModelScope.launch {
            combine(outcome.candidates.map { wishlistRepository.getWishlistFlow(it.uuid) }) { states ->
                outcome.candidates.filterIndexed { index, _ -> states[index] is WishlistState.Present }.map { it.uuid }.toSet()
            }.collect { wishlisted ->
                _uiState.update { state ->
                    val current = state.stage as? ScanStage.Outcome
                    if (current?.outcome == outcome) state.copy(stage = current.copy(wishlistedCandidates = wishlisted)) else state
                }
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

        /** How long the picker waits after the last keystroke before it searches. */
        const val PICKER_DEBOUNCE_MILLIS = 250L

        /** The most library books the picker lists. */
        const val PICKER_HIT_LIMIT = 5
    }
}

/** The wishlist source of a write made from [FoundVia]: a camera read, a typed ISBN or a title-search pick. */
private fun FoundVia.toWishlistSource(): WishlistSource = when (this) {
    FoundVia.Scan -> WishlistSource.Scan
    FoundVia.Manual -> WishlistSource.Manual
    FoundVia.Search -> WishlistSource.Search
}

/** A library book as a copy to check in: no ISBN of its own is known, and it has files when its formats say so. */
private fun LibraryHit.toScanBook(): ScanBook = ScanBook(
    uuid = uuid,
    title = title,
    authors = listOfNotNull(authorDisplay.takeIf { it.isNotBlank() }),
    coverUrl = coverUrl,
    hasPhysical = false,
    hasFiles = formats.isNotEmpty(),
    isbn = null,
)
