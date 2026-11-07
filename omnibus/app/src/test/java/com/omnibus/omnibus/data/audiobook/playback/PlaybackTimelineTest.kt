package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private fun chapter(start: Double, duration: Double) = randomChapter().copy(startSeconds = start, durationSeconds = duration)

private fun stateAt(position: Double, chapters: List<Chapter>, duration: Double = 3000.0, rate: Double = 1.0) =
    randomPlaybackState().copy(chapters = chapters, positionSeconds = position, durationSeconds = duration, rate = rate)

class PlaybackTimelineTest : DescribeSpec({

    describe("resolving chapter lengths") {
        // TC-23
        it("sorts the chapters by start (I-TL-2)") {
            val first = chapter(0.0, 600.0)
            val second = chapter(600.0, 100.0)

            resolveChapters(listOf(second, first), 1000.0) shouldBe listOf(first, second)
        }

        context("when a chapter has no length") {
            // TC-23
            it("fills it from the next start (I-TL-3)") {
                val input = listOf(chapter(0.0, 0.0), chapter(300.0, 0.0), chapter(350.0, 50.0))

                resolveChapters(input, 1000.0).map { it.durationSeconds } shouldBe listOf(300.0, 50.0, 50.0)
            }
        }

        context("when the last chapter has no length") {
            // TC-23
            it("fills it to the end of the book (I-TL-3)") {
                val input = listOf(chapter(0.0, 100.0), chapter(400.0, 0.0))

                resolveChapters(input, 1000.0)[1].durationSeconds shouldBe 600.0
            }
        }

        context("when a chapter starts after the end of the book") {
            // TC-23
            it("gives it no length (I-TL-3)") {
                resolveChapters(listOf(chapter(500.0, 0.0)), 400.0)[0].durationSeconds shouldBe 0.0
            }
        }
    }

    describe("finding the chapter at a position") {
        context("when the book has no chapters") {
            // TC-24
            it("finds none (I-TL-2)") {
                chapterIndexAt(emptyList(), 10.0).shouldBeNull()
            }
        }

        context("when the position is before the first chapter") {
            // TC-24
            it("finds the first chapter (I-TL-2)") {
                chapterIndexAt(listOf(chapter(10.0, 100.0), chapter(110.0, 100.0)), 5.0) shouldBe 0
            }
        }

        context("when the position is on a chapter's start") {
            // TC-24
            it("finds the chapter that starts there (I-TL-2)") {
                chapterIndexAt(chapters(600.0, 1200.0, 900.0), 600.0) shouldBe 1
            }
        }

        context("when the position is past the last chapter") {
            // TC-24
            it("finds the last chapter (I-TL-2)") {
                chapterIndexAt(chapters(600.0, 1200.0, 900.0), 1_000_000.0) shouldBe 2
            }
        }

        context("when two chapters share a start") {
            // TC-24
            it("finds the later one (I-TL-3)") {
                val input = listOf(chapter(0.0, 0.0), chapter(100.0, 0.0), chapter(100.0, 0.0), chapter(200.0, 100.0))

                chapterIndexAt(input, 150.0) shouldBe 2
            }
        }
    }

    describe("the current span") {
        // TC-25
        it("is the chapter at the position (I-TL-4)") {
            val chapters = chapters(600.0, 1200.0, 900.0)
            val state = stateAt(700.0, chapters)

            state.currentChapter shouldBe chapters[1]
            state.spanStartSeconds shouldBe 600.0
            state.spanLengthSeconds shouldBe 1200.0
            state.spanPositionSeconds shouldBe 100.0
        }

        context("when the book has no chapters") {
            // TC-25
            it("is the whole book (I-TL-4)") {
                val state = stateAt(250.0, emptyList(), duration = 3000.0)

                state.currentChapter.shouldBeNull()
                state.spanStartSeconds shouldBe 0.0
                state.spanLengthSeconds shouldBe 3000.0
                state.spanPositionSeconds shouldBe 250.0
            }
        }

        context("when the position is past the end of a chapter") {
            // TC-25
            it("holds the span position at the chapter's length (I-TL-4)") {
                val state = stateAt(300.0, listOf(chapter(0.0, 100.0), chapter(600.0, 600.0)))

                state.spanPositionSeconds shouldBe 100.0
            }
        }
    }

    describe("the time left") {
        // TC-26
        it("in the span shortens with any rate (I-TL-5)") {
            checkAll(Exhaustive.of(1.0, 1.5, 2.0)) { rate ->
                val state = stateAt(700.0, chapters(600.0, 1200.0, 900.0), rate = rate)

                state.spanSecondsLeftAtRate shouldBe (1100.0 / rate plusOrMinus 1e-9)
            }
        }

        // TC-26
        it("in the book shortens with any rate (I-TL-5)") {
            checkAll(Exhaustive.of(1.0, 1.5, 2.0)) { rate ->
                val state = stateAt(700.0, chapters(600.0, 1200.0, 900.0), duration = 3000.0, rate = rate)

                state.bookSecondsLeftAtRate shouldBe (2300.0 / rate plusOrMinus 1e-9)
            }
        }

        context("when the position is past the end of the book") {
            // TC-26
            it("is zero (I-TL-5)") {
                val state = stateAt(3500.0, chapters(600.0, 1200.0, 900.0), duration = 3000.0)

                state.spanSecondsLeftAtRate shouldBe 0.0
                state.bookSecondsLeftAtRate shouldBe 0.0
            }
        }
    }

    describe("being in the last chapter") {
        context("when the book has no chapters") {
            // TC-27
            it("is false") {
                stateAt(10.0, emptyList()).isLastChapter shouldBe false
            }
        }

        context("when the position is in an earlier chapter") {
            // TC-27
            it("is false") {
                stateAt(0.0, chapters(600.0, 1200.0, 900.0)).isLastChapter shouldBe false
            }
        }

        context("when the position is in the final chapter") {
            // TC-27
            it("is true") {
                stateAt(2000.0, chapters(600.0, 1200.0, 900.0)).isLastChapter shouldBe true
            }
        }
    }
})
