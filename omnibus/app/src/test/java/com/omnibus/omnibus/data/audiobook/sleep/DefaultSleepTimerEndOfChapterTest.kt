package com.omnibus.omnibus.data.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.PlaybackError
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.After
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption.EndOfChapter
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.noChapterState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.testing.randomDouble
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private const val MINUTE = 60_000L

/** A book whose first chapter ends at 600 s, at [positionSeconds]. */
private fun inBook(
    book: PlayingBook,
    positionSeconds: Double,
    rate: Double = 1.0,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false,
    isLoading: Boolean = false,
    error: PlaybackError? = null,
    sleepTimer: SleepTimerState? = null,
) = playbackState(
    book = book,
    chapters = chapters(600.0, 1200.0, 900.0),
    positionSeconds = positionSeconds,
    rate = rate,
    isPlaying = isPlaying,
    isBuffering = isBuffering,
    isLoading = isLoading,
    error = error,
    sleepTimer = sleepTimer,
)

/**
 * The end-of-chapter option of [DefaultSleepTimer]: arming, tracking in book time, the boundary and the book change
 * (R3-R7). Each case holds its state first, then arms, then settles so the watch starts.
 */
class DefaultSleepTimerEndOfChapterTest : DescribeSpec({

    fun sut(initial: PlaybackState) = SleepTimerRig(initial)

    describe("arming") {

        // R4: a rate of 0 or less counts as 1
        it("#169 TC-11: shows the time left to the chapter's end, for each rate (P-SLP-3, I-SLP-2)") {
            checkAll(Exhaustive.of(1.0 to 500.0, 2.0 to 250.0, 0.0 to 500.0, -1.0 to 500.0)) { (rate, remaining) ->
                sut(inBook(randomPlayingBook(), 100.0, rate = rate)).use { rig ->
                    rig.timer.set(EndOfChapter)

                    rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, remaining)
                }
            }
        }

        context("when the chapter runs past the book's end") {
            // R4
            it("#169 TC-12: counts to the book's end (I-SLP-2)") {
                val state = playbackState(
                    book = randomPlayingBook(),
                    chapters = chapters(600.0, 1200.0, 1000.0),
                    positionSeconds = 2600.0,
                    durationSeconds = 2700.0,
                )
                sut(state).use { rig ->
                    rig.timer.set(EndOfChapter)

                    rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 100.0)
                }
            }
        }

        context("when a timed option is running") {
            // R3, R4
            it("#169 TC-10: replaces it (P-SLP-3, P-SLP-5)") {
                sut(inBook(randomPlayingBook(), 100.0)).use { rig ->
                    rig.timer.set(After(15))
                    rig.settle()

                    rig.timer.set(EndOfChapter)
                    rig.settle()
                    rig.advance(16 * MINUTE)

                    rig.pauses() shouldBe 0
                }
            }
        }

        // R3, R4: a chapter can't be armed, so the option clears the timer
        it("#169 TC-13: ends a running timer without a pause, for each state with no chapter to arm (P-SLP-5)") {
            val book = randomPlayingBook()
            checkAll(
                Exhaustive.of(
                    noChapterState(),
                    PlaybackState(),
                    inBook(book, 100.0, isLoading = true),
                    playbackState(book, chapters = chapters(600.0, 0.0), positionSeconds = 700.0, durationSeconds = 2700.0),
                ),
            ) { state ->
                sut(state).use { rig ->
                    rig.timer.set(After(15))
                    rig.settle()

                    rig.timer.set(EndOfChapter)
                    rig.advance(20 * MINUTE)

                    rig.timer.state.value.shouldBeNull()
                    rig.pauses() shouldBe 0
                }
            }
        }
    }

    describe("tracking in book time") {

        // R5: a seek back extends the wait
        it("#169 TC-14: follows each new position, at its rate (I-SLP-2)") {
            checkAll(Exhaustive.of(Triple(300.0, 1.0, 300.0), Triple(300.0, 2.0, 150.0), Triple(50.0, 1.0, 550.0))) {
                    (position, rate, remaining) ->
                val book = randomPlayingBook()
                sut(inBook(book, 100.0)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()

                    rig.push(inBook(book, position, rate = rate))

                    rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, remaining)
                }
            }
        }

        // R5: the boundary is 0.05 s early
        it("#169 TC-15: does not fire a tenth of a second short of the end (I-SLP-2)") {
            val book = randomPlayingBook()
            sut(inBook(book, 100.0)).use { rig ->
                rig.timer.set(EndOfChapter)
                rig.settle()

                rig.push(inBook(book, 599.90))

                rig.timer.state.value!!.remainingSeconds shouldBe (0.1 plusOrMinus 1e-9)
            }
        }

        // R5: within the boundary, at it, past it or at the book's end
        it("#169 TC-15, TC-16: pauses once at each position at or past the end (I-SLP-2)") {
            checkAll(Exhaustive.of(599.96, 600.0, 1900.0, 2700.0)) { position ->
                val book = randomPlayingBook()
                sut(inBook(book, 100.0)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()

                    rig.push(inBook(book, position))

                    rig.pauses() shouldBe 1
                }
            }
        }

        context("when the state is loading") {
            // R5
            it("#169 TC-17: neither fires nor clears (I-SLP-2)") {
                val book = randomPlayingBook()
                sut(inBook(book, 100.0)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()

                    rig.push(playbackState(book, isLoading = true, positionSeconds = 0.0, chapters = emptyList()))

                    rig.timer.state.value shouldBe SleepTimerState(EndOfChapter, 500.0)
                }
            }
        }
    }

    describe("the precise boundary") {

        // R6: the pause lands on the boundary, not on the next position update
        it("#169 TC-18: pauses a playing book on the boundary, for each rate (I-SLP-2)") {
            checkAll(Exhaustive.of(Triple(599.25, 1.0, 750L), Triple(598.5, 3.0, 500L))) { (position, rate, millis) ->
                sut(inBook(randomPlayingBook(), position, rate = rate, isPlaying = true)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()

                    rig.advance(millis - 1)
                    rig.pauses() shouldBe 0

                    rig.advance(1)
                    rig.pauses() shouldBe 1
                }
            }
        }

        context("when the end is over a second away") {
            // R6: the watch waits for a state
            it("#169 TC-19: schedules no pause (I-SLP-2)") {
                sut(inBook(randomPlayingBook(), 590.0, isPlaying = true)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()

                    rig.advance(5_000)

                    rig.pauses() shouldBe 0
                }
            }
        }

        // R6
        it("#169 TC-20: cancels the pause for each state that stops the audio (I-SLP-2)") {
            val book = randomPlayingBook()
            checkAll(
                Exhaustive.of(
                    inBook(book, 599.0),
                    inBook(book, 599.0, isPlaying = true, isBuffering = true),
                    inBook(book, 599.0, isLoading = true),
                    inBook(book, 599.0, isPlaying = true, error = PlaybackError.TimelineFailed),
                ),
            ) { stopped ->
                sut(inBook(book, 599.0, isPlaying = true)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()
                    rig.advance(200)

                    rig.push(stopped)
                    rig.advance(5_000)

                    rig.pauses() shouldBe 0
                }
            }
        }

        context("when a new position arrives") {
            // R6
            it("#169 TC-21: reschedules the pause (I-SLP-2)") {
                val book = randomPlayingBook()
                sut(inBook(book, 599.0, isPlaying = true)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()
                    rig.advance(200)

                    rig.push(inBook(book, 599.4, isPlaying = true))
                    rig.advance(599)
                    rig.pauses() shouldBe 0

                    rig.advance(1)
                    rig.pauses() shouldBe 1
                }
            }
        }

        context("when the controller echoes the same position") {
            // R6: the controller mirrors the timer's state back, which must not push the pause later
            it("#169 TC-21: keeps the pause on time (I-SLP-2)") {
                val book = randomPlayingBook()
                sut(inBook(book, 599.0, isPlaying = true)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()
                    rig.advance(200)

                    rig.push(inBook(book, 599.0, isPlaying = true, sleepTimer = rig.timer.state.value))
                    rig.advance(800)

                    rig.pauses() shouldBe 1
                }
            }
        }

        context("when the pause has fired") {
            // R5, R6, R7
            it("#169 TC-22: fires no more for later positions (I-SLP-2, I-SLP-3)") {
                val book = randomPlayingBook()
                sut(inBook(book, 599.0, isPlaying = true)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()
                    rig.advance(1_000)

                    rig.push(inBook(book, randomDouble(600.0, 2_700.0), isPlaying = true))

                    rig.pauses() shouldBe 1
                }
            }
        }
    }

    describe("the book changing") {

        // R7
        it("#169 TC-23: clears the armed chapter without a pause, for another book or none (I-SLP-3)") {
            val book = randomPlayingBook()
            checkAll(Exhaustive.of(inBook(book.copy(bookUuid = "${book.bookUuid}-other"), 1900.0), PlaybackState())) { next ->
                sut(inBook(book, 100.0)).use { rig ->
                    rig.timer.set(EndOfChapter)
                    rig.settle()

                    rig.push(next)

                    rig.timer.state.value.shouldBeNull()
                    rig.pauses() shouldBe 0
                }
            }
        }
    }
})
