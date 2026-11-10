package com.omnibus.omnibus.ui.theme

import androidx.compose.ui.graphics.compositeOver
import com.omnibus.omnibus.ui.components.NavigationIndicatorAlpha
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

    describe("the unselected navigation item") {

        context("in the dark theme") {
            it("TC-4: reads at 4.5:1 or more on bg2") {
                val palette = OmnibusPalette.Atrium

                (contrastRatio(palette.ink2, palette.bg2) >= 4.5) shouldBe true
            }
        }

        context("in the light theme") {
            it("TC-4: reads at 4.5:1 or more on bg2") {
                val palette = OmnibusPalette.Light

                (contrastRatio(palette.ink2, palette.bg2) >= 4.5) shouldBe true
            }
        }
    }

    describe("the selected navigation item") {

        context("in the dark theme") {
            it("TC-4: reads at 4.5:1 or more on its pill") {
                val palette = OmnibusPalette.Atrium
                val pill = palette.accent.copy(alpha = NavigationIndicatorAlpha).compositeOver(palette.bg2)

                (contrastRatio(palette.ink0, pill) >= 4.5) shouldBe true
            }
        }

        context("in the light theme") {
            it("TC-4: reads at 4.5:1 or more on its pill") {
                val palette = OmnibusPalette.Light
                val pill = palette.accent.copy(alpha = NavigationIndicatorAlpha).compositeOver(palette.bg2)

                (contrastRatio(palette.ink0, pill) >= 4.5) shouldBe true
            }
        }
    }
})
