package com.omnibus.omnibus.data.cover

import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class RevalidationPlanTest : DescribeSpec({

    describe("planning a cover's check-back") {
        context("when an old entry has no usable validator") {
            it("TC-136: fetches unconditionally for a null and an empty validator") {
                checkAll(Exhaustive.of(null, "")) { etag ->
                    val age = CoverCacheKeys.REVALIDATE_AFTER_MILLIS + randomLong()

                    RevalidationPlan.of(age, etag) shouldBe RevalidationPlan.Unconditional
                }
            }
        }

        context("when the clock moved backwards past the entry's time") {
            it("TC-137: treats the entry as stale") {
                val etag = randomString()

                RevalidationPlan.of(-1, etag) shouldBe RevalidationPlan.Conditional(etag)
            }
        }
    }
})
