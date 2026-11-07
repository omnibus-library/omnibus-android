package com.omnibus.omnibus.feature.audiobook.carmode

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

class CarModeUIStateTest : DescribeSpec({

    describe("the title") {
        // P-CAR-2
        it("#170 TC-1: is the current chapter's title") {
            val second = randomString()
            val chapters = chapters(600.0, 1200.0, titles = listOf(randomString(), second))
            val state = playbackState(book = randomPlayingBook(), chapters = chapters, positionSeconds = 700.0)

            carModeUIState(state).title shouldBe second
        }

        // P-CAR-2
        it("#170 TC-2 TC-3: falls back to the book's title, for no chapters or a blank chapter title") {
            val book = randomPlayingBook()

            checkAll(Exhaustive.of(emptyList(), chapters(600.0, titles = listOf(" ")))) { chapters ->
                carModeUIState(playbackState(book = book, chapters = chapters)).title shouldBe book.title
            }
        }

        context("when nothing is loaded") {
            // P-CAR-2
            it("#170 TC-4: is empty") {
                carModeUIState(PlaybackState()) shouldBe CarModeUIState()
            }
        }
    }

    describe("the time left") {
        // P-CAR-2, I-TL-5
        it("#170 TC-5: is the whole book's time left, divided by the speed") {
            val state = playbackState(
                book = randomPlayingBook(),
                chapters = chapters(600.0, 1200.0, 900.0),
                positionSeconds = 700.0,
                rate = 1.5,
            )

            carModeUIState(state).bookSecondsLeftAtRate shouldBe (2700.0 - 700.0) / 1.5
        }
    }

    describe("the play state") {
        // P-CAR-3
        it("#170 TC-7: follows the controller") {
            checkAll(Exhaustive.boolean()) { isPlaying ->
                carModeUIState(playbackState(book = randomPlayingBook(), isPlaying = isPlaying)).isPlaying shouldBe isPlaying
            }
        }
    }

    describe("the chapter tiles") {
        // P-CAR-4, P-PLAY-4, P-PLAY-5, P-CAR-AC2, edge case: no chapters
        it("#170 TC-8 TC-10: are enabled in the middle of a book, disabled without chapters") {
            val book = randomPlayingBook()

            checkAll(
                Exhaustive.of(
                    playbackState(book = book, chapters = chapters(600.0, 1200.0, 900.0), positionSeconds = 700.0) to true,
                    playbackState(book = book, chapters = emptyList()) to false,
                ),
            ) { (state, expected) ->
                val ui = carModeUIState(state)

                ui.canPreviousChapter shouldBe expected
                ui.canNextChapter shouldBe expected
            }
        }
    }
})
