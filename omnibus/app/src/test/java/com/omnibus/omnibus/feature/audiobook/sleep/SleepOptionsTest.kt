package com.omnibus.omnibus.feature.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.SleepTimerOption
import com.omnibus.omnibus.data.audiobook.playback.SleepTimerState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.noChapterState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** The sheet's pure rules: the presets, the choices a book offers, and which cell is selected (R10, R11). */
class SleepOptionsTest : DescribeSpec({

    describe("the choices") {
        // R10: Off, the seven presets, then the chapter cell last, numbered from 1
        it("#169 TC-28: the presets are Off, seven timed options, then end of chapter last (P-SLP-1)") {
            SleepTimedMinutes shouldBe listOf(15, 30, 45, 60, 120, 180, 240)
            sleepChoices(playbackState(positionSeconds = 100.0)) shouldBe listOf(
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
        it("#169 TC-29: no chapter cell without chapters, nothing loaded, loading, or a chapter of length 0 (P-SLP-1)") {
            val states = listOf(
                noChapterState(),
                PlaybackState(),
                playbackState(isLoading = true),
                playbackState(chapters = chapters(600.0, 0.0), positionSeconds = 700.0, durationSeconds = 2700.0),
            )
            for (state in states) {
                val choices = sleepChoices(state)
                choices.size shouldBe 8
                choices.none { it is SleepChoice.EndOfChapter } shouldBe true
            }
        }

        // R10: the chapter cell numbers the chapter by its position in the list, not its ordinal
        it("#169 TC-30: the chapter cell is the chapter's 1-based list position, not its ordinal (P-SLP-1)") {
            sleepChoices(playbackState(positionSeconds = 700.0)).last() shouldBe SleepChoice.EndOfChapter(2)
            sleepChoices(playbackState(positionSeconds = 1900.0)).last() shouldBe SleepChoice.EndOfChapter(3)
            val reordered = chapters(600.0, 1200.0, 900.0).mapIndexed { index, chapter ->
                chapter.copy(ordinal = listOf(7L, 3L, 9L)[index])
            }
            sleepChoices(playbackState(chapters = reordered, positionSeconds = 100.0)).last() shouldBe
                SleepChoice.EndOfChapter(1)
        }
    }

    describe("the selection") {
        // R11: the running option marks its cell; a timer with no cell marks none
        it("#169 TC-31: the selected cell matches the running option, and an option with no cell selects nothing (P-SLP-5)") {
            val nine = sleepChoices(playbackState(positionSeconds = 100.0))
            selectedChoice(null, nine) shouldBe SleepChoice.Off
            selectedChoice(SleepTimerState(SleepTimerOption.After(45), 1.0), nine) shouldBe SleepChoice.Timed(45)
            selectedChoice(SleepTimerState(SleepTimerOption.EndOfChapter, 5.0), nine) shouldBe SleepChoice.EndOfChapter(1)
            selectedChoice(SleepTimerState(SleepTimerOption.After(7), 1.0), nine).shouldBeNull()

            val eight = sleepChoices(noChapterState())
            selectedChoice(SleepTimerState(SleepTimerOption.EndOfChapter, 5.0), eight).shouldBeNull()

            SleepChoice.Off.toOption().shouldBeNull()
            SleepChoice.Timed(30).toOption() shouldBe SleepTimerOption.After(30)
            SleepChoice.EndOfChapter(2).toOption() shouldBe SleepTimerOption.EndOfChapter
        }
    }
})
