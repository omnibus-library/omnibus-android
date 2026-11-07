package com.omnibus.omnibus.data.audiobook.playback

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ArtworkRetryScheduleTest : DescribeSpec({

    describe("waiting after repeated failures") {
        // TC-43
        it("doubles from 15 seconds up to 240 seconds for any failure count (I-SYS-4)") {
            checkAll(
                Exhaustive.of(1 to 15_000L, 2 to 30_000L, 3 to 60_000L, 4 to 120_000L, 5 to 240_000L, 6 to 240_000L, 40 to 240_000L),
            ) { (failures, waitMillis) ->
                val schedule = ArtworkRetrySchedule()
                repeat(failures) { schedule.noteFailure(0L) }

                schedule.fetchIsDue(waitMillis - 1) shouldBe false
                schedule.fetchIsDue(waitMillis) shouldBe true
            }
        }
    }
})
