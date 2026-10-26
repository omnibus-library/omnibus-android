package com.omnibus.omnibus.data.cover

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class RevalidationPlanTest : DescribeSpec({

    val window = CoverCacheKeys.REVALIDATE_AFTER_MILLIS

    describe("of") {

        it("TC-134: skips an entry younger than five minutes") {
            RevalidationPlan.of(0, "v1") shouldBe RevalidationPlan.Skip
            RevalidationPlan.of(window - 1, "v1") shouldBe RevalidationPlan.Skip
            RevalidationPlan.of(window - 1, null) shouldBe RevalidationPlan.Skip
        }

        it("TC-135: asks conditionally once the entry is five minutes old and has a validator") {
            RevalidationPlan.of(window, "v1") shouldBe RevalidationPlan.Conditional("v1")
        }

        it("TC-136: fetches unconditionally when an old entry has no validator") {
            RevalidationPlan.of(window, null) shouldBe RevalidationPlan.Unconditional
            RevalidationPlan.of(window, "") shouldBe RevalidationPlan.Unconditional
        }

        it("TC-137: treats a negative age as stale") {
            RevalidationPlan.of(-1, "v1") shouldBe RevalidationPlan.Conditional("v1")
        }
    }
})
