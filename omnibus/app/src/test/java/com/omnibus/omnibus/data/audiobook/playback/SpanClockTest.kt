package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class SpanClockTest : DescribeSpec({

    describe("finding the span around a book position") {
        // TC-33
        it("is the chapter at that position (I-SYS-1)") {
            spanAt(chapters(600.0, 1200.0, 900.0), 2_700_000L, 700_000L) shouldBe
                Span(index = 1, startMs = 600_000L, lengthMs = 1_200_000L)
        }

        context("when the book has no chapters") {
            // TC-33
            it("is the whole book (I-TL-4)") {
                spanAt(emptyList(), 2_700_000L, 123_000L) shouldBe Span(index = null, startMs = 0L, lengthMs = 2_700_000L)
            }

            context("when the book's length is unknown") {
                // TC-33
                it("finds none (I-TL-4)") {
                    spanAt(emptyList(), 0L, 5L).shouldBeNull()
                }
            }
        }
    }

    describe("converting between book time and span time") {
        // TC-33
        it("offsets by the span's start across the span (I-SYS-1)") {
            val span = Span(index = 1, startMs = 600_000L, lengthMs = 1_200_000L)

            checkAll(Exhaustive.of(0L, 100_000L, 1_200_000L)) { spanMs ->
                span.toBookMs(spanMs) shouldBe 600_000L + spanMs
                span.toSpanMs(600_000L + spanMs) shouldBe spanMs
            }
        }

        context("when a book position is outside the span") {
            // TC-33
            it("holds the span time to the span (I-SYS-1)") {
                val span = Span(index = 1, startMs = 600_000L, lengthMs = 1_200_000L)

                span.toSpanMs(100_000L) shouldBe 0L
                span.toSpanMs(9_999_999L) shouldBe 1_200_000L
            }
        }

        context("when a span position is outside the span") {
            // TC-33
            it("holds the book time to the span (I-SYS-1)") {
                val span = Span(index = 1, startMs = 600_000L, lengthMs = 1_200_000L)

                span.toBookMs(-5L) shouldBe 600_000L
                span.toBookMs(5_000_000L) shouldBe 1_800_000L
            }
        }
    }
})
