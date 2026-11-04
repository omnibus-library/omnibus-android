package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ArtworkRetryScheduleTest : DescribeSpec({

    describe("schedule") {

        // TC-43
        it("waits 15 s doubling up to 240 s (I-SYS-4)") {
            (1..8).map { ArtworkRetrySchedule.delayMillis(it) } shouldBe listOf(
                15_000L, 30_000L, 60_000L, 120_000L, 240_000L, 240_000L, 240_000L, 240_000L,
            )
        }

        // TC-43
        it("fetchIsDue honours the wait (I-SYS-4)") {
            val schedule = ArtworkRetrySchedule()
            schedule.noteCurrent("book-1")
            schedule.fetchIsDue(0L) shouldBe true

            schedule.noteFailure(0L)

            schedule.fetchIsDue(14_999L) shouldBe false
            schedule.fetchIsDue(15_000L) shouldBe true
        }
    }

    describe("lifting the wait") {

        // TC-44
        it("any play clears the wait (I-SYS-4)") {
            val schedule = ArtworkRetrySchedule()
            schedule.noteCurrent("book-1")
            schedule.noteFailure(0L)
            schedule.noteFailure(0L)
            schedule.fetchIsDue(1L) shouldBe false

            schedule.noteResumed()

            schedule.fetchIsDue(1L) shouldBe true
        }

        // TC-44
        it("reconnecting lifts only a first failure (I-SYS-4)") {
            val first = ArtworkRetrySchedule()
            first.noteCurrent("book-1")
            first.noteFailure(0L)
            first.noteConnectionReturned()
            first.fetchIsDue(1L) shouldBe true

            val second = ArtworkRetrySchedule()
            second.noteCurrent("book-1")
            second.noteFailure(0L)
            second.noteFailure(0L)
            second.noteConnectionReturned()
            second.fetchIsDue(29_999L) shouldBe false
            second.fetchIsDue(30_000L) shouldBe true
        }

        // TC-44
        it("a new book starts clean (I-SYS-4)") {
            val schedule = ArtworkRetrySchedule()
            schedule.noteCurrent("book-1")
            schedule.noteFailure(0L)
            schedule.noteFailure(0L)

            schedule.noteCurrent("book-2")

            schedule.fetchIsDue(0L) shouldBe true
            schedule.noteFailure(0L)
            schedule.fetchIsDue(14_999L) shouldBe false
            schedule.fetchIsDue(15_000L) shouldBe true
        }
    }
})
