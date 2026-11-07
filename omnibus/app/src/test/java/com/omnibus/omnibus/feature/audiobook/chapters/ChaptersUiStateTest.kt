package com.omnibus.omnibus.feature.audiobook.chapters

import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import com.omnibus.omnibus.data.audiobook.playback.randomPlayingBook
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

class ChaptersUiStateTest : DescribeSpec({

    describe("the sheet's rows") {
        // P-CHP-2: the 1-based number, the title and the length
        it("has one row per chapter with its number, title, length") {
            val first = randomString()
            val second = randomString()
            val state = playbackState(chapters = chapters(600.0, 3700.0, titles = listOf(first, second)))

            chaptersUiState(state, Locale.US).rows shouldBe listOf(
                ChapterRowModel(1, first, "10:00"),
                ChapterRowModel(2, second, "1:01:40"),
            )
        }
    }

    describe("the current chapter") {
        // P-CHP-3: the current row is the chapter at the position, and its progress is the share of it played
        it("is the chapter at the position, with the share of it played") {
            val state = playbackState(chapters = chapters(600.0, 1200.0, 900.0), positionSeconds = 900.0)

            val ui = chaptersUiState(state, Locale.US)

            ui.currentIndex shouldBe 1
            ui.currentProgress shouldBe 0.25f
        }

        context("when the chapter has no length") {
            // P-CHP-3: a chapter with no length has no line to fill
            it("has zero progress") {
                val state = playbackState(chapters = chapters(0.0), positionSeconds = 5.0, durationSeconds = 100.0)

                chaptersUiState(state, Locale.US).currentProgress shouldBe 0f
            }
        }

        context("when the book has no chapters") {
            // P-CHP edge case: no chapters means no rows and no current chapter
            it("is empty") {
                val state = playbackState(book = randomPlayingBook(), chapters = emptyList(), positionSeconds = 120.0)

                chaptersUiState(state, Locale.US) shouldBe ChaptersUiState()
            }
        }
    }
})
