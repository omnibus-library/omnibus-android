package com.omnibus.omnibus.feature.requests.page

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryError
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsRepository
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.Wanter
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.requests.detailPollIntervalMillis
import com.omnibus.omnibus.feature.requests.isMoving
import com.omnibus.omnibus.feature.requests.mainRequest
import com.omnibus.omnibus.feature.requests.splitBookRequests
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
}

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

    /** Reads the page. Called each time the page is shown, and by Retry and pull to refresh. */
    fun load() {
        viewModelScope.launch { loadPage() }
    }

    /**
     * Polls while any lane is moving. The screen runs it while the page is on screen, so polling stops when the entry
     * leaves. It waits while nothing moves and starts again when a lane does, and a failed first load doesn't end it.
     */
    suspend fun keepCurrent() {
        uiState.map { it.pollInterval }.distinctUntilChanged().collectLatest { interval ->
            if (interval != null) {
                while (true) {
                    delay(interval)
                    poll()
                }
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
