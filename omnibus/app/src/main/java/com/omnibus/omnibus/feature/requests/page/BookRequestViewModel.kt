package com.omnibus.omnibus.feature.requests.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsRepository
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.Wanter
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.requests.RequestAction
import com.omnibus.omnibus.feature.requests.actionFailure
import com.omnibus.omnibus.feature.requests.canCancel
import com.omnibus.omnibus.feature.requests.detailPollIntervalMillis
import com.omnibus.omnibus.feature.requests.isCancellable
import com.omnibus.omnibus.feature.requests.isMoving
import com.omnibus.omnibus.feature.requests.kindLabel
import com.omnibus.omnibus.feature.requests.mainRequest
import com.omnibus.omnibus.feature.requests.di.RequestWordingSource
import com.omnibus.omnibus.feature.requests.offersRequestAgain as ruleOffersRequestAgain
import com.omnibus.omnibus.feature.requests.splitBookRequests
import com.omnibus.omnibus.feature.requests.startFailure
import com.omnibus.omnibus.feature.requests.undoDelayMillis
import com.omnibus.omnibus.feature.requests.unrequestedKinds
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max

/** A write in flight, naming the request and the button that started it. One runs at a time, so every other button locks. */
sealed interface Busy {
    data class Choosing(val request: Long, val version: Long) : Busy
    data class Swapping(val request: Long) : Busy
    data class Retrying(val request: Long) : Busy
    data class TakingCover(val request: Long) : Busy
    data class KeepingCover(val request: Long) : Busy
    data class Cancelling(val request: Long) : Busy
    data class Undoing(val request: Long) : Busy
    data class Starting(val kind: RequestKind) : Busy
}

/**
 * A short message over the page.
 *
 * @property undoRequestId Set only for a cancel: the request Undo restores.
 * @property durationMillis How long it stays. A cancel's lasts until the server stops taking it back.
 * @property id Numbers the message, so two with the same text each show.
 */
data class RequestMessage(
    val text: String,
    val undoRequestId: Long? = null,
    val durationMillis: Long = DEFAULT_MESSAGE_MILLIS,
    val id: Long = 0,
)

private const val DEFAULT_MESSAGE_MILLIS = 4_000L

/** Nobody signed in: may do nothing. */
internal val NobodyViewer = RequestViewer(id = 0, canStart = false, canChoose = false)

/**
 * What the request page shows. A book's requests are lanes: the one the page opened on ([mainId]), then each other one
 * going or stuck. Only a single request's own read carries every version and any cover offer, so a lane waits for its
 * entry in [reads].
 *
 * @property wanters The readers with the book on a wishlist, or null when that read failed, so the line stays out.
 * @property reads The full read of each lane's request, by id.
 * @property mainId The request the page opened on. It stays the main lane while it is listed.
 * @property failure Why the first load failed. Only a failed first load shows it.
 * @property isMissing Whether the book itself is gone.
 * @property busy The write in flight, if any.
 * @property message What the page is saying, with an Undo when the write can be taken back.
 * @property isOnline Whether the device is online. Commands never queue, so every write control waits for it.
 * @property nowMillis The clock when this state was built, which dates the lanes' relative times.
 */
