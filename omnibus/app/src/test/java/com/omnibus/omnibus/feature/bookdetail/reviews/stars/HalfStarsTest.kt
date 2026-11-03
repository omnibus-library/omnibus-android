package com.omnibus.omnibus.feature.bookdetail.reviews.stars

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class HalfStarsTest : DescribeSpec({

    describe("halfStepAt") {

        it("TC-1: each step's middle is its half star, and the row is split into ten steps of its width") {
            val width = 440f
            val middles = (1..10).map { k -> halfStepAt((k - 0.5f) / 10f * width, width) }
            middles shouldBe listOf(0.5f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f)

            halfStepAt(1f, width) shouldBe 0.5f
            halfStepAt(22f, width) shouldBe 0.5f
            // Each step is 44 wide here, so the first edge is at 44 (22.5 is still step 1).
            halfStepAt(44.5f, width) shouldBe 1f
            halfStepAt(439f, width) shouldBe 5f
        }

        it("TC-1: a sweep across the row is a multiple of 0.5 in range, and never decreases") {
            val width = 440f
            val values = (0..1000).map { i -> halfStepAt(i * width / 1000f, width) }

            values.forEach { value ->
                (value * 2f).toInt().toFloat() shouldBe value * 2f
                (value in 0.5f..5f) shouldBe true
            }
            values.zipWithNext().all { (a, b) -> b >= a } shouldBe true
        }

        it("TC-2: an exact edge belongs to the lower step, and just past it is the next one") {
            val width = 10f
            (1..10).forEach { k ->
                halfStepAt(k.toFloat(), width) shouldBe k / 2f
                halfStepAt(k + 0.001f, width) shouldBe minOf((k + 1) / 2f, 5f)
            }
        }

        it("TC-3: out of range and degenerate inputs clamp, and never throw") {
            halfStepAt(-1f, 10f) shouldBe 0.5f
            halfStepAt(-1e9f, 10f) shouldBe 0.5f
            halfStepAt(0f, 10f) shouldBe 0.5f
            halfStepAt(Float.NEGATIVE_INFINITY, 10f) shouldBe 0.5f

            halfStepAt(10f, 10f) shouldBe 5f
            halfStepAt(11f, 10f) shouldBe 5f
            halfStepAt(1e9f, 10f) shouldBe 5f
            halfStepAt(Float.POSITIVE_INFINITY, 10f) shouldBe 5f

            halfStepAt(5f, 0f) shouldBe 0.5f
            halfStepAt(5f, -5f) shouldBe 0.5f
            halfStepAt(5f, Float.NaN) shouldBe 0.5f
            halfStepAt(Float.NaN, 10f) shouldBe 0.5f
        }
    }

    describe("toggledRating") {

        it("TC-5: tapping the current rating clears it, and any other tap sets its own value") {
            toggledRating(4f, 4f) shouldBe null
            toggledRating(0.5f, 0.5f) shouldBe null
            toggledRating(5f, 5f) shouldBe null

            toggledRating(4f, 3.5f) shouldBe 3.5f
            toggledRating(null, 3f) shouldBe 3f
        }
    }
})
