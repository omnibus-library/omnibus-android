package com.omnibus.omnibus.feature.requests.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.data.requests.RequestViewer
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestsRepository
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.feature.requests.DefaultFolded
import com.omnibus.omnibus.feature.requests.RequestGroup
import com.omnibus.omnibus.feature.requests.RequestGroupKey
import com.omnibus.omnibus.feature.requests.groupRequests
import com.omnibus.omnibus.feature.requests.listPollIntervalMillis
import com.omnibus.omnibus.feature.requests.page.NobodyViewer
import com.omnibus.omnibus.feature.requests.requestedBooks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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

/**
 * What the Requests list shows.
 *
 * @property listings Every reader's requests, newest first, or null until the first read answers.
 * @property failure Why the first read failed. Only shown while there are no [listings], since a failed refresh or poll
 * keeps the rows.
 * @property folded The groups the reader has folded. The choice lives as long as the screen's ViewModel.
 */
data class RequestsUIState(
    val listings: List<RequestListing>? = null,
    val failure: RequestsError? = null,
    val viewer: RequestViewer = NobodyViewer,
    val folded: Set<RequestGroupKey> = DefaultFolded,
    val isOnline: Boolean = true,
    val isRefreshing: Boolean = false,
) {

    /** The groups of books, most urgent first. */
    val groups: List<RequestGroup> get() = listings?.let { groupRequests(it, viewer) }.orEmpty()

    /** The books with an ebook request going, which the Request a book sheet opens rather than starts again. */
    val requested: Set<String> get() = requestedBooks(listings.orEmpty())

    /** Whether the Request a book button works: the reader may start a request, the list has loaded, and the device is online. */
    val canRequest: Boolean get() = viewer.canStart && listings != null && isOnline
}

/** Owns the Requests list: what the last read answered, which groups are folded, and the poll that keeps it current. */
@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val requests: RequestsRepository,
    private val authRepository: AuthRepository,
    connectivityMonitor: ConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUIState())

    /** The list's state. */
    val uiState: StateFlow<RequestsUIState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            connectivityMonitor.isOnline.collect { online -> _uiState.update { it.copy(isOnline = online) } }
        }
    }

    /** Reads the list. Called each time the screen is shown, and by Retry. */
    fun load() {
        viewModelScope.launch { read() }
    }

    /** Reads the list for pull to refresh. */
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            read()
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /** Flips whether [key]'s books are shown. */
    fun toggleFold(key: RequestGroupKey) {
        _uiState.update { it.copy(folded = if (key in it.folded) it.folded - key else it.folded + key) }
    }

    /**
     * Reads the list again while anything moves. The screen runs it while it is on screen. It waits while nothing moves
     * and starts again when a load or refresh finds something moving, so a failed first load doesn't end it.
     */
    suspend fun keepCurrent() {
        uiState.map { listPollIntervalMillis(it.listings) }.distinctUntilChanged().collectLatest { interval ->
            if (interval != null) {
                while (true) {
                    delay(interval)
                    read()
                }
            }
        }
    }

    private fun fail(error: RequestsError, viewer: RequestViewer) {
        _uiState.update { it.copy(failure = error.takeIf { _ -> it.listings == null }, viewer = viewer) }
    }

    /** One read. A failure shows only while there is nothing else on screen, and leaves the rows as they were. */
    private suspend fun read() {
        val user = authRepository.currentUser.catch { emit(null) }.first()
        val viewer = user?.let { RequestViewer(it.id, it.canStartRequests, it.canChooseRequestVersions) } ?: NobodyViewer
        when (val result = requests.list()) {
            is RequestsResult.Success -> _uiState.update { it.copy(listings = result.value, failure = null, viewer = viewer) }
            is RequestsResult.Failure -> fail(result.error, viewer)
            RequestsResult.NoServer -> fail(RequestsError.Unreachable, viewer)
        }
    }
}
