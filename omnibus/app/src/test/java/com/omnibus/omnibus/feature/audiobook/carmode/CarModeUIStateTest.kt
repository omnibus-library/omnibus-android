package com.omnibus.omnibus.feature.audiobook.carmode

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.noChapterState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.playingBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CarModeUIStateTest : DescribeSpec({

    describe("title") {

        // P-CAR-2
        it("#170 TC-1: shows the current chapter's title") {
            val state = playbackState(positionSeconds = 700.0)

            carModeUIState(state).title shouldBe "Chapter 2"
        }

        // P-CAR-2
        it("#170 TC-2: falls back to the book's title without chapters") {
            val state = noChapterState().copy(book = playingBook(title = "The Long Tide"))

            carModeUIState(state).title shouldBe "The Long Tide"
        }

        // P-CAR-2
        it("#170 TC-3: falls back to the book's title when the chapter's title is blank") {
            val state = playbackState(
                book = playingBook(title = "The Long Tide"),
                chapters = chapters(600.0, 600.0, titles = listOf(" ", "Two")),
                positionSeconds = 10.0,
            )

            carModeUIState(state).title shouldBe "The Long Tide"
        }

        // P-CAR-2
        it("#170 TC-4: is empty with nothing loaded") {
            carModeUIState(PlaybackState()) shouldBe CarModeUIState()
        }
    }

    describe("time left") {

        // P-CAR-2, I-TL-5
        it("#170 TC-5: is the whole book's time left, divided by the speed") {
            val state = playbackState(positionSeconds = 700.0, rate = 1.5)

            carModeUIState(state).bookSecondsLeftAtRate shouldBe (2700.0 - 700.0) / 1.5
        }

        // P-CAR-2
        it("#170 TC-6: counts the whole book, not the chapter") {
            val state = playbackState(positionSeconds = 100.0)

            carModeUIState(state).bookSecondsLeftAtRate shouldBe 2600.0
        }
    }

    describe("play state and chapter tiles") {

        // P-CAR-3
        it("#170 TC-7: follows isPlaying") {
            carModeUIState(playbackState(isPlaying = true)).isPlaying shouldBe true
            carModeUIState(playbackState(isPlaying = false)).isPlaying shouldBe false
        }

        // P-CAR-4, P-PLAY-4, P-PLAY-5
        it("#170 TC-8: the chapter tiles are enabled in the middle of a book") {
            val ui = carModeUIState(playbackState(positionSeconds = 700.0))

            ui.canPreviousChapter shouldBe true
            ui.canNextChapter shouldBe true
        }

        // P-CAR-4, P-PLAY-5
        it("#170 TC-9: next is disabled on the last chapter") {
            val ui = carModeUIState(playbackState(positionSeconds = 2000.0))

            ui.canNextChapter shouldBe false
            ui.canPreviousChapter shouldBe true
        }

        // P-CAR-AC2, edge case: no chapters
        it("#170 TC-10: both chapter tiles are disabled without chapters") {
            val ui = carModeUIState(noChapterState(positionSeconds = 100.0))

            ui.canPreviousChapter shouldBe false
            ui.canNextChapter shouldBe false
        }
    }
})
