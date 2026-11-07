package com.omnibus.omnibus.ui.theme

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private fun Color.channels() = listOf(
    Math.round(red * 255),
    Math.round(green * 255),
    Math.round(blue * 255),
)

class OklchTest : DescribeSpec({

    describe("converting to a color") {

        it("TC-101: matches the Atrium accent") {
            val (r, g, b) = Oklch(0.78, 0.13, 65.0).toColor().channels()

            // Within a step of 0xFFF0A556; the accent was rounded from the OKLCH original.
            r.toDouble() shouldBe (0xF0.toDouble() plusOrMinus 1.0)
            g.toDouble() shouldBe (0xA5.toDouble() plusOrMinus 1.0)
            b.toDouble() shouldBe (0x56.toDouble() plusOrMinus 1.0)
        }

        it("TC-102: carries alpha through") {
            Oklch(0.5, 0.05, 120.0, alpha = 0.3).toColor().alpha.toDouble() shouldBe (0.3 plusOrMinus 0.005)
        }

        context("when the color is out of gamut") {
            it("TC-103: clamps it into sRGB") {
                val color = Oklch(0.7, 0.4, 150.0).toColor()

                listOf(color.red, color.green, color.blue).all { it in 0f..1f } shouldBe true
            }
        }
    }

    describe("converting from sRGB") {

        it("TC-104: round-trips, across hues, white and black") {
            checkAll(Exhaustive.of(0xFFF0A556, 0xFF0A0806, 0xFF336699, 0xFFFFFFFF, 0xFF000000, 0xFFC0392B)) { argb ->
                val color = Color(argb)

                Oklch.fromSrgb(color).toColor().channels() shouldBe color.channels()
            }
        }

        it("TC-106: keeps hue in 0 until 360, across the primaries") {
            checkAll(Exhaustive.of(0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFFF00FF)) { argb ->
                (Oklch.fromSrgb(Color(argb)).h in 0.0..<360.0) shouldBe true
            }
        }
    }

    describe("parsing a color") {

        context("when the text is unitless oklch") {
            it("TC-108 / TC-111: reads it, with any case, commas or surrounding space") {
                checkAll(Exhaustive.of("oklch(0.4 0.1 30)", "  OKLCH(0.4, 0.1, 30)  ")) { text ->
                    Oklch.parse(text) shouldBe Oklch(0.4, 0.1, 30.0, 1.0)
                }
            }
        }

        context("when the text has a percentage lightness") {
            it("TC-109: reads it as a percentage, with its alpha") {
                Oklch.parse("oklch(55% 0.09 200 / 0.5)") shouldBe Oklch(0.55, 0.09, 200.0, 0.5)
            }

            it("TC-109: reads it as a percentage even at or below 1") {
                Oklch.parse("oklch(1% 0 0)")?.l shouldBe 0.01
            }
        }

        context("when the text is hex") {
            it("TC-112: reads it") {
                Oklch.parse("#112233") shouldBe Oklch.fromSrgb(0x11 / 255.0, 0x22 / 255.0, 0x33 / 255.0)
            }
        }

        context("when the text is garbage") {
            it("TC-113: gives null") {
                checkAll(
                    Exhaustive.of("", "garbage", "oklch(", "oklch()", "oklch(0.5 0.1)", "oklch(a b c)", "#12", "#gggggg", "red"),
                ) { text ->
                    Oklch.parse(text).shouldBeNull()
                }
            }
        }
    }
})
