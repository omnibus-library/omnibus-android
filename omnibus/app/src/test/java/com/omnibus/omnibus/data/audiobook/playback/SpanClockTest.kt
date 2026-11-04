package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

private fun chapter(ordinal: Long, start: Double, duration: Double) =
    Chapter(ordinal = ordinal, title = "Chapter $ordinal", startSeconds = start, durationSeconds = duration)

/** Three chapters: 0-600 s, 600-1800 s and 1800-2700 s, in a 2700 s book. */
private val threeChapters = listOf(chapter(1, 0.0, 600.0), chapter(2, 600.0, 1200.0), chapter(3, 1800.0, 900.0))
private const val BOOK_MS = 2_700_000L

class SpanClockTest : DescribeSpec({

    describe("SpanClock") {

        // TC-33
        it("maps book time to span time (I-SYS-1)") {
            val middle = spanAt(threeChapters, BOOK_MS, 700_000L)!!

            middle.index shouldBe 1
            middle.toSpanMs(700_000L) shouldBe 100_000L
            spanAt(threeChapters, BOOK_MS, 550_000L)!!.toSpanMs(550_000L) shouldBe 550_000L
        }

        // TC-33
        it("maps span time back to book time (I-SYS-1)") {
            val middle = spanAt(threeChapters, BOOK_MS, 700_000L)!!

            middle.toBookMs(100_000L) shouldBe 700_000L
            middle.toBookMs(0L) shouldBe 600_000L
        }

        // TC-33
        it("clamps to the span (I-SYS-1)") {
            val span = Span(index = 1, startMs = 600_000L, lengthMs = 1_200_000L)

            span.toSpanMs(100_000L) shouldBe 0L
            span.toSpanMs(9_999_999L) shouldBe 1_200_000L
            span.toBookMs(-5L) shouldBe 600_000L
            span.toBookMs(5_000_000L) shouldBe 1_800_000L
        }

        // TC-33
        it("no chapters spans the whole book (I-TL-4)") {
            val whole = spanAt(emptyList(), BOOK_MS, 123_000L)!!

            whole.index.shouldBeNull()
            whole.startMs shouldBe 0L
            whole.lengthMs shouldBe BOOK_MS
            whole.toSpanMs(123_000L) shouldBe 123_000L
            spanAt(emptyList(), 0L, 5L).shouldBeNull()
        }

        // TC-33
        it("zero-duration chapters (I-TL-3)") {
            // Resolved first, as the controller does: the gaps to the next starts become the lengths.
            val resolved = resolveChapters(
                listOf(
                    chapter(1, 0.0, 0.0),
                    chapter(2, 100.0, 0.0),
                    chapter(3, 100.0, 0.0),
                    chapter(4, 200.0, 100.0),
                ),
                300.0,
            )

            val atEqualStarts = spanAt(resolved, 300_000L, 150_000L)!!
            atEqualStarts.index shouldBe chapterIndexAt(resolved, 150.0)
            atEqualStarts.index shouldBe 2
            atEqualStarts.startMs shouldBe 100_000L
            atEqualStarts.lengthMs shouldBe 100_000L

            spanAt(resolved, 300_000L, 50_000L)!!.index shouldBe 0
        }
    }
})
