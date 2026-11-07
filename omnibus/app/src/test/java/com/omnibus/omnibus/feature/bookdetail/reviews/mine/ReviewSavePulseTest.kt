package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReviewSavePulseTest : DescribeSpec({

    describe("the save pulse") {
        it("TC-49: counts each book's saves on its own") {
            val pulse = ReviewSavePulse()
            val saved = randomString()
            val other = "$saved-other"

            pulse.bump(saved)

            pulse.pulses(saved).value shouldBe 1
            pulse.pulses(other).value shouldBe 0
        }
    }
})
