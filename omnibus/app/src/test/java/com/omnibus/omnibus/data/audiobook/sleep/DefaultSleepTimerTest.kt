package com.omnibus.omnibus.data.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.After
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.position.randomPlayingBook
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.TestTimeSource
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

private const val MINUTE = 60_000L

/** The timed options of [DefaultSleepTimer]: the countdown, expiry, replacing and clearing (R1-R3, R7, R8). */
class DefaultSleepTimerTest : DescribeSpec({

    fun sut(initial: PlaybackState = playbackState(randomPlayingBook()), clock: TimeSource? = null) =
        SleepTimerRig(initial, clock)

    describe("the countdown") {

        // R1
        it("#169 TC-1: counts down each second from the minutes set (P-SLP-2, I-SLP-1)") {
            sut().use { rig ->
                rig.timer.set(After(15))
                rig.settle()
                rig.timer.state.value shouldBe SleepTimerState(After(15), 900.0)

                rig.advance(61_000)

                rig.timer.state.value shouldBe SleepTimerState(After(15), 839.0)
            }
        }

        context("when a tick comes late") {
            // R1: a tick reads the clock
            it("#169 TC-3: catches up with the clock (P-SLP-2)") {
                val clock = TestTimeSource()
                sut(clock = clock).use { rig ->
                    rig.timer.set(After(1))
                    rig.settle()

                    clock.advanceBy(5_000)
                    rig.advance(1_000)

                    rig.timer.state.value shouldBe SleepTimerState(After(1), 55.0)
                }
            }
        }

        // R1: the countdown never reads the rate or the play state
        it("#169 TC-4: follows the wall clock, over each rate crossed with each play state (P-SLP-2, I-SLP-1)") {
            checkAll(Exhaustive.of(0.5, 3.0), Exhaustive.boolean()) { rate, playing ->
                sut(playbackState(randomPlayingBook(), rate = rate, isPlaying = playing)).use { rig ->
                    rig.timer.set(After(15))
                    rig.settle()

                    rig.advance(10_000)

                    rig.timer.state.value shouldBe SleepTimerState(After(15), 890.0)
                }
            }
        }

        context("when the book changes") {
            // R7: a timed timer doesn't watch the book; the controller's own clear is what stops it
            it("#169 TC-9: keeps counting (P-SLP-5, I-SLP-3)") {
                checkAll(Exhaustive.of(playbackState(randomPlayingBook()), PlaybackState())) { next ->
                    sut().use { rig ->
                        rig.timer.set(After(15))
                        rig.settle()
                        rig.advance(MINUTE)

                        rig.push(next)
                        rig.advance(1_000)

                        rig.timer.state.value shouldBe SleepTimerState(After(15), 839.0)
                    }
                }
            }
        }
    }

    describe("expiry") {

        // R2
        it("#169 TC-5: pauses once at the deadline, not before, whatever the play state (P-SLP-2, P-SLP-AC1)") {
            checkAll(Exhaustive.boolean()) { playing ->
                sut(playbackState(randomPlayingBook(), isPlaying = playing)).use { rig ->
                    rig.timer.set(After(15))
                    rig.settle()

                    rig.advance(899_999)
                    rig.pauses() shouldBe 0

                    rig.advance(1)
                    rig.advance(10 * MINUTE)
                    rig.pauses() shouldBe 1
                }
            }
        }

        // R2: pause() re-enters the controller's mirror, which must find the timer gone
        it("#169 TC-5: has cleared the state by the time it pauses (P-SLP-2, P-SLP-AC1)") {
            sut().use { rig ->
                rig.timer.set(After(1))
                rig.settle()

                rig.advance(MINUTE)

                rig.seenAtPause shouldBe listOf(null)
            }
        }
    }

    describe("clearing and replacing") {

        context("when a timer is running") {
            // R3
            it("#169 TC-6: ends it without a pause, for each clearing option (P-SLP-5)") {
                checkAll(Exhaustive.of<SleepTimerOption?>(null, After(0), After(-5))) { option ->
                    sut().use { rig ->
                        rig.timer.set(After(15))
                        rig.settle()

                        rig.timer.set(option)
                        rig.advance(20 * MINUTE)

                        rig.timer.state.value.shouldBeNull()
                        rig.pauses() shouldBe 0
                    }
                }
            }

            // R3: a new option runs in full, and the same option restarts
            it("#169 TC-7: runs a new option in full, for a longer or the same option (P-SLP-5)") {
                checkAll(Exhaustive.of(15, 30)) { minutes ->
                    sut().use { rig ->
                        rig.timer.set(After(15))
                        rig.settle()
                        rig.advance(5 * MINUTE)

                        rig.timer.set(After(minutes))
                        rig.settle()
                        rig.advance(minutes * MINUTE - 1)
                        rig.pauses() shouldBe 0

                        rig.advance(1)
                        rig.pauses() shouldBe 1
                    }
                }
            }
        }
    }
})