data class BookRequestUIState(
    val book: Book? = null,
    val wanters: List<Wanter>? = null,
    val listings: List<RequestListing>? = null,
    val reads: Map<Long, BookRequest> = emptyMap(),
    val mainId: Long? = null,
    val failure: RequestsError? = null,
    val isMissing: Boolean = false,
    val viewer: RequestViewer = NobodyViewer,
    val busy: Busy? = null,
    val message: RequestMessage? = null,
    val isOnline: Boolean = true,
    val nowMillis: Long = 0L,
) {

    /** Whether nothing has answered yet. */
    val isLoading: Boolean get() = book == null && failure == null && !isMissing

    private val laneIds: List<Long>
        get() {
            val main = mainId ?: return emptyList()
            val listed = listings ?: return emptyList()
            return listOf(main) + splitBookRequests(listed, main).lanes.map { it.request.id }
        }

    /** The main lane, then each other request going or stuck. One whose full read hasn't arrived waits. */
    val lanes: List<BookRequest> get() = laneIds.mapNotNull { reads[it] }

    /** The book's past requests: the cancelled ones and any that swapped a version. */
    val history: List<RequestListing>
        get() = listings?.let { splitBookRequests(it, mainId ?: 0).history }.orEmpty()

    /** The kinds the viewer may request that nothing yet covers. */
    val unrequested: List<RequestKind>
        get() {
            val listed = listings ?: return emptyList()
            val held = book ?: return emptyList()
            return unrequestedKinds(held.formats, listed, viewer)
        }

    /** Counts the listings too, so a lane whose full read failed keeps polling and its read is retried. */
    val pollInterval: Long?
        get() = detailPollIntervalMillis(lanes + laneIds.mapNotNull { id -> listings?.firstOrNull { it.request.id == id }?.request })

    /** Who asked for [lane], as the list names them. */
    fun listingFor(lane: BookRequest): RequestListing? = listings?.firstOrNull { it.request.id == lane.id }

    /** Whether Request again is offered on [request] now. */
    fun offersRequestAgain(request: BookRequest): Boolean =
        ruleOffersRequestAgain(request, viewer, listings.orEmpty().map { it.request }, nowMillis)

    /** When the next cancel's Undo window closes, so the page can read the clock again then. Null when none is open. */
    val nextWindowCloseMillis: Long?
        get() = (lanes + history.map { it.request })
            .filter { it.state == RequestState.Cancelled }
            .mapNotNull { it.cancelAt?.let { at -> at * MILLIS } }
            .filter { it > nowMillis }
            .minOrNull()
}

private const val MILLIS = 1_000L

/** How long past a window's close the page wakes, so it never lands a hair before it and finds the window still open. */
private const val WINDOW_WAKE_MARGIN_MILLIS = 250L

/**
 * Owns one book's request page: its lanes, its history, and the reads that keep them current. A poll that started
 * before a write is dropped when it answers after it, so a slow read can't undo what the reader just did.
 *
 * @property uuid The book the page is for.
 */
