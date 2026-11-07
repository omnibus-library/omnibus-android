package com.omnibus.omnibus.data.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.ControllerRig
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.After
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.EndOfChapter
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler

/** A controller with the timer bound through a lazy, built the way the app builds them, with a book loaded. */
@OptIn(ExperimentalCoroutinesApi::class)
private class TimerWiring : AutoCloseable {
    private val scheduler = TestCoroutineScheduler()
    private val timerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    private val timer = DefaultSleepTimer(
        dagger.Lazy<AudiobookController> { rig.controller },
        TimeSource { scheduler.currentTime },
        timerScope,
    )
    val rig: ControllerRig = ControllerRig(scheduler, sleepTimer = timer).also { it.loadAndSettle() }

    fun enginePauses(): Int = rig.engine.calls.count { it == "pause" }

    override fun close() {
        rig.close()
        timerScope.cancel()
    }
}

/**
 * The timer through the real [com.omnibus.omnibus.data.audiobook.playback.DefaultAudiobookController]: the mirror into
 * `PlaybackState.sleepTimer`, the clear on a book change and on stop, and the end-of-chapter pause through the engine
 * (R5, R7, R8).
 */
class SleepTimerWiringTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("ending the timer") {

        context("when another book loads") {
            // R7
            it("#169 TC-25: clears it (I-SLP-3)") {
                TimerWiring().use { w ->
                    w.rig.controller.setSleepTimer(After(15))
                    w.rig.settle()

                    w.rig.stageBook()
                    w.rig.controller.load("book-2", null, null, true)
                    w.rig.settle()

                    w.rig.controller.state.value.sleepTimer.shouldBeNull()
                }
            }
        }

        context("when playback stops") {
            // R7
            it("#169 TC-25: never pauses later (I-SLP-3)") {
                TimerWiring().use { w ->
                    w.rig.controller.setSleepTimer(After(15))
                    w.rig.settle()
                    w.rig.controller.stop()
                    w.rig.settle()
                    val before = w.enginePauses()

                    w.rig.advance(20 * 60_000L)

                    w.enginePauses() shouldBe before
                }
            }
        }

        context("when a seek reaches the armed chapter's end") {
            // R5, R8
            it("#169 TC-26: pauses the engine once (I-SLP-2, I-SLP-3)") {
                TimerWiring().use { w ->
                    w.rig.controller.setSleepTimer(EndOfChapter)
                    w.rig.settle()
                    val before = w.enginePauses()

                    w.rig.controller.seekTo(700.0)
                    w.rig.advance(5_000)

                    w.enginePauses() shouldBe before + 1
                }
            }
        }
    }
})
