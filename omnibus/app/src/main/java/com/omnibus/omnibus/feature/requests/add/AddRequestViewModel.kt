package com.omnibus.omnibus.feature.requests.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.LibraryHit
import com.omnibus.omnibus.data.requests.ProviderBook
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsRepository
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.ScanOutcomeKind
import com.omnibus.omnibus.feature.requests.AddAction
import com.omnibus.omnibus.feature.requests.addAction
import com.omnibus.omnibus.feature.requests.di.RequestWordingSource
import com.omnibus.omnibus.feature.requests.startFailure
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What names a provider's record among the results: its own JSON, which is all there is to tell two apart. */
internal fun ProviderBook.key(): String = "record:" + meta.hashCode()

/** The outcomes whose library book is the record itself. */
private val ExactMatches = setOf(ScanOutcomeKind.AlreadyOwned, ScanOutcomeKind.OnWishlist, ScanOutcomeKind.InLibraryUnowned)

/** How long after the last keystroke the sheet searches. */
private const val SEARCH_DEBOUNCE_MILLIS = 250L

/** One section's search state. */
sealed interface SearchResults<out T> {
    data object Loading : SearchResults<Nothing>
    data object Failed : SearchResults<Nothing>

    /** The answer. An empty one is "Nothing matches". */
    data class Done<T>(val items: List<T>) : SearchResults<T>
}

/**
 * What the Request a book sheet shows.
 *
 * @property library The books in the library the query matches.
 * @property providers The online providers' records the query matches.
 * @property starting Whether a start is running, so no second one can begin and every Request button waits.
 * @property startingKey Which result the running start is for, so only its row says "Requesting…".
 * @property message What the last start said, such as why it failed. A new search clears it.
 * @property pendingOpen The book whose request page the Requests screen should open, set when a start succeeds. It lives
 * here, in the ViewModel the nav entry owns, so a fold or a rotation doesn't lose it.
 */
data class AddRequestUIState(
    val query: String = "",
    val library: SearchResults<LibraryHit> = SearchResults.Loading,
    val providers: SearchResults<ProviderBook> = SearchResults.Loading,
    val starting: Boolean = false,
    val startingKey: String? = null,
    val message: String? = null,
    val pendingOpen: String? = null,
    val isOnline: Boolean = true,
) {
    /** Whether there is nothing to search for yet. */
    val isIdle: Boolean get() = query.isBlank()
}

/** Owns the Request a book sheet: the search, and starting an ebook request on a result. */
@HiltViewModel
class AddRequestViewModel @Inject constructor(
    private val requests: RequestsRepository,
    connectivityMonitor: ConnectivityMonitor,
    private val wordingSource: RequestWordingSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddRequestUIState())

    /** The sheet's state. */
    val uiState: StateFlow<AddRequestUIState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    /**
     * Sets the query. After [SEARCH_DEBOUNCE_MILLIS] without another change it fills both sections, each as its own
     * answer arrives. A newer query drops an older one's answers.
     */
    fun updateQuery(query: String) {
        searchJob?.cancel()
        _uiState.update { it.copy(query = query, message = null, library = SearchResults.Loading, providers = SearchResults.Loading) }
        val text = query.trim()
        if (text.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            coroutineScope {
                launch {
                    val answer = requests.searchLibrary(text)
                    _uiState.update { it.copy(library = answer.toResults()) }
                }
                launch {
                    val answer = requests.searchProviders(text)
                    _uiState.update { it.copy(providers = answer.toResults()) }
                }
            }
        }
    }

    /** Starts an ebook request on a library book. */
    fun startFromLibrary(uuid: String) {
        launchStart(uuid) { start(uuid) }
    }

    /**
     * Starts from a provider's record: on the library book it matches when there is one (opening its request, or
     * refusing when the library holds an ebook), else on a book made from the record.
     *
     * @param requested The books with an ebook request going, which the match opens rather than starts again.
     */
    fun startFromRecord(book: ProviderBook, requested: Set<String>) {
        launchStart(book.key()) {
            val match = when (val resolved = requests.resolve(book)) {
                is RequestsResult.Success -> resolved.value
                else -> return@launchStart fail(wordingSource.wording().couldntLookUp(book.title))
            }
            // A close match is only a similar title ("Dune" for "Dune Messiah"), so it never stands for the record.
            val found = match.library.firstOrNull().takeIf { match.outcome in ExactMatches }
            when (found?.let { addAction(it.uuid, null, it.hasFiles, requested) }) {
                AddAction.Open -> open(found.uuid)
                AddAction.Held -> fail(wordingSource.wording().alreadyInLibrary(book.title))
                AddAction.Start -> start(found.uuid)
                null -> settle(requests.startFromRecord(book))
            }
        }
    }

    /** Called once the page has been opened for [AddRequestUIState.pendingOpen]. */
    fun opened() {
        _uiState.update { it.copy(pendingOpen = null) }
    }

    /** Runs one start. A start while one runs, or offline, is ignored, and says so. */
    private fun launchStart(key: String, block: suspend () -> Unit) {
        val state = _uiState.value
        if (!state.isOnline) return
        if (state.starting) {
            _uiState.update { it.copy(message = wordingSource.wording().stillStarting) }
            return
        }
        _uiState.update { it.copy(starting = true, startingKey = key, message = null) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _uiState.update { it.copy(starting = false, startingKey = null) }
            }
        }
    }

    private suspend fun start(uuid: String) = settle(requests.start(uuid, RequestKind.Ebook))

    private fun settle(result: RequestsResult<BookRequest>) {
        when (result) {
            is RequestsResult.Success -> open(result.value.bookUuid)
            is RequestsResult.Failure -> fail(startFailure(result.error, wordingSource.wording()))
            RequestsResult.NoServer -> fail(startFailure(RequestsError.Unreachable, wordingSource.wording()))
        }
    }

    private fun open(uuid: String) {
        _uiState.update { it.copy(pendingOpen = uuid) }
    }

    private fun fail(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private fun <T> RequestsResult<List<T>>.toResults(): SearchResults<T> = when (this) {
        is RequestsResult.Success -> SearchResults.Done(value)
        else -> SearchResults.Failed
    }
}
