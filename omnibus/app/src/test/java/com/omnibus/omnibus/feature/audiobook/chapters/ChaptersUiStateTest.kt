package com.omnibus.omnibus.feature.audiobook.chapters

import com.omnibus.omnibus.data.audiobook.playback.chapters
import com.omnibus.omnibus.data.audiobook.playback.noChapterState
import com.omnibus.omnibus.data.audiobook.playback.playbackState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

class ChaptersUiStateTest : DescribeSpec({

    describe("chaptersUiState") {

        // P-CHP-2: the 1-based number, the title and the length
        it("builds one row per chapter with its number, title and length") {
            val state = playbackState(
                chapters = chapters(600.0, 1200.0, 3700.0, titles = listOf("Intro", "Middle", "End")),
            )

            chaptersUiState(state, Locale.US).rows shouldBe listOf(
                ChapterRowModel(1, "Intro", "10:00"),
                ChapterRowModel(2, "Middle", "20:00"),
                ChapterRowModel(3, "End", "1:01:40"),
            )
        }

        // P-CHP-2: m:ss under an hour, h:mm:ss from an hour, floored
        it("formats lengths as m:ss, or h:mm:ss from an hour, flooring the seconds") {
            val rows = chaptersUiState(
                playbackState(chapters = chapters(7.9, 3599.9, 3600.0)),
                Locale.US,
            ).rows

            rows.map { it.lengthLabel } shouldBe listOf("0:07", "59:59", "1:00:00")
        }

        // P-CHP-3: the current row is the chapter at the position, and its progress is the share of it played
        it("marks the chapter at the position as current, with its progress") {
            val state = playbackState(chapters = chapters(600.0, 1200.0, 900.0), positionSeconds = 900.0)

            val ui = chaptersUiState(state, Locale.US)

            ui.currentIndex shouldBe 1
            ui.currentProgress shouldBe 0.25f
        }

        // P-CHP-3: a chapter's start belongs to it, so a position on the boundary is its beginning
        it("treats a boundary position as the start of the next chapter") {
            val ui = chaptersUiState(
                playbackState(chapters = chapters(600.0, 1200.0), positionSeconds = 600.0),
                Locale.US,
            )

            ui.currentIndex shouldBe 1
            ui.currentProgress shouldBe 0f
        }

        // P-CHP-3: the progress follows the position while playing
        it("moves the progress as the position advances") {
            val book = chapters(1000.0, 1000.0)

            val early = chaptersUiState(playbackState(chapters = book, positionSeconds = 1100.0), Locale.US)
            val later = chaptersUiState(playbackState(chapters = book, positionSeconds = 1600.0), Locale.US)

            early.currentProgress shouldBe 0.1f
            later.currentProgress shouldBe 0.6f
        }

        // P-CHP-3: past the end of the last chapter the line is full, never beyond
        it("clamps the progress to one at the end of the last chapter") {
            val ui = chaptersUiState(
                playbackState(chapters = chapters(600.0, 1200.0), positionSeconds = 1800.0),
                Locale.US,
            )

            ui.currentIndex shouldBe 1
            ui.currentProgress shouldBe 1f
        }

        // P-CHP-3: a chapter with no length has no line to fill
        it("has zero progress when the current chapter has no length") {
            val ui = chaptersUiState(
                playbackState(chapters = chapters(0.0), positionSeconds = 5.0, durationSeconds = 100.0),
                Locale.US,
            )

            ui.currentProgress shouldBe 0f
        }

        // P-CHP edge case: no chapters means no rows and no current chapter
        it("is empty for a book with no chapters") {
            chaptersUiState(noChapterState(positionSeconds = 120.0), Locale.US) shouldBe ChaptersUiState()
        }
    }
})
