package com.omnibus.omnibus.feature.audiobook.player

import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class PlayerBackdropTest : DescribeSpec({

    describe("the bloom's colors") {

        context("when the book's tone has no chroma") {
            it("#197: lifts the core to the chroma floor") {
                val hue = randomDouble(0.0, 360.0)

                val core = playerBloomColors(Oklch(randomDouble(), 0.0, hue)).first()

                core shouldBe Oklch(0.45, 0.06 * 1.05, hue).toColor()
            }
        }

        context("when the book's tone has chroma above the floor") {
            it("#197: scales the shoulder's chroma by 0.7 at half alpha") {
                val tone = Oklch(randomDouble(), randomDouble(0.1, 0.3), randomDouble(0.0, 360.0))

                val shoulder = playerBloomColors(tone)[1]

                shoulder shouldBe Oklch(0.24, tone.c * 0.7, tone.h, alpha = 0.5).toColor()
            }
        }

        it("#197: fades out at the edge") {
            val tone = Oklch(randomDouble(), randomDouble(0.1, 0.3), randomDouble(0.0, 360.0))

            playerBloomColors(tone).last().alpha shouldBe 0f
        }
    }
})
