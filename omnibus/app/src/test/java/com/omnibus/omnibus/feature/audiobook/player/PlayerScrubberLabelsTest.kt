package com.omnibus.omnibus.feature.audiobook.player

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.Locale

class PlayerScrubberLabelsTest : DescribeSpec({

    var previousLocale: Locale = Locale.getDefault()

    beforeSpec {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }
    afterSpec { Locale.setDefault(previousLocale) }

    describe("scrubberTimes") {

        // I-TL-5, AC3, AC8
        it("#165 TC-36: left labels divide by the rate, elapsed does not") {
            val at1x = scrubberTimes(100.0, 600.0, 1000.0, 7200.0, 1.0, hasChapters = true)
            at1x.elapsed shouldBe "1:40"
            at1x.spanLeft shouldBe "8:20"
            at1x.bookLeft shouldBe "1h 41m"

            val at15x = scrubberTimes(100.0, 600.0, 1000.0, 7200.0, 1.5, hasChapters = true)
            at15x.elapsed shouldBe "1:40"
            at15x.spanLeft shouldBe "5:33"
            at15x.bookLeft shouldBe "1h 7m"

            val at2x = scrubberTimes(100.0, 600.0, 1000.0, 7200.0, 2.0, hasChapters = true)
            at2x.elapsed shouldBe "1:40"
            at2x.spanLeft shouldBe "4:10"
            at2x.bookLeft shouldBe "50m"
        }

        // AC5
        it("#165 TC-37: no centre label without chapters") {
            val times = scrubberTimes(100.0, 600.0, 0.0, 7200.0, 1.0, hasChapters = false)
            times.bookLeft.shouldBeNull()
            times.spanLeft shouldBe "8:20"
        }

        // AC8, I-TL-5
        it("#165 TC-38: under one second left reads 0:00 and a bad rate counts as 1x") {
            scrubberTimes(599.5, 600.0, 0.0, 600.0, 1.0, hasChapters = false).spanLeft shouldBe "0:00"
            scrubberTimes(600.0, 600.0, 0.0, 600.0, 1.0, hasChapters = false).spanLeft shouldBe "0:00"
            scrubberTimes(700.0, 600.0, 0.0, 600.0, 1.0, hasChapters = false).spanLeft shouldBe "0:00"
            scrubberTimes(100.0, 600.0, 0.0, 600.0, 0.0, hasChapters = false).spanLeft shouldBe "8:20"
            scrubberTimes(100.0, 600.0, 0.0, 600.0, Double.NaN, hasChapters = false).spanLeft shouldBe "8:20"
            scrubberTimes(-50.0, 600.0, 0.0, 600.0, 1.0, hasChapters = false).elapsed shouldBe "0:00"
        }
    }
})