@HiltViewModel(assistedFactory = BookRequestViewModel.Factory::class)
class BookRequestViewModel @AssistedInject constructor(
    @Assisted private val uuid: String,
    private val requests: RequestsRepository,
    private val library: LibraryRepository,
    private val authRepository: AuthRepository,
    private val timeSource: TimeSource,
    connectivityMonitor: ConnectivityMonitor,
    private val wordingSource: RequestWordingSource,
) : ViewModel() {

    /** Creates a [BookRequestViewModel] for the book with [uuid]. */
    @AssistedFactory
    interface Factory {
        fun create(uuid: String): BookRequestViewModel
    }

    private val _uiState = MutableStateFlow(BookRequestUIState())

    /** The page's state. */
    val uiState: StateFlow<BookRequestUIState> = _uiState.asStateFlow()

    /** Numbers each write, so a read that started before one is dropped when it answers after it. */
    private var generation = 0

    private var nextMessageId = 1L

    init {
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> change { it.copy(isOnline = online) } }
        }
    }

    /** Reads the page. Called each time the page is shown, and by Retry and pull to refresh. */
    fun load() {
        viewModelScope.launch { loadPage() }
    }

    /**
     * Polls while any lane is moving. The screen runs it while the page is on screen, so polling stops when the entry
     * leaves. It waits while nothing moves and starts again when a lane does, and a failed first load doesn't end it.
     */
    suspend fun keepCurrent() = coroutineScope {
        launch { pollWhileMoving() }
        launch { wakeAtWindowCloses() }
    }

    private suspend fun pollWhileMoving() {
        uiState.map { it.pollInterval }.distinctUntilChanged().collectLatest { interval ->
            if (interval != null) {
                while (true) {
                    delay(interval)
                    poll()
                }
            }
        }
    }

    /** Reads the clock again at each Undo window's close, then arms for the next open window. */
    private suspend fun wakeAtWindowCloses() {
        uiState.map { it.nextWindowCloseMillis }.distinctUntilChanged().collectLatest { close ->
            if (close != null) {
                delay(max(0L, close - timeSource.nowMillis()) + WINDOW_WAKE_MARGIN_MILLIS)
                change { it }
            }
        }
    }

    private fun change(transform: (BookRequestUIState) -> BookRequestUIState) {
        _uiState.update { transform(it).copy(nowMillis = timeSource.nowMillis()) }
    }

    /**
     * Reads the book, its listings and wishers, then each lane's full request. A failure shows only while there is no
     * page yet, and a write that lands meanwhile keeps its answer.
     */
    private suspend fun loadPage() {
        val viewer = readViewer()
        val started = generation
        val hadPage = _uiState.value.let { it.book != null && it.failure == null }
        change { it.copy(viewer = viewer, isMissing = it.isMissing && hadPage, failure = it.failure.takeIf { hadPage }) }

        val (bookResult, listResult, wishResult) = coroutineScope {
            val book = async { library.getBookFlow(uuid).first() }
            val list = async { requests.bookList(uuid) }
            val wishers = async { requests.wanters(uuid) }
            Triple(book.await(), list.await(), wishers.await())
        }
        val found = (bookResult as? BookResult.Success)?.book
        val listed = (listResult as? RequestsResult.Success)?.value
        if (found == null || listed == null) {
            if (!hadPage) failFirstLoad(bookResult, listResult)
            return
        }
        change { it.copy(book = found, wanters = (wishResult as? RequestsResult.Success)?.value) }
        if (started != generation) return
        change { it.copy(listings = listed) }
        settleMain()

        val mainId = _uiState.value.mainId ?: return
        val others = splitBookRequests(listed, mainId).lanes.map { it.request.id }
        val (main, otherReads) = coroutineScope {
            val mainRead = async { requests.request(mainId) }
            val otherRead = async { readAll(others) }
            mainRead.await() to otherRead.await()
        }
        when (main) {
            is RequestsResult.Success -> {
                val answers = otherReads + (main.value.id to main.value)
                // A write that landed meanwhile keeps its answers; this read only fills the lanes it lacks.
                change { it.copy(reads = if (started == generation) answers else answers + it.reads) }
            }
            else -> if (!hadPage && mainId !in _uiState.value.reads) change { it.copy(failure = main.errorOrNull()) }
        }
    }

    /** Picks version [versionId] for the request [laneId]. */
    fun choose(laneId: Long, versionId: Long) {
        viewModelScope.launch {
            write(RequestAction.Choose, Busy.Choosing(laneId, versionId), allowed = _uiState.value.viewer.canChoose) {
                requests.choose(laneId, versionId)
            }
        }
    }

    /** Gives up the downloading version and goes back to choosing among the others. */
    fun swap(laneId: Long) {
        viewModelScope.launch {
            write(RequestAction.Swap, Busy.Swapping(laneId), allowed = _uiState.value.viewer.canChoose) { requests.swap(laneId) }
        }
    }

    /** Searches again for a stuck or stale request. */
    fun retry(laneId: Long) {
        viewModelScope.launch {
            write(RequestAction.Retry, Busy.Retrying(laneId), allowed = _uiState.value.viewer.canChoose) { requests.retry(laneId) }
        }
    }

    /** Makes the downloaded cover the book's own. */
    fun takeCover(laneId: Long) {
        viewModelScope.launch {
            write(RequestAction.TakeCover, Busy.TakingCover(laneId), allowed = _uiState.value.viewer.canChoose, rereading = laneId) {
                requests.takeCover(laneId)
            }
        }
    }

    /** Turns the downloaded cover down. */
    fun keepCover(laneId: Long) {
        viewModelScope.launch {
            write(RequestAction.KeepCover, Busy.KeepingCover(laneId), allowed = _uiState.value.viewer.canChoose, rereading = laneId) {
                requests.keepCover(laneId)
            }
        }
    }

    /** Cancels the request [laneId], then offers Undo for as long as the server will take it back. */
    fun cancel(laneId: Long) {
        viewModelScope.launch {
            val state = _uiState.value
            val lane = state.reads[laneId] ?: return@launch
            val allowed = canCancel(state.viewer, lane) && isCancellable(lane)
            val cancelled = write(
                RequestAction.Cancel, Busy.Cancelling(laneId), allowed, rereading = laneId, rereadsListings = false,
            ) { requests.cancel(laneId) } ?: return@launch
            undoDelayMillis(cancelled.cancelAt, timeSource.nowMillis())?.let { delay ->
                val text = wordingSource.wording().let { it.requestCancelled(kindLabel(cancelled.kind, it)) }
                change { it.copy(message = RequestMessage(text, cancelled.id, delay, nextMessageId++)) }
            }
            // Read once Undo is up and the controls are free, so a slow list can't eat the server's grace.
            rereadListings()
        }
    }

    /** Takes the open cancel back and shows whatever the server returns. A press while another write runs is kept for later. */
    fun undo() {
        viewModelScope.launch {
            val state = _uiState.value
            val id = state.message?.undoRequestId ?: return@launch
            if (state.busy != null || !state.isOnline) return@launch
            change { it.copy(message = null) }
            write(RequestAction.Undo, Busy.Undoing(id), allowed = true) { requests.undoCancel(id) }
        }
    }

    /** Starts a request of [kind] and stays on the page. The new lane arrives with the answer. */
    fun start(kind: RequestKind) {
        viewModelScope.launch {
            val start = { error: RequestsError -> startFailure(error, wordingSource.wording()) }
            write(start, Busy.Starting(kind), allowed = _uiState.value.viewer.canStart) { requests.start(uuid, kind) }
        }
    }

    /** Clears [message] once it has been shown for its duration, unless a newer one has replaced it. */
    fun messageDismissed(message: RequestMessage) {
        change { if (it.message?.id == message.id) it.copy(message = null) else it }
    }

    private suspend fun write(
        action: RequestAction,
        busy: Busy,
        allowed: Boolean,
        rereading: Long? = null,
        rereadsListings: Boolean = true,
        call: suspend () -> RequestsResult<BookRequest>,
    ): BookRequest? = write(
        { error -> actionFailure(action, error, wordingSource.wording()) },
        busy, allowed, rereading, rereadsListings, call,
    )

    /**
     * Runs one write: the request takes the server's answer and the listings are read again. A failure says so, and
     * where [rereading] names a request, the refusal may mean it moved on, so it is read again. The control that
     * started it is busy meanwhile and every other write waits. Answers what the server returned.
     */
    private suspend fun write(
        failure: (RequestsError) -> String,
        busy: Busy,
        allowed: Boolean,
        rereading: Long? = null,
        rereadsListings: Boolean = true,
        call: suspend () -> RequestsResult<BookRequest>,
    ): BookRequest? {
        val before = _uiState.value
        if (before.busy != null || !allowed || !before.isOnline) return null
        change { it.copy(busy = busy) }
        generation++
        try {
            when (val result = call()) {
                is RequestsResult.Success -> {
                    val answer = result.value
                    change { it.copy(reads = it.reads + (answer.id to answer)) }
                    adopt(answer)
                    if (rereadsListings) rereadListings()
                    return answer
                }
                else -> {
                    showFailure(failure(result.errorOrNull() ?: RequestsError.UnexpectedResponse))
                    if (rereading != null) {
                        (requests.request(rereading) as? RequestsResult.Success)?.let { fresh ->
                            change { it.copy(reads = it.reads + (rereading to fresh.value)) }
                        }
                        rereadListings()
                    }
                    return null
                }
            }
        } finally {
            change { it.copy(busy = null) }
            generation++
        }
    }

    /** Says a write failed. An open Undo rides along for the window that remains, since losing it loses the only way back. */
    private fun showFailure(text: String) {
        val state = _uiState.value
        val open = state.message?.undoRequestId
        val left = open?.let { undoDelayMillis(state.reads[it]?.cancelAt, timeSource.nowMillis()) ?: 0L }
        val message = if (open == null) {
            RequestMessage(text, id = nextMessageId++)
        } else {
            RequestMessage(text, open, left ?: 0L, nextMessageId++)
        }
        change { it.copy(message = message) }
    }

    /** Files a write's answer in the listings, so its lane and its kind hold even when the reread fails. */
    private fun adopt(answer: BookRequest) {
        val state = _uiState.value
        val listed = state.listings ?: return
        val book = state.book ?: return
        val at = listed.indexOfFirst { it.request.id == answer.id }
        // shortcut: the requester's name stays blank until the next listings read fills it.
        val updated = if (at >= 0) {
            listed.toMutableList().also { it[at] = it[at].copy(request = answer) }
        } else {
            listed + RequestListing(answer, book, requesterName = "", requesterHasAvatar = false, cancelledByName = null)
        }
        change { it.copy(listings = updated) }
        settleMain()
    }

    /** A write that lands meanwhile has the newer listings, so this read is dropped. */
    private suspend fun rereadListings() {
        val started = generation
        val fresh = (requests.bookList(uuid) as? RequestsResult.Success)?.value ?: return
        if (started != generation) return
        change { it.copy(listings = fresh) }
        settleMain()
    }

    private suspend fun readViewer(): RequestViewer {
        val user = authRepository.currentUser.catch { emit(null) }.first()
        return user?.let { RequestViewer(it.id, it.canStartRequests, it.canChooseRequestVersions) } ?: NobodyViewer
    }

    private fun failFirstLoad(book: BookResult, list: RequestsResult<List<RequestListing>>) {
        when {
            book is BookResult.NotFound -> change { it.copy(isMissing = true) }
            book is BookResult.NoServer -> change { it.copy(failure = RequestsError.Unreachable) }
            book is BookResult.Failure -> change { it.copy(failure = book.error.toRequestsError()) }
            else -> change { it.copy(failure = list.errorOrNull()) }
        }
    }

    /** Keeps the lane the page opened on while its request is listed; else the list's most urgent one. */
    private fun settleMain() {
        val state = _uiState.value
        val listed = state.listings ?: return
        if (listed.any { it.request.id == state.mainId }) return
        change { it.copy(mainId = mainRequest(listed, it.viewer)?.request?.id) }
    }

    /** Reads the listings and every moving lane again; a failure leaves the page as it was. */
    private suspend fun poll() {
        val started = generation
        val fresh = (requests.bookList(uuid) as? RequestsResult.Success)?.value ?: return
        val mainId = _uiState.value.mainId ?: return
        val others = splitBookRequests(fresh, mainId).lanes.map { it.request.id }
        val state = _uiState.value
        val moving = state.lanes.filter { isMoving(it.state) }.map { it.id }
        // A lane that appeared since the last read, or changed on another client, needs its full request again.
        val stale = (listOf(mainId) + others).filter { id ->
            val read = state.reads[id] ?: return@filter true
            val listed = fresh.firstOrNull { it.request.id == id }?.request ?: return@filter false
            listed.state != read.state || listed.updatedAt != read.updatedAt
        }
        val answers = readAll((moving + stale).distinct())
        if (started != generation) return
        change { it.copy(listings = fresh, reads = it.reads + answers) }
    }

    /** Each id's full request, concurrently; one that fails is left out. */
    private suspend fun readAll(ids: List<Long>): Map<Long, BookRequest> = coroutineScope {
        ids.map { id -> async { (requests.request(id) as? RequestsResult.Success)?.value } }.awaitAll()
    }.filterNotNull().associateBy { it.id }

    private fun RequestsResult<*>.errorOrNull(): RequestsError? = when (this) {
        is RequestsResult.Failure -> error
        RequestsResult.NoServer -> RequestsError.Unreachable
        is RequestsResult.Success -> null
    }

    private fun LibraryError.toRequestsError(): RequestsError = when (this) {
        LibraryError.Unreachable -> RequestsError.Unreachable
        LibraryError.UnexpectedResponse -> RequestsError.UnexpectedResponse
        LibraryError.SessionExpired -> RequestsError.SessionExpired
        is LibraryError.Http -> RequestsError.Http(code, serverMessage)
    }
}
