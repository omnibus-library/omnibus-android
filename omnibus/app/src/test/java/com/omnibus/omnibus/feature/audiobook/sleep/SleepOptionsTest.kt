package com.omnibus.omnibus.feature.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.testing.randomDouble
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** The sheet's pure rules: the choices a book offers, and which cell is selected (R10, R11). */
class SleepOptionsTest : DescribeSpec({

    fun timer(option: SleepTimerOption) = SleepTimerState(option, randomDouble(1.0, 7_200.0))

    fun loaded(positionSeconds: Double = 100.0) = playbackState(book = randomPlayingBook(), positionSeconds = positionSeconds, isLoading = false)

    describe("the choices") {
        // R10: Off, the seven presets, then the chapter cell last, numbered from 1
        it("#169 TC-28: are Off, seven timed options, then end of chapter last (P-SLP-1)") {
            sleepChoices(loaded()) shouldBe listOf(
                SleepChoice.Off,
                SleepChoice.Timed(15),
                SleepChoice.Timed(30),
                SleepChoice.Timed(45),
                SleepChoice.Timed(60),
                SleepChoice.Timed(120),
                SleepChoice.Timed(180),
                SleepChoice.Timed(240),
                SleepChoice.EndOfChapter(1),
            )
        }

        // R10: a book with no chapter to arm has the eight presets and no chapter cell
        it("#169 TC-29: have no chapter cell for no chapters, nothing loaded, a load in flight or a chapter of length 0 (P-SLP-1)") {
            checkAll(
                Exhaustive.of(
                    playbackState(book = randomPlayingBook(), chapters = emptyList(), isLoading = false),
                    PlaybackState(),
                    playbackState(book = randomPlayingBook(), isLoading = true),
                    playbackState(book = randomPlayingBook(), chapters = chapters(600.0, 0.0), positionSeconds = 700.0, durationSeconds = 2700.0, isLoading = false),
                ),
            ) { state ->
                sleepChoices(state).map { it is SleepChoice.EndOfChapter } shouldNotContain true
            }
        }

        // R10: the chapter cell numbers the chapter by its position in the list, not its ordinal
        it("#169 TC-30: number the chapter cell by its 1-based list position, not its ordinal (P-SLP-1)") {
            val reordered = chapters(600.0, 1200.0).mapIndexed { index, chapter ->
                chapter.copy(ordinal = listOf(7L, 3L)[index])
            }
            val state = playbackState(book = randomPlayingBook(), chapters = reordered, positionSeconds = 700.0, isLoading = false)

            sleepChoices(state).last() shouldBe SleepChoice.EndOfChapter(2)
        }
    }

    describe("the selection") {
        // R11: the running option marks its cell; a timer with no cell marks none
        it("#169 TC-31: is the running option's cell, or nothing for an option with no cell (P-SLP-5)") {
            val nine = sleepChoices(loaded())
            val eight = sleepChoices(playbackState(book = randomPlayingBook(), chapters = emptyList(), isLoading = false))

            checkAll(
                Exhaustive.of(
                    Triple(null, nine, SleepChoice.Off),
                    Triple(timer(SleepTimerOption.After(45)), nine, SleepChoice.Timed(45)),
                    Triple(timer(SleepTimerOption.EndOfChapter), nine, SleepChoice.EndOfChapter(1)),
                    Triple(timer(SleepTimerOption.After(7)), nine, null),
                    Triple(timer(SleepTimerOption.EndOfChapter), eight, null),
                ),
            ) { (running, choices, expected) ->
                selectedChoice(running, choices) shouldBe expected
            }
        }
    }
})
