package com.omnibus.omnibus.data.progress

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PositionPushThrottleTest : DescribeSpec({

    describe("shouldPush") {

        it("TC-1: forced decisions always push and re-arm the window") {
            val throttle = PositionPushThrottle()
            val t0 = 10_000L

            throttle.shouldPush(force = true, nowMillis = t0) shouldBe true
            throttle.shouldPush(force = true, nowMillis = t0 + 1_000) shouldBe true
            // The forced push at t0+1000 armed the window, so the window ends at t0+5000.
            throttle.shouldPush(force = false, nowMillis = t0 + 1_000 + 4_000) shouldBe false
            throttle.shouldPush(force = false, nowMillis = t0 + 1_000 + 4_001) shouldBe true
        }

        it("TC-2: the first unforced decision is suppressed without arming the window") {
            // Case A: an unforced first decision is suppressed, so the next unforced decision pushes.
            val unforcedFirst = PositionPushThrottle()
            unforcedFirst.shouldPush(force = false, nowMillis = 10_000) shouldBe false
            unforcedFirst.shouldPush(force = false, nowMillis = 10_001) shouldBe true

            // Case B: a forced first decision clears the suppression, and the window rules apply after it.
            val forcedFirst = PositionPushThrottle()
            forcedFirst.shouldPush(force = true, nowMillis = 10_000) shouldBe true
            forcedFirst.shouldPush(force = false, nowMillis = 10_001) shouldBe false
            forcedFirst.shouldPush(force = false, nowMillis = 14_001) shouldBe true
        }

        it("TC-3: at most one unforced push per window, strictly more than 4000 ms apart") {
            val throttle = PositionPushThrottle()
            val t0 = 20_000L

            // A forced first decision pushes and arms the window at t0.
            throttle.shouldPush(force = true, nowMillis = t0) shouldBe true
            throttle.shouldPush(force = false, nowMillis = t0 + 1) shouldBe false
            throttle.shouldPush(force = false, nowMillis = t0 + 2_000) shouldBe false
            throttle.shouldPush(force = false, nowMillis = t0 + 4_000) shouldBe false
            throttle.shouldPush(force = false, nowMillis = t0 + 4_001) shouldBe true
            throttle.shouldPush(force = false, nowMillis = t0 + 4_002) shouldBe false
        }
    }
})
