package com.omnibus.omnibus.data.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.SleepTimer
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.TimeSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The sleep timer (#169), the one [SleepTimer] the controller mirrors (C7). It is a singleton on the application scope,
 * so it outlives any screen and keeps running with the app in the background (I-SLP-3).
 *
 * A timed option counts down by the wall clock, once a second, and keeps counting while playback is paused (I-SLP-1).
 * End of chapter works in book time: it stores the chapter's end when it is set, and pauses once the position reaches
 * that end, by playback or by a seek to or past it. While the book plays, a one-shot job lands the pause on the boundary
 * instead of on the next position update (I-SLP-2). Loading another book or stopping clears it, through the controller.
 *
 * [controller] is a [dagger.Lazy] because the controller is built with this timer in its constructor. Only the end of
 * chapter arm, the watch and the firing dereference it, so the graph stays acyclic.
 *
 * Every entry point runs on the main thread, as the controller's commands do, so the state needs no locks.
 */
@Singleton
class DefaultSleepTimer @Inject constructor(
    private val controller: dagger.Lazy<AudiobookController>,
    private val timeSource: TimeSource,
    @param:ApplicationScope private val scope: CoroutineScope,
) : SleepTimer {

    private val _state = MutableStateFlow<SleepTimerState?>(null)
    override val state: StateFlow<SleepTimerState?> = _state.asStateFlow()

    private var modeJob: Job? = null // the timed ticker or the chapter watch
    private var preciseJob: Job? = null // the one-shot pause at the chapter boundary
    private var preciseKey: Pair<Double, Double>? = null // (position, rate) the precise job was scheduled from
    private var armedBook: String? = null
    private var chapterEnd = 0.0

    override fun set(option: SleepTimerOption?) {
        cancelAll() // a new option replaces the old one, whatever it was
        when {
            option is SleepTimerOption.After && option.minutes > 0 -> startTimed(option)
            option == SleepTimerOption.EndOfChapter -> armEndOfChapter()
            else -> _state.value = null // null, or After with no minutes left
        }
    }

    /** Counts down from the deadline set now. It never touches [controller]. */
    private fun startTimed(option: SleepTimerOption.After) {
        val totalMs = option.minutes * 60_000L
        val deadline = timeSource.nowMillis() + totalMs
        fun leftMs() = (deadline - timeSource.nowMillis()).coerceIn(0L, totalMs)
        _state.value = SleepTimerState(option, totalMs / 1000.0)
        modeJob = scope.launch {
            var left = totalMs
            while (true) {
                val step = left % 1000
                delay(if (step == 0L) 1000L else step) // ticks land on whole seconds before the deadline
                left = leftMs() // read from the clock, so a late tick catches up
                if (left == 0L) {
                    fire()
                    return@launch
                }
                _state.value = SleepTimerState(option, left / 1000.0)
            }
        }
    }

    /** Arms the chapter that is current now, or clears the timer when no chapter can be armed (R4). */
    private fun armEndOfChapter() {
        val c = controller.get()
        val s = c.state.value
        val end = s.sleepChapterEnd()
        val uuid = s.book?.bookUuid
        if (end == null || uuid == null) {
            _state.value = null
            return
        }
        if (s.positionSeconds >= end - EPSILON_SECONDS) {
            fire() // already at the end: fire before the watch starts, so nothing can cancel it from inside
            return
        }
        armedBook = uuid
        chapterEnd = end
        _state.value = SleepTimerState(SleepTimerOption.EndOfChapter, maxOf(0.0, end - s.positionSeconds) / s.safeRate())
        modeJob = scope.launch { c.state.collect { evaluate(it) } }
    }

    /** Applies one controller state to the armed chapter. It is a pure function of [s] and the armed data. */
    private fun evaluate(s: PlaybackState) {
        val uuid = armedBook ?: return
        if (s.book?.bookUuid != uuid) {
            cancelAll()
            _state.value = null
            return
        }
        if (s.isLoading) {
            cancelPrecise()
            return
        }
        if (s.positionSeconds >= chapterEnd - EPSILON_SECONDS) {
            fire()
            return
        }
        val rate = s.safeRate()
        val wallLeft = (chapterEnd - s.positionSeconds) / rate
        _state.value = SleepTimerState(SleepTimerOption.EndOfChapter, wallLeft) // equal values are dropped by the flow
        val eligible = s.isPlaying && !s.isBuffering && s.error == null && wallLeft <= PRECISE_WINDOW_SECONDS
        if (!eligible) {
            cancelPrecise()
            return
        }
        val key = s.positionSeconds to rate
        if (preciseJob?.isActive == true && key == preciseKey) return // the mirror's echo must not push the pause later
        cancelPrecise()
        preciseKey = key
        // Rounded, not ceiled: 0.6000000000000227 s must be 600 ms.
        preciseJob = scope.launch {
            delay((wallLeft * 1000).roundToLong().coerceAtLeast(0L))
            fire()
        }
    }

    /** Clears first, then pauses: `pause()` re-enters the watch synchronously, which must find nothing armed. */
    private fun fire() {
        cancelAll() // may cancel the coroutine running this; nothing below suspends, so it still completes
        _state.value = null
        controller.get().pause()
    }

    private fun cancelPrecise() {
        preciseJob?.cancel()
        preciseJob = null
        preciseKey = null
    }

    private fun cancelAll() {
        modeJob?.cancel()
        modeJob = null
        cancelPrecise()
        armedBook = null
    }

    private fun PlaybackState.safeRate(): Double = if (rate > 0) rate else 1.0

    private companion object {
        /** Book seconds. Absorbs millisecond-quantised engine positions and float sums at a chapter boundary. */
        const val EPSILON_SECONDS = 0.05

        /** The wall-clock window in which the precise pause is scheduled. */
        const val PRECISE_WINDOW_SECONDS = 1.0
    }
}
