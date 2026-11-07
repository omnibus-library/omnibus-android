package com.omnibus.omnibus.feature.audiobook.player

import com.omnibus.omnibus.feature.audiobook.PlaybackTimeFormat
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class PlayerScrubberLabelsTest : DescribeSpec({

    describe("the scrubber's labels") {
        // I-TL-5, AC3, AC8
        it("#165 TC-36: divide only the left labels by the rate") {
            val times = scrubberTimes(100.0, 600.0, 1000.0, 7200.0, 2.0, hasChapters = true)

            times.elapsed shouldBe PlaybackTimeFormat.clock(100.0)
            times.spanLeft shouldBe PlaybackTimeFormat.clock(250.0)
            times.bookLeft shouldBe PlaybackTimeFormat.hoursMinutes(3050.0)
        }

        // AC5
        it("#165 TC-37: have no centre label without chapters") {
            scrubberTimes(100.0, 600.0, 0.0, 7200.0, 1.0, hasChapters = false).bookLeft.shouldBeNull()
        }

        // I-TL-5
        it("#165 TC-38: count a zero, negative or NaN rate as 1x") {
            checkAll(Exhaustive.of(0.0, -1.0, Double.NaN)) { rate ->
                scrubberTimes(100.0, 600.0, 0.0, 600.0, rate, hasChapters = false).spanLeft shouldBe PlaybackTimeFormat.clock(500.0)
            }
        }
    }
})
