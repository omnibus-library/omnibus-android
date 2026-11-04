package com.omnibus.omnibus.data.audiobook.playback

import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [SleepTimer]. [state] is set by the test, and [set] records each call. */
class FakeSleepTimer : SleepTimer {

    override val state: MutableStateFlow<SleepTimerState?> = MutableStateFlow(null)

    /** Every value given to [set], in call order. */
    val sets = mutableListOf<SleepTimerOption?>()

    /** Records [option]. Clearing the timer also clears [state], as the real timer does. */
    override fun set(option: SleepTimerOption?) {
        sets += option
        if (option == null) state.value = null
    }
}
