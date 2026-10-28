package com.omnibus.omnibus.ui.theme

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class OmnibusPaletteTest : DescribeSpec({

    describe("Atrium") {

        it("TC-31: atrium new tokens match the #13 OKLCH values") {
            OmnibusPalette.Atrium.bg2 shouldBe Oklch(0.215, 0.010, 70.0).toColor()
            OmnibusPalette.Atrium.line2 shouldBe Oklch(0.30, 0.010, 70.0, alpha = 0.30).toColor()
            OmnibusPalette.Atrium.ink1 shouldBe Oklch(0.82, 0.010, 75.0).toColor()
            OmnibusPalette.Atrium.warn shouldBe Oklch(0.78, 0.13, 75.0).toColor()
        }
    }

    describe("Light") {

        it("TC-32: light new tokens match the #13 OKLCH values") {
            OmnibusPalette.Light.bg2 shouldBe Oklch(0.940, 0.006, 75.0).toColor()
            OmnibusPalette.Light.line2 shouldBe Oklch(0.40, 0.010, 70.0, alpha = 0.10).toColor()
            OmnibusPalette.Light.ink1 shouldBe Oklch(0.36, 0.008, 70.0).toColor()
            OmnibusPalette.Light.warn shouldBe Oklch(0.78, 0.13, 75.0).toColor()
        }
    }

    describe("existing tokens") {

        it("TC-33: pre-existing tokens are unchanged in both palettes") {
            with(OmnibusPalette.Atrium) {
                bg0 shouldBe Color(0xFF0A0806)
                bg1 shouldBe Color(0xFF13100D)
                line shouldBe Color(0xA6312D28)
                ink0 shouldBe Color(0xFFF7F5F1)
                ink2 shouldBe Color(0xFF8B857F)
                ink3 shouldBe Color(0xFF625D56)
                accent shouldBe Color(0xFFF0A556)
                accentInk shouldBe Color(0xFF130B05)
                bad shouldBe Color(0xFFF97770)
            }
            with(OmnibusPalette.Light) {
                bg0 shouldBe Color(0xFFFBFAF8)
                bg1 shouldBe Color(0xFFF5F3F0)
                line shouldBe Color(0x334B4742)
                ink0 shouldBe Color(0xFF110F0D)
                ink2 shouldBe Color(0xFF6D6863)
                ink3 shouldBe Color(0xFF67625D)
                accent shouldBe Color(0xFFF0A556)
                accentInk shouldBe Color(0xFF130B05)
                bad shouldBe Color(0xFFF97770)
            }
        }
    }
})
