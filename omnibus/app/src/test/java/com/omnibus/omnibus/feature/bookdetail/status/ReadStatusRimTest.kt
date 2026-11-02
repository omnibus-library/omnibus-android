package com.omnibus.omnibus.feature.bookdetail.status

import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class ReadStatusRimTest : DescribeSpec({

    val tone = Oklch(0.62, 0.10, 200.0)

    describe("statusRim") {

        it("TC-106: the rim follows the ground, at 22% alpha") {
            statusRim(tone, lightGround = true) shouldBe Oklch(0.42, tone.c, tone.h, alpha = 0.22).toColor()
            statusRim(tone, lightGround = false) shouldBe Oklch(0.80, tone.c, tone.h, alpha = 0.22).toColor()
            statusRim(tone, lightGround = false).alpha shouldBe 0.22f.plusOrMinus(0.01f)  // 8-bit alpha
            statusRim(tone, lightGround = true).alpha shouldBe 0.22f.plusOrMinus(0.01f)
        }
    }
})
