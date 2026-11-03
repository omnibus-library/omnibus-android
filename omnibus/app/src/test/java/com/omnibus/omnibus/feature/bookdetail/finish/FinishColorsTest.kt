package com.omnibus.omnibus.feature.bookdetail.finish

import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class FinishColorsTest : DescribeSpec({

    describe("the card's ground") {

        it("TC-19: the ground is a slant across the book's colour") {
            val tone = Oklch(0.62, 0.12, 60.0)

            val light = finishGround(tone, lightGround = true)
            light.size shouldBe 2
            light[0] shouldBe bookAccent(tone, lightGround = true).tintSoft

            val dark = finishGround(tone, lightGround = false)
            dark.size shouldBe 2
            dark[0].alpha shouldBe (0.30f plusOrMinus 0.01f)
            dark[1].alpha shouldBe (0.55f plusOrMinus 0.01f)
        }
    }
})
