package com.omnibus.omnibus.feature.audiobook.player

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.text.NumberFormat
import java.util.Locale

class PlayerToolRowTest : DescribeSpec({

    describe("rateLabel") {

        // P-SCR-11
        it("#165 TC-73: whole rates drop decimals and others keep up to two") {
            val us = Locale.US
            rateLabel(1.0, us) shouldBe "1x"
            rateLabel(2.0, us) shouldBe "2x"
            rateLabel(0.5, us) shouldBe "0.5x"
            rateLabel(1.25, us) shouldBe "1.25x"
            rateLabel(1.5, us) shouldBe "1.5x"
            rateLabel(1.75, us) shouldBe "1.75x"
            rateLabel(1.333, us) shouldBe "1.33x"
            rateLabel(0.75, us) shouldBe "0.75x"
        }

        // P-SCR-11, AND-UI-6
        it("#165 TC-73: uses the locale's digits") {
            val locale = Locale.forLanguageTag("ar-EG")
            val format = NumberFormat.getNumberInstance(locale).apply {
                maximumFractionDigits = 2
                minimumFractionDigits = 0
                isGroupingUsed = false
            }
            rateLabel(1.25, locale) shouldBe format.format(1.25) + "x"
            rateLabel(2.0, locale) shouldBe String.format(locale, "%d", 2) + "x"
        }
    }
})
