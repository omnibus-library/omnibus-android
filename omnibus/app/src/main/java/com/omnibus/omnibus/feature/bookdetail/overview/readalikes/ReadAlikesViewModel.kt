package com.omnibus.omnibus.feature.bookdetail.overview.readalikes

import androidx.lifecycle.ViewModel
import coil3.fetch.Fetcher
import com.omnibus.omnibus.data.suggestions.SuggestionsAnswer
import com.omnibus.omnibus.data.suggestions.SuggestionsRepository
import com.omnibus.omnibus.data.suggestions.SuggestionsResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the read-alikes section shows. [Ready.items] is never empty. */
sealed interface ReadAlikesUiState {

    /** Nothing: not read yet, not configured, nothing to show, or the first read failed. */
    data object Hidden : ReadAlikesUiState

    /** The server is still looking. */
    data object Searching : ReadAlikesUiState

    /** The server gave up, or kept looking past the poll budget. */
    data object Unavailable : ReadAlikesUiState

    /** The shelf. */
    data class Ready(val items: List<ReadAlike>) : ReadAlikesUiState
}

/**
 * Owns the read-alikes section for one book. A failed read leaves whatever the section already shows, and only the
 * server's status moves it between states.
 *
 * @property bookUuid The book whose suggestions to read.
 */
@HiltViewModel(assistedFactory = ReadAlikesViewModel.Factory::class)
class ReadAlikesViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val repository: SuggestionsRepository,
) : ViewModel() {

    /** Creates a [ReadAlikesViewModel] for the book [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ReadAlikesViewModel
    }

    private val _uiState = MutableStateFlow<ReadAlikesUiState>(ReadAlikesUiState.Hidden)

    /** The section's state. Starts [ReadAlikesUiState.Hidden] until a load finishes its first read. */
    val uiState: StateFlow<ReadAlikesUiState> = _uiState.asStateFlow()

    /** Fetches the cover images through [repository], so the bearer token is sent. */
    val coverFetcher: Fetcher.Factory<SuggestionCoverRequest> = SuggestionCoverFetcher.Factory(repository)

    private var settledKey: Int? = null

    /**
     * Reads the suggestions for [reloadKey], then asks again every [SUGGESTIONS_POLL_INTERVAL_MILLIS] while the server
     * is still searching, up to [SUGGESTIONS_POLL_ATTEMPTS] times. It runs in the caller's coroutine, so cancelling
     * that coroutine stops the polling. A key whose load finished is not read again.
     */
    suspend fun load(reloadKey: Int) {
        if (reloadKey == settledKey) return
        read()
        var attempts = 0
        while (_uiState.value == ReadAlikesUiState.Searching && attempts < SUGGESTIONS_POLL_ATTEMPTS) {
            delay(SUGGESTIONS_POLL_INTERVAL_MILLIS)
            attempts++
            read()
        }
        if (_uiState.value == ReadAlikesUiState.Searching) _uiState.value = ReadAlikesUiState.Unavailable
        // Not reached when the load is cancelled, so an interrupted load reads again when it is called again.
        settledKey = reloadKey
    }

    /** One read. A failed read changes nothing. */
    private suspend fun read() {
        val answer = (repository.getSuggestions(bookUuid) as? SuggestionsResult.Success)?.answer ?: return
        _uiState.value = when (answer) {
            SuggestionsAnswer.NotConfigured -> ReadAlikesUiState.Hidden
            SuggestionsAnswer.Pending -> ReadAlikesUiState.Searching
            SuggestionsAnswer.Unavailable -> ReadAlikesUiState.Unavailable
            is SuggestionsAnswer.Ready -> readAlikesOf(answer.items).let {
                if (it.isEmpty()) ReadAlikesUiState.Hidden else ReadAlikesUiState.Ready(it)
            }
        }
    }
}
