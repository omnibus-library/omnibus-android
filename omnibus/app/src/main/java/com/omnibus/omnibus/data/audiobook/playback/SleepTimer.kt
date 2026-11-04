package com.omnibus.omnibus.data.audiobook.playback

import kotlinx.coroutines.flow.StateFlow

/** When the sleep timer pauses playback. */
sealed interface SleepTimerOption {
    /** Pauses after [minutes] of playback. */
    data class After(val minutes: Int) : SleepTimerOption

    /** Pauses at the end of the current chapter. */
    data object EndOfChapter : SleepTimerOption
}

/** A sleep timer that is running. */
data class SleepTimerState(
    val option: SleepTimerOption,
    /**
     * Wall-clock seconds left. For [SleepTimerOption.EndOfChapter], the chapter's end minus the position, divided by the
     * rate.
     */
    val remainingSeconds: Double,
)

/** Counts down a sleep timer. Optional: bind it with `@BindsOptionalOf`. With no binding, the timer does nothing. */
interface SleepTimer {

    /** The running timer, or null when none is set. */
    val state: StateFlow<SleepTimerState?>

    /** Sets the timer to [option], or clears it with null. */
    fun set(option: SleepTimerOption?)
}
