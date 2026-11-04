package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun chapter(ordinal: Long, start: Double, duration: Double) =
    Chapter(ordinal = ordinal, title = "Chapter $ordinal", startSeconds = start, durationSeconds = duration)

private fun stateAt(
    position: Double,
    chapters: List<Chapter>,
    duration: Double = 3000.0,
    rate: Double = 1.0,
) = PlaybackState(
    chapters = chapters,
    positionSeconds = position,
    durationSeconds = duration,
    rate = rate,
    currentChapterIndex = chapterIndexAt(chapters, position),
)

/** Three chapters: 0-600, 600-1800 and 1800-2700, in a 3000 s book. */
private val threeChapters = listOf(chapter(1, 0.0, 600.0), chapter(2, 600.0, 1200.0), chapter(3, 1800.0, 900.0))

class PlaybackTimelineTest : DescribeSpec({

    describe("resolveChapters") {

        // TC-23
        it("sorts by start (I-TL-2)") {
            val input = listOf(chapter(2, 600.0, 100.0), chapter(1, 0.0, 600.0))

            resolveChapters(input, 1000.0).map { it.ordinal } shouldBe listOf(1L, 2L)
        }

        // TC-23
        it("fills a zero length from the next start (I-TL-3)") {
            val input = listOf(chapter(1, 0.0, 0.0), chapter(2, 300.0, 0.0), chapter(3, 350.0, 50.0))

            resolveChapters(input, 1000.0).map { it.durationSeconds } shouldBe listOf(300.0, 50.0, 50.0)
        }

        // TC-23
        it("fills the last chapter to the book end (I-TL-3)") {
            val input = listOf(chapter(1, 0.0, 100.0), chapter(2, 400.0, 0.0))

            resolveChapters(input, 1000.0)[1].durationSeconds shouldBe 600.0
        }

        // TC-23
        it("floors at 0 (I-TL-3)") {
            resolveChapters(listOf(chapter(1, 500.0, 0.0)), 400.0)[0].durationSeconds shouldBe 0.0
        }

        // TC-23
        it("keeps given lengths (I-TL-3)") {
            val input = listOf(chapter(1, 0.0, 123.0), chapter(2, 123.0, 77.0))

            resolveChapters(input, 1000.0) shouldBe input
        }

        // TC-23
        it("gives an empty list for an empty list") {
            resolveChapters(emptyList(), 1000.0) shouldBe emptyList()
        }
    }

    describe("chapterIndexAt") {

        // TC-24
        it("is null with no chapters (I-TL-2)") {
            chapterIndexAt(emptyList(), 10.0).shouldBeNull()
        }

        // TC-24
        it("before the first chapter is chapter 1 (I-TL-2)") {
            val chapters = listOf(chapter(1, 10.0, 100.0), chapter(2, 110.0, 100.0))

            chapterIndexAt(chapters, 5.0) shouldBe 0
            chapterIndexAt(chapters, -20.0) shouldBe 0
        }

        // TC-24
        it("a start boundary belongs to the new chapter (I-TL-2)") {
            chapterIndexAt(threeChapters, 600.0) shouldBe 1
            chapterIndexAt(threeChapters, 599.9) shouldBe 0
        }

        // TC-24
        it("the last chapter (I-TL-2)") {
            chapterIndexAt(threeChapters, 2500.0) shouldBe 2
            chapterIndexAt(threeChapters, 1_000_000.0) shouldBe 2
        }

        // TC-24
        it("zero-duration chapters (I-TL-3)") {
            val chapters = listOf(
                chapter(1, 0.0, 0.0),
                chapter(2, 100.0, 0.0),
                chapter(3, 100.0, 0.0),
                chapter(4, 200.0, 100.0),
            )

            chapterIndexAt(chapters, 100.0) shouldBe 2
            chapterIndexAt(chapters, 150.0) shouldBe 2
            chapterIndexAt(chapters, 50.0) shouldBe 0
        }
    }

    describe("span helpers") {

        // TC-25
        it("uses the chapter span (I-TL-4)") {
            val state = stateAt(700.0, threeChapters)

            state.currentChapter shouldBe threeChapters[1]
            state.spanStartSeconds shouldBe 600.0
            state.spanLengthSeconds shouldBe 1200.0
            state.spanPositionSeconds shouldBe 100.0
        }

        // TC-25
        it("no chapters spans the whole book (I-TL-4)") {
            val state = PlaybackState(positionSeconds = 250.0, durationSeconds = 3000.0)

            state.currentChapter.shouldBeNull()
            state.spanStartSeconds shouldBe 0.0
            state.spanLengthSeconds shouldBe 3000.0
            state.spanPositionSeconds shouldBe 250.0
        }

        // TC-25
        it("clamps the span position (I-TL-4)") {
            // Chapter 1 resolves to 100 s, so 300 s in is 100 s past its end, and a negative position is 0.
            val chapters = listOf(chapter(1, 0.0, 100.0), chapter(2, 600.0, 600.0))

            stateAt(300.0, chapters).spanPositionSeconds shouldBe 100.0
            PlaybackState(positionSeconds = -5.0, durationSeconds = 3000.0).spanPositionSeconds shouldBe 0.0
        }
    }

    describe("time left") {

        // TC-26
        it("divides by the rate (I-TL-5)") {
            val state = stateAt(700.0, threeChapters)

            state.spanSecondsLeftAtRate shouldBe 1100.0 plusOrMinus 1e-9
            state.bookSecondsLeftAtRate shouldBe 2300.0 plusOrMinus 1e-9

            val at15 = stateAt(700.0, threeChapters, rate = 1.5)
            at15.spanSecondsLeftAtRate shouldBe 733.333 plusOrMinus 0.01
            at15.bookSecondsLeftAtRate shouldBe 1533.333 plusOrMinus 0.01

            val at20 = stateAt(700.0, threeChapters, rate = 2.0)
            at20.spanSecondsLeftAtRate shouldBe 550.0 plusOrMinus 1e-9
            at20.bookSecondsLeftAtRate shouldBe 1150.0 plusOrMinus 1e-9
        }

        // TC-26
        it("never goes below 0 (I-TL-5)") {
            val state = stateAt(3500.0, threeChapters)

            state.spanSecondsLeftAtRate shouldBe 0.0
            state.bookSecondsLeftAtRate shouldBe 0.0
        }
    }

    describe("flags") {

        // TC-27
        it("isLastChapter and hasChapters") {
            PlaybackState().isLastChapter shouldBe false
            PlaybackState().hasChapters shouldBe false

            stateAt(0.0, threeChapters).isLastChapter shouldBe false
            stateAt(0.0, threeChapters).hasChapters shouldBe true
            stateAt(2000.0, threeChapters).isLastChapter shouldBe true

            stateAt(10.0, listOf(chapter(1, 0.0, 100.0))).isLastChapter shouldBe true
        }
    }
})
