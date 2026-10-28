package com.omnibus.omnibus.ui.theme

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

private fun Color.channels() = intArrayOf(
    Math.round(red * 255),
    Math.round(green * 255),
    Math.round(blue * 255),
)

class OklchTest : DescribeSpec({

    describe("toColor") {

        it("TC-101: matches the Atrium accent") {
            val (r, g, b) = Oklch(0.78, 0.13, 65.0).toColor().channels().toList()

            // Within a step of 0xFFF0A556; the accent was rounded from the OKLCH original.
            r.toDouble() shouldBe (0xF0.toDouble() plusOrMinus 1.0)
            g.toDouble() shouldBe (0xA5.toDouble() plusOrMinus 1.0)
            b.toDouble() shouldBe (0x56.toDouble() plusOrMinus 1.0)
        }

        it("TC-102: carries alpha through") {
            Oklch(0.5, 0.05, 120.0, alpha = 0.3).toColor().alpha.toDouble() shouldBe (0.3 plusOrMinus 0.005)
        }

        it("TC-103: clamps out-of-gamut colors into sRGB") {
            val color = Oklch(0.7, 0.4, 150.0).toColor()

            listOf(color.red, color.green, color.blue).forEach {
                it shouldBe (it.coerceIn(0f, 1f))
            }
        }
    }

    describe("fromSrgb") {

        it("TC-104: round-trips through sRGB") {
            listOf(0xFFF0A556, 0xFF0A0806, 0xFF336699, 0xFFFFFFFF, 0xFF000000, 0xFFC0392B).forEach { argb ->
                val color = Color(argb)

                Oklch.fromSrgb(color).toColor().channels().toList() shouldBe color.channels().toList()
            }
        }

        it("TC-105: puts white at full lightness with no chroma") {
            val white = Oklch.fromSrgb(Color.White)

            white.l shouldBe (1.0 plusOrMinus 0.001)
            white.c shouldBe (0.0 plusOrMinus 0.001)
        }

        it("TC-106: keeps hue in 0 until 360") {
            listOf(0xFFFF0000, 0xFF0000FF, 0xFF00FF00, 0xFFFF00FF).forEach {
                Oklch.fromSrgb(Color(it)).h shouldBe (180.0 plusOrMinus 180.0)
            }
        }
    }

    describe("chroma") {

        it("TC-107: scales chroma and leaves the rest") {
            Oklch(0.5, 0.1, 200.0, 0.8).chroma(0.5) shouldBe Oklch(0.5, 0.05, 200.0, 0.8)
        }
    }

    describe("parse") {

        it("TC-108: reads unitless oklch") {
            Oklch.parse("oklch(0.55 0.09 200)") shouldBe Oklch(0.55, 0.09, 200.0, 1.0)
        }

        it("TC-109: reads a percentage lightness and alpha") {
            Oklch.parse("oklch(55% 0.09 200 / 0.5)") shouldBe Oklch(0.55, 0.09, 200.0, 0.5)
            // CSS reading: anything marked % is a percentage, even at or below 1.
            Oklch.parse("oklch(1% 0 0)")!!.l shouldBe 0.01
        }

        it("TC-110: keeps lightness of 1 or less as is") {
            Oklch.parse("oklch(1 0 0)")!!.l shouldBe 1.0
        }

        it("TC-111: accepts commas, mixed case and surrounding space") {
            Oklch.parse("  OKLCH(0.4, 0.1, 30)  ") shouldBe Oklch(0.4, 0.1, 30.0, 1.0)
        }

        it("TC-112: reads #rrggbb") {
            val parsed = Oklch.parse("#112233").shouldNotBeNull()

            parsed shouldBe Oklch.fromSrgb(0x11 / 255.0, 0x22 / 255.0, 0x33 / 255.0)
        }

        it("TC-113: returns null for garbage") {
            listOf("", "garbage", "oklch(", "oklch()", "oklch(0.5 0.1)", "oklch(a b c)", "#12", "#gggggg", "red")
                .forEach { Oklch.parse(it).shouldBeNull() }
        }

        it("TC-30: parses the #13 acceptance inputs") {
            val unitless = Oklch.parse("oklch(0.55 0.09 200)").shouldNotBeNull()
            unitless.l shouldBe 0.55
            unitless.c shouldBe 0.09
            unitless.h shouldBe 200.0

            Oklch.parse("oklch(55% 0.09 200 / 0.5)").shouldNotBeNull().alpha shouldBe 0.5

            val (r, g, b) = Oklch.parse("#112233").shouldNotBeNull().toColor().channels().toList()
            r.toDouble() shouldBe (0x11.toDouble() plusOrMinus 1.0)
            g.toDouble() shouldBe (0x22.toDouble() plusOrMinus 1.0)
            b.toDouble() shouldBe (0x33.toDouble() plusOrMinus 1.0)

            Oklch.parse("garbage").shouldBeNull()
            Oklch.parse("").shouldBeNull()
        }
    }
})
