package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.feature.bookdetail.stage.washShowsImage
import com.omnibus.omnibus.feature.bookdetail.stage.washTransform
import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class BookStageWashTest : DescribeSpec({

    describe("the wash's drift") {

        context("when the reader has asked for reduced motion") {
            it("#93 TC-31: holds still at every phase") {
                checkAll(Exhaustive.of(0.5f, 1f)) { phase ->
                    washTransform(phase, reducedMotion = true) shouldBe washTransform(0f, reducedMotion = true)
                }
            }
        }
    }

    describe("the wash's image") {

        context("when the book has a cover") {
            it("#93 TC-32: shows from API 31") {
                washShowsImage(hasCover = true, sdkInt = 31) shouldBe true
                washShowsImage(hasCover = true, sdkInt = 30) shouldBe false
            }
        }

        context("when the book has no cover") {
            it("#93 TC-32: stays hidden on any API") {
                washShowsImage(hasCover = false, sdkInt = randomInt(24..40)) shouldBe false
            }
        }
    }
})
