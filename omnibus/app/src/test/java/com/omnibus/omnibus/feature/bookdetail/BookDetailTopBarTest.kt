package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.animation.core.SnapSpec
import com.omnibus.omnibus.feature.bookdetail.ui.isTopBarFilled
import com.omnibus.omnibus.feature.bookdetail.ui.topBarFillSpec
import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BookDetailTopBarTest : DescribeSpec({

    describe("filling the top bar") {

        it("#93 TC-28: starts just past the threshold") {
            val threshold = randomInt(0..1_000)

            isTopBarFilled(threshold, threshold.toFloat()) shouldBe false
            isTopBarFilled(threshold + 1, threshold.toFloat()) shouldBe true
        }

        context("when the reader has asked for reduced motion") {
            it("#93 TC-101: snaps") {
                topBarFillSpec(reducedMotion = true).shouldBeInstanceOf<SnapSpec<Float>>()
            }
        }
    }
})
