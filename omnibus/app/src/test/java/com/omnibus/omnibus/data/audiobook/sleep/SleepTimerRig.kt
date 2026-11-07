package com.omnibus.omnibus.data.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.TestAudiobookController.Command
import com.omnibus.omnibus.di.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/**
 * A [DefaultSleepTimer] on a virtual clock, over a [TestAudiobookController] that holds [initial]. The timer's clock and
 * its scope share one scheduler, so `delay` and the timer's `nowMillis()` move together (AND-TEST-3). Every pause is
 * recorded with the timer's state at that moment, in [seenAtPause].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SleepTimerRig(initial: PlaybackState, clock: TimeSource? = null) : AutoCloseable {
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    private val fake = TestAudiobookController(initial)

    /** The timer's state at each pause, in call order. */
    val seenAtPause = mutableListOf<SleepTimerState?>()

    private lateinit var timerRef: DefaultSleepTimer

    private val controller: AudiobookController = object : AudiobookController by fake {
        override fun pause() {
            seenAtPause += timerRef.state.value
            fake.pause()
        }
    }

    val timer: DefaultSleepTimer = DefaultSleepTimer(
        dagger.Lazy<AudiobookController> { controller },
        clock ?: TimeSource { scheduler.currentTime },
        scope,
    ).also { timerRef = it }

    /** Runs whatever is due now, without moving the clock. */
    fun settle() = scheduler.runCurrent()

    /** Moves the clock forward by [millis], running everything that falls due, including at the end instant. */
    fun advance(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    /** Sets the controller's state to [state] and lets the timer's collectors see it. */
    fun push(state: PlaybackState) {
        fake.state.value = state
        settle()
    }

    /** How many times the controller was asked to pause. */
    fun pauses(): Int = fake.commands.count { it == Command.Pause }

    /** Cancels the timer's scope. */
    override fun close() {
        scope.cancel()
    }
}
