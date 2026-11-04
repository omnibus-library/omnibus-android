package com.omnibus.omnibus.feature.audiobook.speed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the speed sheet shows: the rate, the preset it matches (if any), and whether the step buttons can move.
 *
 * @property rate The rate the sheet draws: the live drag value while a drag is open, else the player's rate.
 * @property selectedPreset The preset equal to [rate] within the selection tolerance (R10), or null when none matches.
 * @property canSlower False at the lower bound, so the slower button is disabled (P-SPD-4, A3).
 * @property canFaster False at the upper bound, so the faster button is disabled (P-SPD-4, A3).
 */
data class SpeedUIState(
    val rate: Double = 1.0,
    val selectedPreset: Double? = 1.0,
    val canSlower: Boolean = true,
    val canFaster: Boolean = true,
) {
    companion object {

        /** The state the sheet shows for [rate]. The rate is shown as given; the step buttons snap it on the next change (A5). */
        fun forRate(rate: Double): SpeedUIState = SpeedUIState(
            rate = rate,
            selectedPreset = SPEED_PRESETS.firstOrNull { isSelectedPreset(it, rate) },
            canSlower = canSlower(rate),
            canFaster = canFaster(rate),
        )
    }
}

/**
 * The speed sheet's state and intents (R12). Every intent goes through [AudiobookController.setRate]: a slider drag sends
 * live values with `persist = false` and the release (or a dismiss mid-drag) persists the last one once (I-SPD-2). The
 * sheet never touches the rate store, so the controller decides what is saved.
 *
 * The state follows the player's rate, so a rate the controller picks up at load (I-SPD-4) shows in the sheet.
 */
@HiltViewModel
class SpeedViewModel @Inject constructor(
    private val controller: AudiobookController,
) : ViewModel() {

    /** The snapped value of an open slider drag, or null when none is open. */
    private var liveRate: Double? = null

    private val _uiState = MutableStateFlow(SpeedUIState.forRate(controller.state.value.rate))

    /** The sheet's state, republished on every intent and on every external rate change. */
    val uiState: StateFlow<SpeedUIState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            controller.state.map { it.rate }.distinctUntilChanged().collect { publish() }
        }
    }

    /** Sets the preset the listener tapped, saved at once, including the preset already selected (P-SPD-AC1, A4). */
    fun onPresetChosen(preset: Double) = commit(preset)

    /**
     * A slider move: the snapped value is applied live and not saved. A value that snaps to the last one sends nothing
     * (R6).
     */
    fun onSliderMoved(value: Float) {
        val snapped = snapRate(value.toDouble())
        if (snapped == liveRate) return
        liveRate = snapped
        controller.setRate(snapped, persist = false)
        publish()
    }

    /** The slider's release: the last snapped value is saved once. Does nothing when no drag is open (R6, I-SPD-2). */
    fun onSliderReleased() {
        val live = liveRate ?: return
        commit(live)
    }

    /** Persists a drag the sheet's dismissal cut short (no release came). Safe to call more than once (R6). */
    fun onSheetClosed() = onSliderReleased()

    /** One fine-tune step slower from the current rate, saved. Does nothing at the lower bound (P-SPD-4). */
    fun onSlower() {
        val rate = currentRate()
        if (canSlower(rate)) commit(stepRate(rate, -1))
    }

    /** One fine-tune step faster from the current rate, saved. Does nothing at the upper bound (P-SPD-4). */
    fun onFaster() {
        val rate = currentRate()
        if (canFaster(rate)) commit(stepRate(rate, +1))
    }

    private fun currentRate(): Double = liveRate ?: controller.state.value.rate

    private fun commit(rate: Double) {
        liveRate = null
        controller.setRate(rate, persist = true)
        publish()
    }

    private fun publish() {
        _uiState.value = SpeedUIState.forRate(currentRate())
    }
}
