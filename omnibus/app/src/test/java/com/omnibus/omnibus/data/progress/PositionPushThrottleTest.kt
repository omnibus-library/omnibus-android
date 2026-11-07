package com.omnibus.omnibus.data.progress

import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PositionPushThrottleTest : DescribeSpec({

    fun sut() = PositionPushThrottle()

    describe("deciding whether to push") {

        context("when the decision is forced") {
            it("TC-1: pushes, even inside the window") {
                val throttle = sut()
                val start = randomLong()
                throttle.shouldPush(force = true, nowMillis = start)

                throttle.shouldPush(force = true, nowMillis = start + 1) shouldBe true
            }

            it("TC-1: re-arms the window from that push") {
                val throttle = sut()
                val start = randomLong()
                throttle.shouldPush(force = true, nowMillis = start)
                throttle.shouldPush(force = true, nowMillis = start + 1_000)

                throttle.shouldPush(force = false, nowMillis = start + 1_000 + 4_000) shouldBe false
            }
        }

        context("when the first decision is unforced") {
            it("TC-2: is suppressed") {
                sut().shouldPush(force = false, nowMillis = randomLong()) shouldBe false
            }

            it("TC-2: leaves the window unarmed, so the next unforced decision pushes") {
                val throttle = sut()
                val start = randomLong()
                throttle.shouldPush(force = false, nowMillis = start)

                throttle.shouldPush(force = false, nowMillis = start + 1) shouldBe true
            }
        }

        context("when a push has armed the window") {
            it("TC-3: holds back unforced decisions up to 4000 ms") {
                val throttle = sut()
                val start = randomLong()
                throttle.shouldPush(force = true, nowMillis = start)

                throttle.shouldPush(force = false, nowMillis = start + 4_000) shouldBe false
            }

            it("TC-3: pushes past 4000 ms") {
                val throttle = sut()
                val start = randomLong()
                throttle.shouldPush(force = true, nowMillis = start)

                throttle.shouldPush(force = false, nowMillis = start + 4_001) shouldBe true
            }

            it("TC-3: arms a new window from an unforced push") {
                val throttle = sut()
                val start = randomLong()
                throttle.shouldPush(force = true, nowMillis = start)
                throttle.shouldPush(force = false, nowMillis = start + 4_001)

                throttle.shouldPush(force = false, nowMillis = start + 4_002) shouldBe false
            }
        }
    }
})
