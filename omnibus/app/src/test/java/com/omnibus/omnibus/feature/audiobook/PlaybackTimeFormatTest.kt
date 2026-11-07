package com.omnibus.omnibus.feature.audiobook

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.util.Locale

class PlaybackTimeFormatTest : DescribeSpec({

    describe("formatting a clock time") {
        // AND-UI-6
        it("#165 TC-32: floors to m:ss or h:mm:ss, reading 0:00 for negative or non-finite seconds") {
            checkAll(
                Exhaustive.of(
                    59.9 to "0:59",
                    3599.9 to "59:59",
                    3600.0 to "1:00:00",
                    -5.0 to "0:00",
                    Double.NaN to "0:00",
                    Double.POSITIVE_INFINITY to "0:00",
                ),
            ) { (seconds, expected) ->
                PlaybackTimeFormat.clock(seconds, locale = Locale.US) shouldBe expected
            }
        }

        context("when hours are always shown") {
            // AND-UI-6
            it("#165 TC-32: pads a time under an hour") {
                PlaybackTimeFormat.clock(300.0, alwaysHours = true, locale = Locale.US) shouldBe "0:05:00"
            }
        }

        // AND-UI-6
        it("#165 TC-33: uses the locale's digits") {
            val locale = Locale.forLanguageTag("ar-EG")

            PlaybackTimeFormat.clock(65.0, locale = locale) shouldBe String.format(locale, "%d:%02d", 1, 5)
        }
    }

    describe("formatting a duration") {
        // AND-UI-6
        it("#165 TC-34: prints the two largest non-zero units, reading 0m for less than a second or invalid seconds") {
            checkAll(
                Exhaustive.of(
                    73500.0 to "20h 25m",
                    72000.0 to "20h",
                    1500.0 to "25m",
                    45.0 to "45s",
                    0.4 to "0m",
                    -12.0 to "0m",
                    Double.NaN to "0m",
                ),
            ) { (seconds, expected) ->
                PlaybackTimeFormat.hoursMinutes(seconds, locale = Locale.US) shouldBe expected
            }
        }
    }
})
