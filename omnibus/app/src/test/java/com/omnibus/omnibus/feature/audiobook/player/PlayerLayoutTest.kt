package com.omnibus.omnibus.feature.audiobook.player

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeExactly
import io.kotest.matchers.shouldBe

class PlayerLayoutTest : DescribeSpec({

    describe("coverShare") {

        // AND-UI-5, P-SCR-5
        it("#165 TC-39: switches to 66% under 380 dp") {
            coverShare(379.9f) shouldBeExactly 0.66f
            coverShare(380f) shouldBeExactly 0.76f
            coverShare(700f) shouldBeExactly 0.76f
        }
    }

    describe("coverHeightDp") {

        // AND-UI-5, P-SCR-5
        it("#165 TC-40: is capped by width and 450 dp and never negative") {
            // Tall band, narrow width: the 2:3 width caps it.
            coverHeightDp(bandHeightDp = 600f, widthDp = 200f) shouldBeExactly 300f
            // Short band: the band share caps it.
            coverHeightDp(bandHeightDp = 300f, widthDp = 900f) shouldBe (198f plusOrMinus 0.01f)
            // Huge band and wide width: the 450 dp ceiling caps it.
            coverHeightDp(bandHeightDp = 2000f, widthDp = 1000f) shouldBeExactly 450f
            coverHeightDp(bandHeightDp = 0f, widthDp = 300f) shouldBeExactly 0f
            coverHeightDp(bandHeightDp = -10f, widthDp = 300f) shouldBeExactly 0f
            coverHeightDp(bandHeightDp = 500f, widthDp = -5f) shouldBeExactly 0f
        }
    }
})
