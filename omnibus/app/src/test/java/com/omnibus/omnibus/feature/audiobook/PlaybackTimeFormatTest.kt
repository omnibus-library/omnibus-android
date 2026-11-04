package com.omnibus.omnibus.feature.audiobook

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale

class PlaybackTimeFormatTest : DescribeSpec({

    describe("clock") {

        // AND-UI-6
        it("#165 TC-32: formats m:ss and h:mm:ss, floored") {
            val us = Locale.US
            PlaybackTimeFormat.clock(0.0, locale = us) shouldBe "0:00"
            PlaybackTimeFormat.clock(59.9, locale = us) shouldBe "0:59"
            PlaybackTimeFormat.clock(60.0, locale = us) shouldBe "1:00"
            PlaybackTimeFormat.clock(3599.9, locale = us) shouldBe "59:59"
            PlaybackTimeFormat.clock(3600.0, locale = us) shouldBe "1:00:00"
            PlaybackTimeFormat.clock(3661.0, locale = us) shouldBe "1:01:01"
            PlaybackTimeFormat.clock(300.0, alwaysHours = true, locale = us) shouldBe "0:05:00"
            PlaybackTimeFormat.clock(-5.0, locale = us) shouldBe "0:00"
            PlaybackTimeFormat.clock(Double.NaN, locale = us) shouldBe "0:00"
            PlaybackTimeFormat.clock(Double.POSITIVE_INFINITY, locale = us) shouldBe "0:00"
        }

        // AND-UI-6
        it("#165 TC-33: uses the locale's digits") {
            val locale = Locale.forLanguageTag("ar-EG")
            PlaybackTimeFormat.clock(65.0, locale = locale) shouldBe String.format(locale, "%d:%02d", 1, 5)
        }
    }

    describe("hoursMinutes") {

        // AND-UI-6
        it("#165 TC-34: prints the two largest non-zero units") {
            val us = Locale.US
            PlaybackTimeFormat.hoursMinutes(73500.0, locale = us) shouldBe "20h 25m"
            PlaybackTimeFormat.hoursMinutes(72000.0, locale = us) shouldBe "20h"
            PlaybackTimeFormat.hoursMinutes(1500.0, locale = us) shouldBe "25m"
            PlaybackTimeFormat.hoursMinutes(45.0, locale = us) shouldBe "45s"
            PlaybackTimeFormat.hoursMinutes(0.4, locale = us) shouldBe "0m"
            PlaybackTimeFormat.hoursMinutes(0.0, locale = us) shouldBe "0m"
            PlaybackTimeFormat.hoursMinutes(-12.0, locale = us) shouldBe "0m"
            PlaybackTimeFormat.hoursMinutes(Double.NaN, locale = us) shouldBe "0m"
        }
    }

    describe("rate adjusted inputs") {

        // AND-UI-6, AC8
        it("#165 TC-35: 6:38 becomes 3:19 at 2x (P-SCR edge case)") {
            val us = Locale.US
            val left = 398.0
            PlaybackTimeFormat.clock(left / 1.0, locale = us) shouldBe "6:38"
            PlaybackTimeFormat.clock(left / 2.0, locale = us) shouldBe "3:19"
            PlaybackTimeFormat.clock(left / 1.5, locale = us) shouldBe "4:25"
        }
    }
})
