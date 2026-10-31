package com.omnibus.omnibus.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class MotionTest : DescribeSpec({

    describe("Motion") {

        it("TC-36: snap has response 0.22, damping 0.80 and stiffness about 816") {
            val spec = Motion.snap<Float>()

            spec.dampingRatio shouldBe (0.80f plusOrMinus 0.001f)
            spec.stiffness shouldBe (816f plusOrMinus 1f)
        }

        it("TC-37: glide has response 0.50, damping 0.88 and stiffness about 158") {
            val spec = Motion.glide<Float>()

            spec.dampingRatio shouldBe (0.88f plusOrMinus 0.001f)
            spec.stiffness shouldBe (158f plusOrMinus 1f)
        }

        it("TC-38: page is a 400 ms cubic bezier tween, not a spring") {
            val spec = Motion.page<Float>()

            spec.durationMillis shouldBe 400
            spec.easing shouldBe CubicBezierEasing(0.2f, 0.6f, 0.3f, 1f)
        }

        it("TC-39: lift and settle keep their parameters") {
            val lift = Motion.lift<Float>()
            val settle = Motion.settle<Float>()

            lift.dampingRatio shouldBe (0.74f plusOrMinus 0.001f)
            lift.stiffness shouldBe (504f plusOrMinus 1f)
            settle.dampingRatio shouldBe (0.82f plusOrMinus 0.001f)
            settle.stiffness shouldBe (204f plusOrMinus 1f)
        }

        it("RC-23: reduced motion is an animator scale of 0") {
            isReducedMotion(0f) shouldBe true
            isReducedMotion(0.5f) shouldBe false
            isReducedMotion(1f) shouldBe false
        }
    }
})
