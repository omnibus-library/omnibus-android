package com.omnibus.omnibus.feature.bookdetail.wishlist

import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.slots.stageAboveTitle
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class WishlistActivationTest : DescribeSpec({

    describe("wishlist slots") {

        it("#119 TC-39: the stage above the title registers the wishlist stage section") {
            stageAboveTitle shouldBe listOf(WishlistStageSection)
            BookDetailSlots.Registered.stageAboveTitle shouldContain WishlistStageSection
        }
    }
})
