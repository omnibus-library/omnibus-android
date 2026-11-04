package com.omnibus.omnibus.feature.audiobook.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the sleep sheet shows. [remainingSeconds] is non-null only while the timer has time left, and [selected] is the
 * cell marked as chosen.
 */
data class SleepUIState(
    val choices: List<SleepChoice> = emptyList(),
    val selected: SleepChoice? = SleepChoice.Off,
    val remainingSeconds: Double? = null,
)

/** Maps the controller's state to what the sleep sheet shows. */
internal fun sleepUIState(playback: PlaybackState): SleepUIState {
    val choices = sleepChoices(playback)
    return SleepUIState(
        choices = choices,
        selected = selectedChoice(playback.sleepTimer, choices),
        remainingSeconds = playback.sleepTimer?.remainingSeconds?.takeIf { it > 0 },
    )
}

/**
 * The sleep sheet's state. The timer belongs to the loaded book, which is the one the player shows, so there is no book
 * id here.
 */
@HiltViewModel
class SleepViewModel @Inject constructor(private val controller: AudiobookController) : ViewModel() {
    private val _uiState = MutableStateFlow(sleepUIState(controller.state.value))

    /** What the sheet shows. */
    val uiState: StateFlow<SleepUIState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state.collect { _uiState.value = sleepUIState(it) }
        }
    }

    /** Sets the timer to [choice]. The sheet stays open after a pick (P-SLP-1). */
    fun onSelect(choice: SleepChoice) = controller.setSleepTimer(choice.toOption())
}
