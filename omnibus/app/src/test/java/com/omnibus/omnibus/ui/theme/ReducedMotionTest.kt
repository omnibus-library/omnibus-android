package com.omnibus.omnibus.ui.theme

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReducedMotionTest : DescribeSpec({

    describe("whether motion is reduced") {

        it("RC-23: is an animator scale of 0") {
            isReducedMotion(0f) shouldBe true
            isReducedMotion(0.5f) shouldBe false
        }
    }
})
