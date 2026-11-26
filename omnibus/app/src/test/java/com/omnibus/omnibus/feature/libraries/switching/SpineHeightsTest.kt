package com.omnibus.omnibus.feature.libraries.switching

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll

class SpineHeightsTest : DescribeSpec({

    describe("the spine height of a library") {

        it("#369 TC-28: is always between 84 and 112 dp, for any id") {
            checkAll(Arb.string()) { id ->
                val height = SpineHeights.of(id).value
                height.shouldBeGreaterThanOrEqualTo(84f)
                height.shouldBeLessThanOrEqualTo(112f)
            }
        }

        it("#369 TC-28: is the same for the same id") {
            checkAll(Arb.string()) { id ->
                SpineHeights.of(id) shouldBe SpineHeights.of(id)
            }
        }
    }
})
