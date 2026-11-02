package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.feature.bookdetail.stage.stageGroundTone
import com.omnibus.omnibus.feature.bookdetail.stage.washScrimStops
import com.omnibus.omnibus.feature.bookdetail.stage.washShowsImage
import com.omnibus.omnibus.feature.bookdetail.stage.washTransform
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

class BookStageWashTest : DescribeSpec({

    describe("washTransform") {

        it("#93 TC-30: interpolates the drift") {
            val start = washTransform(0f, reducedMotion = false)
            start.scale shouldBe (1.02f plusOrMinus 1e-6f)
            start.dxFraction shouldBe (-0.02f plusOrMinus 1e-6f)
            start.dyFraction shouldBe (-0.015f plusOrMinus 1e-6f)

            val end = washTransform(1f, reducedMotion = false)
            end.scale shouldBe (1.07f plusOrMinus 1e-6f)
            end.dxFraction shouldBe (0.02f plusOrMinus 1e-6f)
            end.dyFraction shouldBe (0.015f plusOrMinus 1e-6f)

            val middle = washTransform(0.5f, reducedMotion = false)
            middle.scale shouldBe (1.045f plusOrMinus 1e-6f)
            middle.dxFraction shouldBe (0f plusOrMinus 1e-6f)
            middle.dyFraction shouldBe (0f plusOrMinus 1e-6f)
        }

        it("#93 TC-31: reduced motion rests at phase 0") {
            val rest = washTransform(0f, reducedMotion = true)
            for (phase in listOf(0f, 0.5f, 1f)) {
                washTransform(phase, reducedMotion = true) shouldBe rest
            }
            rest.scale shouldBe (1.02f plusOrMinus 1e-6f)
        }
    }

    describe("washShowsImage") {

        it("#93 TC-32: image only with a cover on API 31+") {
            washShowsImage(hasCover = true, sdkInt = 31) shouldBe true
            washShowsImage(hasCover = true, sdkInt = 36) shouldBe true
            washShowsImage(hasCover = true, sdkInt = 30) shouldBe false
            washShowsImage(hasCover = true, sdkInt = 24) shouldBe false
            washShowsImage(hasCover = false, sdkInt = 36) shouldBe false
        }
    }

    describe("washScrimStops") {

        it("#93 TC-33: scrim stops per ground") {
            washScrimStops(lightGround = false) shouldBe listOf(0f to .55f, .30f to .16f, .76f to .74f, 1f to 1f)
            washScrimStops(lightGround = true) shouldBe listOf(0f to .78f, .30f to .62f, .76f to .86f, 1f to 1f)
        }
    }

    describe("stageGroundTone") {

        it("#93 TC-34: no-cover ground is L 0.3 in the book tone") {
            stageGroundTone(Oklch(0.55, 0.12, 40.0)) shouldBe Oklch(0.3, 0.12, 40.0)

            val tone = CoverIdentity(uuid = "uuid-dune", title = "Dune", hasCover = false).tone
            val ground = stageGroundTone(tone)
            ground.l shouldBe 0.3
            ground.c shouldBe (tone.c plusOrMinus 1e-9)
            ground.h shouldBe (tone.h plusOrMinus 1e-9)
        }
    }
})
