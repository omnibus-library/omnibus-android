package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

class ReviewSavePulseTest : DescribeSpec({

    describe("the save pulse") {

        it("TC-49: each book counts its own saves from zero, one flow per book, and no bump is lost") {
            val pulse = ReviewSavePulse()
            pulse.pulses("a").value shouldBe 0
            pulse.pulses("a") shouldBeSameInstanceAs pulse.pulses("a")

            pulse.bump("a")
            pulse.pulses("a").value shouldBe 1
            pulse.pulses("b").value shouldBe 0

            val threads = List(8) { Thread { repeat(500) { pulse.bump("c") } } }
            threads.forEach { it.start() }
            threads.forEach { it.join() }
            pulse.pulses("c").value shouldBe 4000
            pulse.pulses("a").value shouldBe 1
        }
    }
})
