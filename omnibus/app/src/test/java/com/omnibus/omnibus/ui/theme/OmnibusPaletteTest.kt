package com.omnibus.omnibus.ui.theme

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class OmnibusPaletteTest : DescribeSpec({

    describe("the series fill") {

        context("in the dark theme") {
            it("#258 TC-6: is the accent at 13%") {
                val palette = OmnibusPalette.Atrium

                palette.seriesFill.copy(alpha = 1f) shouldBe palette.accent
                (abs(palette.seriesFill.alpha - 0.13f) <= 1f / 255) shouldBe true
            }
        }

        context("in the light theme") {
            it("#258 TC-6: is the accent at 16%") {
                val palette = OmnibusPalette.Light

                palette.seriesFill.copy(alpha = 1f) shouldBe palette.accent
                (abs(palette.seriesFill.alpha - 0.16f) <= 1f / 255) shouldBe true
            }
        }
    }
})
