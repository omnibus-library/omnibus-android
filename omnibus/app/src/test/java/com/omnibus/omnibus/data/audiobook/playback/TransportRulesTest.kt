package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun chapter(ordinal: Long, start: Double, duration: Double) =
    Chapter(ordinal = ordinal, title = "Chapter $ordinal", startSeconds = start, durationSeconds = duration)

/** Three chapters: 0-600, 600-1800 and 1800-2700, in a 2700 s book. */
private val threeChapters = listOf(chapter(1, 0.0, 600.0), chapter(2, 600.0, 1200.0), chapter(3, 1800.0, 900.0))

class TransportRulesTest : DescribeSpec({

    describe("skips") {

        // TC-28
        it("back is 15 s and forward is 30 s (P-PLAY-3)") {
            skipTarget(100.0, 3000.0, -SKIP_BACK_SECONDS) shouldBe 85.0
            skipTarget(100.0, 3000.0, SKIP_FORWARD_SECONDS) shouldBe 130.0
        }

        // TC-28
        it("clamp to the book (I-TL-6)") {
            skipTarget(5.0, 3000.0, -SKIP_BACK_SECONDS) shouldBe 0.0
            skipTarget(2990.0, 3000.0, SKIP_FORWARD_SECONDS) shouldBe 3000.0
        }

        // TC-28
        it("an unknown duration clamps only the lower bound (I-TL-6)") {
            skipTarget(5.0, 0.0, -SKIP_BACK_SECONDS) shouldBe 0.0
            skipTarget(5.0, 0.0, SKIP_FORWARD_SECONDS) shouldBe 35.0
        }
    }

    describe("previous chapter") {

        // TC-29
        it("restarts when more than 3 s in (I-TL-7)") {
            previousChapterTarget(threeChapters, 610.0) shouldBe 600.0
            previousChapterTarget(threeChapters, 603.01) shouldBe 600.0
        }

        // TC-29
        it("goes back at exactly 3 s (I-TL-7)") {
            previousChapterTarget(threeChapters, 603.0) shouldBe 0.0
        }

        // TC-29
        it("chapter 1 restarts (P-PLAY-4)") {
            previousChapterTarget(threeChapters, 1.0) shouldBe 0.0
            previousChapterTarget(threeChapters, 50.0) shouldBe 0.0
        }

        // TC-29
        it("does nothing without chapters (P-PLAY-4)") {
            previousChapterTarget(emptyList(), 10.0).shouldBeNull()
        }
    }

    describe("next chapter") {

        // TC-30
        it("goes to the next start (P-PLAY-5)") {
            nextChapterTarget(threeChapters, 0.0) shouldBe 600.0
            nextChapterTarget(threeChapters, 700.0) shouldBe 1800.0
        }

        // TC-30
        it("does nothing on the last chapter (P-PLAY-5)") {
            nextChapterTarget(threeChapters, 2000.0).shouldBeNull()
        }

        // TC-30
        it("does nothing without chapters (P-PLAY-5)") {
            nextChapterTarget(emptyList(), 0.0).shouldBeNull()
        }
    }

    describe("seekTo") {

        // TC-31
        it("clamps to [0, duration] (I-TL-6)") {
            clampToBook(-5.0, 3000.0) shouldBe 0.0
            clampToBook(3500.0, 3000.0) shouldBe 3000.0
            clampToBook(1200.0, 3000.0) shouldBe 1200.0
            clampToBook(50.0, 0.0) shouldBe 50.0
        }
    }

    describe("rate") {

        // TC-32
        it("clamps to [0.5, 3.0] (I-SPD-1)") {
            clampRate(0.1) shouldBe 0.5
            clampRate(0.5) shouldBe 0.5
            clampRate(1.7) shouldBe 1.7
            clampRate(3.0) shouldBe 3.0
            clampRate(9.0) shouldBe 3.0
        }

        // TC-32
        it("ignores NaN and infinite rates (I-SPD-1)") {
            clampRate(Double.NaN).shouldBeNull()
            clampRate(Double.POSITIVE_INFINITY).shouldBeNull()
            clampRate(Double.NEGATIVE_INFINITY).shouldBeNull()
        }
    }
})
