package com.omnibus.omnibus.testing

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class FakeTimeSourceTest : DescribeSpec({

    describe("FakeTimeSource") {

        // TC-86
        it("can be set and advanced") {
            val clock = FakeTimeSource()
            clock.nowMillis() shouldBe 0L

            clock.advanceBy(250L)
            clock.nowMillis() shouldBe 250L

            clock.set(10_000L)
            clock.nowMillis() shouldBe 10_000L
        }
    }
})
