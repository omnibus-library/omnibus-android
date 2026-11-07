package com.omnibus.omnibus.feature.audiobook.player

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.util.Locale

class PlayerToolRowTest : DescribeSpec({

    describe("the rate's label") {
        // P-SCR-11
        it("#165 TC-73: drops the decimals of a whole rate, keeping up to two of any other") {
            checkAll(Exhaustive.of(2.0 to "2x", 1.5 to "1.5x", 1.333 to "1.33x")) { (rate, expected) ->
                rateLabel(rate, Locale.US) shouldBe expected
            }
        }

        // P-SCR-11, AND-UI-6
        it("#165 TC-73: uses the locale's decimal separator") {
            rateLabel(1.25, Locale.GERMANY) shouldBe "1,25x"
        }
    }
})
