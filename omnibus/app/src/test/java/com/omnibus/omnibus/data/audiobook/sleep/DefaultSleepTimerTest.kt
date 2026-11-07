package com.omnibus.omnibus.data.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.After
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import com.omnibus.omnibus.testing.TestTimeSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

/** The timed options of [DefaultSleepTimer]: the countdown, expiry, replacing and clearing (R1-R3, R7, R8). */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSleepTimerTest : DescribeSpec({

    val minute = 60_000L

    describe("the countdown") {
        // R1: counts once a second from the minutes set, on the wall clock, with no fresh emission between whole seconds
        it("#169 TC-1: a timed option counts down once a second from the minutes set (P-SLP-2, I-SLP-1)") {
            val rig = SleepTimerRig()
            rig.timer.set(After(15))
            rig.settle()
            rig.timer.state.value shouldBe SleepTimerState(After(15), 900.0)
            rig.advance(1_000)
            rig.timer.state.value shouldBe SleepTimerState(After(15), 899.0)
            rig.advance(1_000)
            rig.timer.state.value shouldBe SleepTimerState(After(15), 898.0)
            rig.advance(59_000)
            rig.timer.state.value shouldBe SleepTimerState(After(15), 839.0)
            rig.close()
        }

        // R1: emissions land on whole seconds counted back from the deadline, and none come between them
        it("#169 TC-2: emissions land on whole seconds from the deadline, with none between them (P-SLP-2, I-SLP-1)") {
            val rig = SleepTimerRig()
            val values = mutableListOf<SleepTimerState?>()
            rig.scope.launch { rig.timer.state.collect { values += it } }
            rig.settle()
            rig.timer.set(After(15))
            rig.settle()
            values shouldBe listOf(null, SleepTimerState(After(15), 900.0))

            rig.advance(999)
            values.size shouldBe 2
            rig.advance(1)
            values.last() shouldBe SleepTimerState(After(15), 899.0)
            values.size shouldBe 3

            rig.advance(4_000)
            values.size shouldBe 7
            values.takeLast(5).map { it?.remainingSeconds } shouldBe listOf(899.0, 898.0, 897.0, 896.0, 895.0)
            rig.close()
        }

        // R1: a tick reads the clock, so a late tick catches up, and a backward jump stays clamped to the minutes
        it("#169 TC-3: a tick reads the clock, so a late tick catches up and a clock jump stays within the minutes (P-SLP-2)") {
            val clock = TestTimeSource(0L)
            val rig = SleepTimerRig(clock = clock)
            rig.timer.set(After(1))
            rig.settle()
            clock.advanceBy(5_000)
            rig.advance(1_000)
            rig.timer.state.value shouldBe SleepTimerState(After(1), 55.0)

            clock.set(-10_000)
            rig.advance(1_000)
            rig.timer.state.value shouldBe SleepTimerState(After(1), 60.0)
            rig.close()
        }

        // R1: the countdown never reads the rate or the play state
        it("#169 TC-4: the countdown is the wall clock, whatever the rate and play state (P-SLP-2, I-SLP-1)") {
            val rig = SleepTimerRig(playbackState(rate = 3.0, isPlaying = false))
            rig.timer.set(After(15))
            rig.settle()
            rig.advance(10_000)
            rig.timer.state.value shouldBe SleepTimerState(After(15), 890.0)
            rig.push(playbackState(rate = 0.5, isPlaying = true))
            rig.advance(10_000)
            rig.timer.state.value shouldBe SleepTimerState(After(15), 880.0)
            rig.close()
        }
    }

    describe("expiry") {
        // R2: the state clears before the single pause, at the deadline and not before
        it("#169 TC-5: expiry clears the state, then pauses once, at the deadline (P-SLP-2, P-SLP-AC1)") {
            for (playing in listOf(true, false)) {
                val rig = SleepTimerRig(playbackState(isPlaying = playing))
                rig.timer.set(After(15))
                rig.settle()
                rig.advance(899_999)
                rig.timer.state.value shouldBe SleepTimerState(After(15), 1.0)
                rig.pauses() shouldBe 0
                rig.advance(1)
                rig.timer.state.value.shouldBeNull()
                rig.pauses() shouldBe 1
                rig.seenAtPause shouldBe listOf(null)
                rig.advance(10 * minute)
                rig.pauses() shouldBe 1
                rig.close()
            }
        }
    }

    describe("clearing and replacing") {
        // R3: null and a timed option with no minutes clear without a pause, from a running timer or from idle
        it("#169 TC-6: set(null), set(After(0)) and set(After(-5)) clear the timer without pausing (P-SLP-5)") {
            val options: List<SleepTimerOption?> = listOf(null, After(0), After(-5))
            for (option in options) {
                val running = SleepTimerRig()
                val values = mutableListOf<SleepTimerState?>()
                running.scope.launch { running.timer.state.collect { values += it } }
                running.timer.set(After(15))
                running.settle()
                running.advance(minute)
                running.timer.set(option)
                running.settle()
                running.timer.state.value.shouldBeNull()
                val afterClear = values.size
                running.advance(20 * minute)
                values.size shouldBe afterClear
                running.pauses() shouldBe 0
                running.close()

                val idle = SleepTimerRig()
                idle.timer.set(option)
                idle.timer.state.value.shouldBeNull()
                idle.advance(20 * minute)
                idle.pauses() shouldBe 0
                idle.close()
            }
        }

        // R3: picking a new option replaces the running one, including the same option, which restarts
        it("#169 TC-7: a new option replaces the running timer, and the same option restarts it (P-SLP-5)") {
            val replaced = SleepTimerRig()
            replaced.timer.set(After(15))
            replaced.settle()
            replaced.advance(5 * minute)
            replaced.timer.set(After(30))
            replaced.timer.state.value shouldBe SleepTimerState(After(30), 1800.0)
            replaced.advance(10 * minute)
            replaced.pauses() shouldBe 0
            replaced.advance(20 * minute)
            replaced.pauses() shouldBe 1
            replaced.close()

            val restarted = SleepTimerRig()
            restarted.timer.set(After(15))
            restarted.settle()
            restarted.advance(5 * minute)
            restarted.timer.set(After(15))
            restarted.timer.state.value shouldBe SleepTimerState(After(15), 900.0)
            restarted.advance(10 * minute)
            restarted.pauses() shouldBe 0
            restarted.advance(5 * minute)
            restarted.pauses() shouldBe 1
            restarted.close()
        }
    }

    describe("the controller and the book") {
        // R8: construction, set(null) and the timed countdown never take the controller; only expiry does
        it("#169 TC-8: the controller is taken only at expiry for a timed option (I-SLP-3)") {
            val rig = SleepTimerRig()
            rig.lazyGets shouldBe 0
            rig.timer.set(null)
            rig.timer.set(After(1))
            rig.settle()
            rig.advance(59_000)
            rig.lazyGets shouldBe 0
            rig.advance(1_000)
            (rig.lazyGets >= 1) shouldBe true
            rig.close()

            val armed = SleepTimerRig(playbackState(positionSeconds = 100.0))
            armed.lazyGets shouldBe 0
            armed.timer.set(SleepTimerOption.EndOfChapter)
            (armed.lazyGets >= 1) shouldBe true
            armed.close()
        }

        // R7: a timed timer doesn't watch the book; the controller's own clear is what stops it
        it("#169 TC-9: a timed timer keeps counting across book changes, to expiry (P-SLP-5, I-SLP-3)") {
            val rig = SleepTimerRig()
            rig.timer.set(After(15))
            rig.settle()
            rig.advance(minute)
            rig.push(playbackState(book = playingBook("book-2")))
            rig.advance(1_000)
            rig.timer.state.value shouldBe SleepTimerState(After(15), 839.0)
            rig.push(PlaybackState())
            rig.timer.state.value shouldBe SleepTimerState(After(15), 839.0)
            rig.advance(839_000)
            rig.pauses() shouldBe 1
            rig.close()
        }
    }
})
