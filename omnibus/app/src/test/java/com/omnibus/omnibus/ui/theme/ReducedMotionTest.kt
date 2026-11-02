package com.omnibus.omnibus.ui.theme

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReducedMotionTest : DescribeSpec({

    describe("isReducedMotion") {

        it("RC-23: reduced motion is an animator scale of 0") {
            isReducedMotion(0f) shouldBe true
            isReducedMotion(0.5f) shouldBe false
            isReducedMotion(1f) shouldBe false
        }
    }
})
