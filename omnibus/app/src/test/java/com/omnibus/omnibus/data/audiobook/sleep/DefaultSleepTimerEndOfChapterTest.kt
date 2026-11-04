package com.omnibus.omnibus.data.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.After
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.EndOfChapter
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.noChapterState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch

/**
 * The end-of-chapter option of [DefaultSleepTimer]: arming, tracking in book time, the boundary and the book change
 * (R3-R7). Each case sets the fake's state first, then arms, then settles so the watch starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSleepTimerEndOfChapterTest : DescribeSpec({

    val minute = 60_000L

    describe("arming") {
        // R3, R4: arming replaces a running timed option, and the old one never fires
        it("#169 TC-10: arming replaces a timed option, and a timed option replaces the armed chapter (P-SLP-3, P-SLP-5)") {
            val replacedByTimed = SleepTimerRig(playbackState(positionSeconds = 100.0))
            replacedByTimed.timer.set(EndOfChapter)
            replacedByTimed.settle()
            replacedByTimed.timer.set(After(15))
            replacedByTimed.settle()
            replacedByTimed.push(playbackState(positionSeconds = 1900.0))
            replacedByTimed.timer.state.value shouldBe SleepTimerState(After(15), 900.0)
            replacedByTimed.pauses() shouldBe 0
            replacedByTimed.advance(15 * minute)
            replacedByTimed.pauses() shouldBe 1
            replacedByTimed.close()

            val replacedByChapter = SleepTimerRig(playbackState(positionSeconds = 100.0))
            replacedByChapter.timer.set(After(15))
            replacedByChapter.settle()
            replacedByChapter.timer.set(EndOfChapter)
            replacedByChapter.settle()
            replacedByChapter.advance(16 * minute)
            replacedByChapter.timer.state.value shouldBe SleepTimerState(EndOfChapter, 500.0)
            replacedByChapter.pauses() shouldBe 0
            replacedByChapter.close()
        }

        // R4: the remaining time is the chapter's end minus the position, divided by the rate, with a rate of 0 or less as 1
        it("#169 TC-11: the remaining time divides by the rate, and a rate of 0 or less counts as 1 (P-SLP-3, I-SLP-2)") {
            val cases = listOf(1.0 to 500.0, 2.0 to 250.0, 0.0 to 500.0, -1.0 to 500.0)
            for ((rate, expected) in cases) {
                val rig = SleepTimerRig(playbackState(positionSeconds = 100.0, rate = rate))
                rig.timer.set(EndOfChapter)
                rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, expected)
                rig.close()
            }
        }

        // R4: the end is capped at the book's duration
        it("#169 TC-12: the armed end is capped at the book's duration (I-SLP-2)") {
            val rig = SleepTimerRig(
                playbackState(
                    chapters = chapters(600.0, 1200.0, 1000.0),
                    positionSeconds = 2600.0,
                    durationSeconds = 2700.0,
                ),
            )
            rig.timer.set(EndOfChapter)
            rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 100.0)
            rig.push(playbackState(chapters = chapters(600.0, 1200.0, 1000.0), positionSeconds = 2700.0, durationSeconds = 2700.0))
            rig.pauses() shouldBe 1
            rig.close()
        }

        // R3, R4: a chapter can't be armed, so the option clears the timer, and nothing fires later
        it("#169 TC-13: an end of chapter that can't be armed clears the timer and never pauses (P-SLP-5)") {
            val unarmable = listOf(
                noChapterState(),
                PlaybackState(),
                playbackState(isLoading = true),
                playbackState(chapters = chapters(600.0, 0.0), positionSeconds = 700.0, durationSeconds = 2700.0),
            )
            for (state in unarmable) {
                val idle = SleepTimerRig(state)
                idle.timer.set(EndOfChapter)
                idle.timer.state.value.shouldBeNull()
                idle.push(playbackState(positionSeconds = 1900.0))
                idle.advance(20 * minute)
                idle.pauses() shouldBe 0
                idle.close()

                val running = SleepTimerRig(playbackState())
                running.timer.set(After(15))
                running.settle()
                running.advance(minute)
                running.fake.state.value = state
                running.timer.set(EndOfChapter)
                running.timer.state.value.shouldBeNull()
                running.push(playbackState(positionSeconds = 1900.0))
                running.advance(20 * minute)
                running.pauses() shouldBe 0
                running.close()
            }
        }
    }

    describe("tracking in book time") {
        // R5: a seek back extends the wait, and paused book time holds still
        it("#169 TC-14: the remaining time follows the position and the rate, and holds while paused (I-SLP-2)") {
            val rig = SleepTimerRig(playbackState(positionSeconds = 100.0, isPlaying = false))
            rig.timer.set(EndOfChapter)
            rig.settle()
            rig.push(playbackState(positionSeconds = 300.0))
            rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 300.0)
            rig.push(playbackState(positionSeconds = 599.0))
            rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 1.0)
            rig.push(playbackState(positionSeconds = 300.0, rate = 2.0))
            rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 150.0)
            rig.push(playbackState(positionSeconds = 100.0, rate = 1.0))
            rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 500.0)
            rig.advance(10 * minute)
            rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 500.0)
            rig.pauses() shouldBe 0
            rig.close()
        }

        // R5: the boundary is 0.05 s early, so a position a tenth of a second short does not fire yet
        it("#169 TC-15: the pause fires at the boundary within 0.05 s, and not before (I-SLP-2)") {
            val rig = SleepTimerRig(playbackState(positionSeconds = 100.0, isPlaying = false))
            rig.timer.set(EndOfChapter)
            rig.settle()
            rig.push(playbackState(positionSeconds = 599.90, isPlaying = false))
            rig.timer.state.value?.option shouldBe EndOfChapter
            rig.timer.state.value!!.remainingSeconds shouldBe (0.1 plusOrMinus 1e-9)
            rig.pauses() shouldBe 0
            rig.push(playbackState(positionSeconds = 599.96, isPlaying = false))
            rig.timer.state.value.shouldBeNull()
            rig.pauses() shouldBe 1
            rig.close()
        }

        // R5: every position at or past the end fires, whether it is the boundary, past it, the book's end or the engine's end
        it("#169 TC-16: a position at or past the end fires once, including the book's end (I-SLP-2)") {
            val positions = listOf(
                playbackState(positionSeconds = 600.0, isPlaying = false),
                playbackState(positionSeconds = 600.0 - 1e-9, isPlaying = false),
                playbackState(positionSeconds = 1900.0, isPlaying = false),
                playbackState(positionSeconds = 2700.0, isPlaying = false),
                playbackState(positionSeconds = 2700.0, isPlaying = false),
            )
            for (state in positions) {
                val rig = SleepTimerRig(playbackState(positionSeconds = 100.0, isPlaying = false))
                rig.timer.set(EndOfChapter)
                rig.settle()
                rig.push(state)
                rig.timer.state.value.shouldBeNull()
                rig.pauses() shouldBe 1
                rig.close()
            }
        }

        // R5: a loading state is ignored: it neither fires nor clears
        it("#169 TC-17: a loading state is ignored, neither firing nor clearing the armed chapter (I-SLP-2)") {
            val rig = SleepTimerRig(playbackState(positionSeconds = 100.0, isPlaying = false))
            rig.timer.set(EndOfChapter)
            rig.settle()
            rig.push(playbackState(isLoading = true, positionSeconds = 0.0, chapters = emptyList()))
            rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 500.0)
            rig.pauses() shouldBe 0
            rig.push(playbackState(positionSeconds = 300.0, isPlaying = false))
            rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 300.0)
            rig.close()
        }
    }

    describe("the precise boundary") {
        // R6: while playing, the pause lands on the boundary, not on the next position update
        it("#169 TC-18: a playing book pauses on the boundary, not up to a tick later (I-SLP-2)") {
            val normal = SleepTimerRig(playbackState(positionSeconds = 599.25, isPlaying = true))
            normal.timer.set(EndOfChapter)
            normal.settle()
            normal.advance(749)
            normal.pauses() shouldBe 0
            normal.advance(1)
            normal.pauses() shouldBe 1
            normal.timer.state.value.shouldBeNull()
            normal.close()

            val fast = SleepTimerRig(playbackState(positionSeconds = 598.5, rate = 3.0, isPlaying = true))
            fast.timer.set(EndOfChapter)
            fast.settle()
            fast.advance(499)
            fast.pauses() shouldBe 0
            fast.advance(1)
            fast.pauses() shouldBe 1
            fast.close()
        }

        // R6: outside the one-second window nothing is scheduled; the watch waits for a state
        it("#169 TC-19: outside the one-second window no pause is scheduled (I-SLP-2)") {
            val rig = SleepTimerRig(playbackState(positionSeconds = 590.0, isPlaying = true))
            rig.timer.set(EndOfChapter)
            rig.settle()
            rig.advance(5_000)
            rig.pauses() shouldBe 0
            rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 10.0)
            rig.close()
        }

        // R6: a state that stops, buffers, loads or errs cancels the scheduled pause
        it("#169 TC-20: pausing, buffering, loading or failing cancels the scheduled boundary pause (I-SLP-2)") {
            val stops = listOf(
                playbackState(positionSeconds = 599.0, isPlaying = false),
                playbackState(positionSeconds = 599.0, isPlaying = true, isBuffering = true),
                playbackState(positionSeconds = 599.0, isLoading = true),
                playbackState(positionSeconds = 599.0, isPlaying = true, error = PlaybackError.TimelineFailed),
            )
            for (state in stops) {
                val rig = SleepTimerRig(playbackState(positionSeconds = 599.0, isPlaying = true))
                rig.timer.set(EndOfChapter)
                rig.settle()
                rig.advance(200)
                rig.push(state)
                rig.advance(5_000)
                rig.pauses() shouldBe 0
                rig.close()
            }
        }

        // R6: a new position reschedules the pause; an echo of the same position does not push it later
        it("#169 TC-21: a new position reschedules the pause, and the mirror's echo keeps it (I-SLP-2)") {
            val moved = SleepTimerRig(playbackState(positionSeconds = 599.0, isPlaying = true))
            moved.timer.set(EndOfChapter)
            moved.settle()
            moved.advance(200)
            moved.push(playbackState(positionSeconds = 599.4, isPlaying = true))
            moved.advance(599)
            moved.pauses() shouldBe 0
            moved.advance(1)
            moved.pauses() shouldBe 1
            moved.close()

            val echoed = SleepTimerRig(playbackState(positionSeconds = 599.0, isPlaying = true))
            echoed.timer.set(EndOfChapter)
            echoed.settle()
            echoed.advance(200)
            echoed.push(playbackState(positionSeconds = 599.0, isPlaying = true, sleepTimer = echoed.timer.state.value))
            echoed.advance(799)
            echoed.pauses() shouldBe 0
            echoed.advance(1)
            echoed.pauses() shouldBe 1
            echoed.close()
        }

        // R5, R6, R7: the watch fires once, the echo and stray states after the pause do nothing, and a steady state settles
        it("#169 TC-22: the watch fires once, and steady or echoed states emit nothing new (I-SLP-2, I-SLP-3)") {
            val fired = SleepTimerRig(playbackState(positionSeconds = 599.0, isPlaying = true))
            fired.timer.set(EndOfChapter)
            fired.settle()
            fired.advance(1_000)
            fired.pauses() shouldBe 1
            fired.push(playbackState(positionSeconds = 600.0, isPlaying = false))
            fired.push(playbackState(positionSeconds = 600.2, isPlaying = true))
            fired.pauses() shouldBe 1
            fired.timer.state.value.shouldBeNull()
            fired.close()

            val steady = SleepTimerRig(playbackState(positionSeconds = 100.0))
            val values = mutableListOf<SleepTimerState?>()
            steady.scope.launch { steady.timer.state.collect { values += it } }
            steady.settle()
            steady.timer.set(EndOfChapter)
            steady.settle()
            for (i in 0 until 50) {
                steady.push(playbackState(positionSeconds = 100.0))
                steady.push(playbackState(positionSeconds = 100.0, sleepTimer = steady.timer.state.value))
            }
            values shouldBe listOf(null, SleepTimerState(EndOfChapter, 500.0))
            steady.pauses() shouldBe 0
            steady.close()
        }
    }

    describe("the book") {
        // R7: a different or null book clears the armed chapter without pausing
        it("#169 TC-23: a different or null book clears the armed chapter without pausing (I-SLP-3)") {
            val otherBook = SleepTimerRig(playbackState(positionSeconds = 100.0))
            otherBook.timer.set(EndOfChapter)
            otherBook.settle()
            otherBook.push(playbackState(book = playingBook("book-2"), positionSeconds = 1900.0))
            otherBook.timer.state.value.shouldBeNull()
            otherBook.pauses() shouldBe 0
            otherBook.push(playbackState(positionSeconds = 1900.0))
            otherBook.pauses() shouldBe 0
            otherBook.close()

            val nothingLoaded = SleepTimerRig(playbackState(positionSeconds = 100.0))
            nothingLoaded.timer.set(EndOfChapter)
            nothingLoaded.settle()
            nothingLoaded.push(PlaybackState())
            nothingLoaded.timer.state.value.shouldBeNull()
            nothingLoaded.pauses() shouldBe 0
            nothingLoaded.close()
        }
    }
})
