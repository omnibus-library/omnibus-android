package com.omnibus.omnibus.data.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.ControllerRig
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.After
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.EndOfChapter
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * The timer through the real [com.omnibus.omnibus.data.audiobook.playback.DefaultAudiobookController]: the mirror into
 * `PlaybackState.sleepTimer`, the clear on a book change and on stop, and the end-of-chapter pause through the engine
 * (R5, R7, R8). The timer takes the controller through a lazy, as the app wires it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerWiringTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var scheduler: TestCoroutineScheduler
    lateinit var timerScope: CoroutineScope
    lateinit var timer: DefaultSleepTimer
    val rigs = mutableListOf<ControllerRig>()

    beforeEach {
        scheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(scheduler))
        timerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    }

    afterEach {
        rigs.forEach { it.close() }
        rigs.clear()
        timerScope.cancel()
        Dispatchers.resetMain()
    }

    /** A controller with the timer bound, built the way the app builds them. */
    fun newRig(): ControllerRig {
        lateinit var rig: ControllerRig
        timer = DefaultSleepTimer(
            dagger.Lazy<AudiobookController> { rig.controller },
            TimeSource { scheduler.currentTime },
            timerScope,
        )
        rig = ControllerRig(scheduler, sleepTimer = timer)
        rigs += rig
        return rig
    }

    describe("the mirror and the countdown") {
        // R8: building and loading never reach the controller through the lazy; setSleepTimer mirrors the timer
        it("#169 TC-24: building, loading and setting a timed option work through the real controller (I-SLP-3, C7)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.setSleepTimer(After(15))
            rig.settle()
            timer.state.value shouldBe SleepTimerState(After(15), 900.0)
            rig.controller.state.value.sleepTimer shouldBe timer.state.value

            rig.advance(1_000)
            timer.state.value shouldBe SleepTimerState(After(15), 899.0)
            rig.controller.state.value.sleepTimer shouldBe SleepTimerState(After(15), 899.0)
        }
    }

    describe("book change and stop") {
        // R7: loading another book clears the timer through the controller, and nothing pauses after it
        it("#169 TC-25: loading another book clears the timer, and stopping clears it too (I-SLP-3)") {
            val switched = newRig()
            switched.loadAndSettle()
            switched.controller.setSleepTimer(After(15))
            switched.settle()
            switched.controller.load("book-2", null, null, true)
            switched.settle()
            timer.state.value.shouldBeNull()
            switched.controller.state.value.sleepTimer.shouldBeNull()
            val afterSwitch = switched.engine.calls.size
            switched.advance(20 * 60_000L)
            switched.engine.calls.drop(afterSwitch).count { it == "pause" } shouldBe 0

            val stopped = newRig()
            stopped.loadAndSettle()
            stopped.controller.setSleepTimer(After(15))
            stopped.settle()
            stopped.controller.stop()
            stopped.settle()
            timer.state.value.shouldBeNull()
            stopped.controller.state.value.sleepTimer.shouldBeNull()
            val afterStop = stopped.engine.calls.size
            stopped.advance(20 * 60_000L)
            timer.state.value.shouldBeNull()
            stopped.engine.calls.drop(afterStop).count { it == "pause" } shouldBe 0
        }

        // R5, R8: the end of chapter pauses the engine once, at the seek that reaches it, and never again
        it("#169 TC-26: a seek to the armed end pauses the engine once and clears the timer (I-SLP-2, I-SLP-3)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.settle()
            rig.controller.state.value.isLoading shouldBe false
            rig.controller.state.value.chapters.size shouldBe 3

            rig.controller.setSleepTimer(EndOfChapter)
            rig.settle()
            rig.controller.state.value.sleepTimer shouldBe SleepTimerState(EndOfChapter, 700.0)

            val mark = rig.engine.calls.size
            rig.controller.seekTo(700.0)
            rig.settle()
            rig.controller.state.value.sleepTimer.shouldBeNull()
            rig.engine.calls.drop(mark).count { it == "pause" } shouldBe 1

            rig.advance(5_000)
            rig.engine.calls.drop(mark).count { it == "pause" } shouldBe 1
        }

        // R7: a book change clears an armed chapter without a pause
        it("#169 TC-27: a book change clears an armed chapter without a pause (I-SLP-3)") {
            val rig = newRig()
            rig.loadAndSettle()
            rig.controller.setSleepTimer(EndOfChapter)
            rig.settle()
            rig.controller.load("book-2", null, null, true)
            rig.settle()
            timer.state.value.shouldBeNull()
            rig.controller.state.value.sleepTimer.shouldBeNull()

            val mark = rig.engine.calls.size
            rig.advance(5_000)
            timer.state.value.shouldBeNull()
            rig.engine.calls.drop(mark).count { it == "pause" } shouldBe 0
        }
    }
})
