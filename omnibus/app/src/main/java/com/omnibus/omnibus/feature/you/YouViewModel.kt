package com.omnibus.omnibus.feature.you

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.requests.RequestsRepository
import com.omnibus.omnibus.data.requests.RequestsResult
import com.omnibus.omnibus.data.requests.toRequestViewer
import com.omnibus.omnibus.feature.requests.needsYouCount
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the You root shows about requests.
 *
 * @property showsRequests Whether the Requests row is shown: the server has book requests on for the reader.
 * @property needsYou How many books wait on the reader to pick a version. Zero shows no pill.
 */
data class YouUIState(val showsRequests: Boolean = false, val needsYou: Int = 0)

/** Owns the You root's Requests row and its "N needs you" pill. */
@HiltViewModel
class YouViewModel @Inject constructor(
    private val requests: RequestsRepository,
    authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(YouUIState())

    /** The You root's state. */
    val uiState: StateFlow<YouUIState> = _uiState.asStateFlow()

    private var user: CurrentUser? = null
    private var isShown = false

    init {
        // Follows the session, which is not confirmed until the server answers, so a user arriving after the screen
        // was shown still gets the row and the count.
        viewModelScope.launch {
            authRepository.currentUser.catch { emit(null) }.collect {
                user = it
                if (isShown) readCount()
            }
        }
    }

    /**
     * Reads the count again. The screen calls it each time the You root enters composition: on first show, on return
     * from Requests or a request page, and on a switch back to the tab. A failed read leaves no pill rather than a
     * stale count, and a reader who can't pick versions never has one, so that read is skipped.
     */
    fun shown() {
        isShown = true
        viewModelScope.launch { readCount() }
    }

    private suspend fun readCount() {
        val shows = user?.bookRequests == true
        val viewer = user.toRequestViewer()
        _uiState.update { it.copy(showsRequests = shows, needsYou = 0) }
        if (!shows || !viewer.canChoose) return
        val listed = (requests.list() as? RequestsResult.Success)?.value ?: return
        _uiState.update { it.copy(needsYou = needsYouCount(listed, viewer)) }
    }
}
