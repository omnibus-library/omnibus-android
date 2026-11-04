package com.omnibus.omnibus.feature.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.sleep.sleepChapterEnd
import com.omnibus.omnibus.data.audiobook.sleep.sleepChapterNumber

/** The timed presets, in minutes, in the order the sheet shows them (P-SLP-1). */
val SleepTimedMinutes: List<Int> = listOf(15, 30, 45, 60, 120, 180, 240)

/** One cell of the sleep sheet. */
sealed interface SleepChoice {
    /** Turns the timer off. */
    data object Off : SleepChoice

    /** Pauses after [minutes]. */
    data class Timed(val minutes: Int) : SleepChoice

    /** Pauses at the end of chapter [chapterNumber], its 1-based position in the chapter list. */
    data class EndOfChapter(val chapterNumber: Int) : SleepChoice
}

/** The controller option this choice sets, or null for [SleepChoice.Off]. */
fun SleepChoice.toOption(): SleepTimerOption? = when (this) {
    SleepChoice.Off -> null
    is SleepChoice.Timed -> SleepTimerOption.After(minutes)
    is SleepChoice.EndOfChapter -> SleepTimerOption.EndOfChapter
}

/** Off, the seven timed presets, then end of chapter when the current chapter can be armed. */
fun sleepChoices(state: PlaybackState): List<SleepChoice> {
    val choices = mutableListOf<SleepChoice>(SleepChoice.Off)
    SleepTimedMinutes.forEach { choices += SleepChoice.Timed(it) }
    val number = state.sleepChapterNumber()
    if (state.sleepChapterEnd() != null && number != null) choices += SleepChoice.EndOfChapter(number)
    return choices
}

/**
 * The cell marked selected for [timer]: Off with no timer, the timed cell for a running [SleepTimerOption.After], or the
 * end-of-chapter cell while one is armed. Null when the running option has no cell (an unlisted duration, or end of
 * chapter with no cell).
 */
fun selectedChoice(timer: SleepTimerState?, choices: List<SleepChoice>): SleepChoice? =
    when (val option = timer?.option) {
        null -> SleepChoice.Off
        is SleepTimerOption.After -> choices.firstOrNull { it == SleepChoice.Timed(option.minutes) }
        SleepTimerOption.EndOfChapter -> choices.firstOrNull { it is SleepChoice.EndOfChapter }
    }
