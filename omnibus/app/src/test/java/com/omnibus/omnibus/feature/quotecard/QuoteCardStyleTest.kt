package com.omnibus.omnibus.feature.quotecard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

private val LIGHT_INK = Color(0xFFF5F0E8)
private val DARK_INK = Color(0xFF221D18)

/** Compares at 8 bits per channel, which is all a rendered card keeps. */
private infix fun Color.shouldMatch(expected: Color) = toArgb() shouldBe expected.toArgb()

class QuoteCardStyleTest : DescribeSpec({

    describe("passageSizeFraction") {
        listOf(
            0 to 0.082f, 89 to 0.082f, 90 to 0.066f, 179 to 0.066f, 180 to 0.052f,
            319 to 0.052f, 320 to 0.042f, 519 to 0.042f, 520 to 0.034f, 2_000 to 0.034f,
        ).forEach { (length, fraction) ->
            it("returns $fraction for a passage of $length characters") {
                passageSizeFraction("a".repeat(length)) shouldBe fraction
            }
        }

        it("counts the collapsed passage, not the source whitespace") {
            passageSizeFraction("  \n\t" + "a".repeat(89) + " \n\n  ") shouldBe 0.082f
            passageSizeFraction("a".repeat(44) + "\n\n    \t" + "a".repeat(45)) shouldBe 0.066f
        }
    }

    describe("collapsingWhitespace") {
        it("turns each run of spaces, tabs and newlines into one space and trims the ends") {
            "  The  road\n\n\tgoes   on \n".collapsingWhitespace() shouldBe "The road goes on"
        }
    }

    describe("QuoteSize and QuoteShape") {
        it("scales the passage S 0.85, M 1.0 and L 1.18") {
            QuoteSize.entries.map { it.scale } shouldBe listOf(0.85f, 1.0f, 1.18f)
        }

        it("lists the shapes Square, Post, Portrait, Story with their ratios") {
            QuoteShape.entries.map { it.name to it.ratio } shouldBe listOf(
                "Square" to 1f,
                "Post" to 0.8f,
                "Portrait" to 0.75f,
                "Story" to 9f / 16f,
            )
        }
    }

    describe("exportSize") {
        it("is 1080 px wide and as tall as the shape makes it") {
            QuoteShape.entries.map { exportSize(it) } shouldBe listOf(
                IntSize(1080, 1080),
                IntSize(1080, 1350),
                IntSize(1080, 1440),
                IntSize(1080, 1920),
            )
        }
    }

    describe("fill of a solid background") {
        val anyTone = Oklch(0.5, 0.1, 30.0)

        it("drifts a light-ink wash to a darker, quieter, hue-shifted second stop") {
            val fill = QuoteBackground.Wash(QuoteWash.Moss).fill(anyTone)

            fill.from shouldMatch Oklch(0.38, 0.075, 155.0).toColor()
            fill.to shouldMatch Oklch(0.28, 0.06375, 167.0).toColor()
            fill.ink shouldMatch Oklch(0.95, 0.012, 155.0).toColor()
            fill.angle shouldBe 135
        }

        it("drifts a dark-ink wash lighter and paints it dark ink") {
            val fill = QuoteBackground.Wash(QuoteWash.Paper).fill(anyTone)

            fill.from shouldMatch Oklch(0.93, 0.017, 80.0).toColor()
            fill.to shouldMatch Oklch(0.98, 0.01445, 92.0).toColor()
            fill.ink shouldMatch Oklch(0.24, 0.02, 80.0).toColor()
        }

        it("floors the drifted lightness at 0.06") {
            val fill = QuoteBackground.CustomSolid(0xFF000000.toInt()).fill(anyTone)

            fill.to shouldMatch Oklch(0.06, 0.0, 0.0).toColor()
        }

        listOf(
            "below the clamp" to (0.10 to 0.28),
            "inside the clamp" to (0.40 to 0.40),
            "above the clamp" to (0.80 to 0.46),
        ).forEach { (name, case) ->
            it("clamps the book tone's lightness for a tone $name") {
                val (toneL, baseL) = case
                val fill = QuoteBackground.Book.fill(Oklch(toneL, 0.09, 200.0))

                fill.from shouldMatch Oklch(baseL, 0.09, 200.0).toColor()
                fill.to shouldMatch Oklch(baseL - 0.10, 0.09 * 0.85, 212.0).toColor()
                fill.ink shouldMatch Oklch(0.95, 0.012, 200.0).toColor()
            }
        }

        it("raises a book tone's chroma to 0.05") {
            QuoteBackground.Book.fill(Oklch(0.4, 0.01, 200.0)).from shouldMatch Oklch(0.4, 0.05, 200.0).toColor()
        }

        it("gives a near-white custom colour dark ink and a near-black one light ink") {
            QuoteBackground.CustomSolid(0xFFFAF8F4.toInt()).fill(anyTone).ink.luminance().toDouble() shouldBe
                (0.0 plusOrMinus 0.1)
            QuoteBackground.CustomSolid(0xFF14110F.toInt()).fill(anyTone).ink.luminance().toDouble() shouldBe
                (0.85 plusOrMinus 0.1)
        }
    }

    describe("fill of a gradient background") {
        val anyTone = Oklch(0.5, 0.1, 30.0)

        listOf(
            Triple(QuoteGradientPreset.Ember, 0xFF7A1F16 to 0xFFA24C16, LIGHT_INK),
            Triple(QuoteGradientPreset.Dusk, 0xFF2E2660 to 0xFF9A4060, LIGHT_INK),
            Triple(QuoteGradientPreset.Lagoon, 0xFF0B4A5A to 0xFF26357F, LIGHT_INK),
            Triple(QuoteGradientPreset.Grove, 0xFF183F2A to 0xFF7A5C18, LIGHT_INK),
            Triple(QuoteGradientPreset.Dawn, 0xFFF7E1C8 to 0xFFEEB0A8, DARK_INK),
        ).forEach { (preset, stops, ink) ->
            it("fills ${preset.name} with exactly its two stops at 135 degrees") {
                QuoteBackground.Preset(preset).fill(anyTone) shouldBe
                    QuoteFill(Color(stops.first), Color(stops.second), 135, ink)
            }
        }

        it("fills a cover pair with its stops and the ink its pair names") {
            val light = CoverPair(0xFF5D1F1C.toInt(), 0xFFC01A1A.toInt(), lightInk = true)
            val dark = CoverPair(0xFFEFE4E6.toInt(), 0xFFD5C2C9.toInt(), lightInk = false)

            QuoteBackground.Cover(light).fill(anyTone) shouldBe
                QuoteFill(Color(0xFF5D1F1C), Color(0xFFC01A1A), 135, LIGHT_INK)
            QuoteBackground.Cover(dark).fill(anyTone) shouldBe
                QuoteFill(Color(0xFFEFE4E6), Color(0xFFD5C2C9), 135, DARK_INK)
        }

        it("keeps a custom gradient's own angle and stops") {
            val fill = QuoteBackground.CustomGradient(0xFF102030.toInt(), 0xFF304050.toInt(), angle = 270).fill(anyTone)

            fill.from shouldBe Color(0xFF102030)
            fill.to shouldBe Color(0xFF304050)
            fill.angle shouldBe 270
            fill.ink shouldBe LIGHT_INK
        }

        it("inks a custom gradient by the worst contrast over both stops, not by either stop alone") {
            // Light ink beats dark on the first stop alone, but over the second it falls to about 2:1.
            val first = 0xFF6C6C6C.toInt()
            val second = 0xFFAAAAAA.toInt()

            inkFor(first, first) shouldBe LIGHT_INK
            QuoteBackground.CustomGradient(first, second).fill(Oklch(0.5, 0.1, 30.0)).ink shouldBe DARK_INK
        }
    }

    describe("swatchesOf") {
        val pairs = listOf(
            CoverPair(0xFF5D1F1C.toInt(), 0xFFC01A1A.toInt(), lightInk = true),
            CoverPair(0xFFEFE4E6.toInt(), 0xFFD5C2C9.toInt(), lightInk = false),
        )

        it("lists the solid row as the book's colour, then ink, paper, moss, rose, dusk and sea") {
            swatchesOf(QuoteBackgroundKind.Solid, pairs) shouldBe listOf(
                QuoteBackground.Book,
                QuoteBackground.Wash(QuoteWash.Ink),
                QuoteBackground.Wash(QuoteWash.Paper),
                QuoteBackground.Wash(QuoteWash.Moss),
                QuoteBackground.Wash(QuoteWash.Rose),
                QuoteBackground.Wash(QuoteWash.Dusk),
                QuoteBackground.Wash(QuoteWash.Sea),
            )
        }

        it("lists the gradient row as the cover's pairs in order, then Ember, Dusk, Lagoon, Grove and Dawn") {
            swatchesOf(QuoteBackgroundKind.Gradient, pairs) shouldBe listOf(
                QuoteBackground.Cover(pairs[0]),
                QuoteBackground.Cover(pairs[1]),
                QuoteBackground.Preset(QuoteGradientPreset.Ember),
                QuoteBackground.Preset(QuoteGradientPreset.Dusk),
                QuoteBackground.Preset(QuoteGradientPreset.Lagoon),
                QuoteBackground.Preset(QuoteGradientPreset.Grove),
                QuoteBackground.Preset(QuoteGradientPreset.Dawn),
            )
        }

        it("lists only the presets when the cover offers no pairs") {
            swatchesOf(QuoteBackgroundKind.Gradient, emptyList()).first() shouldBe
                QuoteBackground.Preset(QuoteGradientPreset.Ember)
            swatchesOf(QuoteBackgroundKind.Gradient, emptyList()).size shouldBe 5
        }
    }

    describe("pickerArgb and pickerOf") {
        fun channels(argb: Int) = listOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)

        it("turns hue 65, chroma 0.13 and lightness 0.78 into the Atrium accent, #F0A556") {
            channels(pickerArgb(65.0, 0.13, 0.78)).zip(listOf(0xF0, 0xA5, 0x56)).forEach { (actual, expected) ->
                actual.toDouble() shouldBe (expected.toDouble() plusOrMinus 1.0)
            }
        }

        it("gives an opaque grey for a colour with no chroma") {
            val (r, g, b) = channels(pickerArgb(200.0, 0.0, 0.5))

            (pickerArgb(200.0, 0.0, 0.5) ushr 24) shouldBe 0xFF
            r shouldBe g
            g shouldBe b
        }

        it("reads a colour back so that picking it again lands within a step of every channel") {
            listOf(0xFF7A1F16, 0xFFF0A556, 0xFF336699, 0xFF14110F, 0xFFFAF8F4, 0xFF2A9D8F).forEach { argb ->
                val picked = pickerOf(argb.toInt())

                channels(pickerArgb(picked.hue, picked.chroma, picked.lightness)).zip(channels(argb.toInt()))
                    .forEach { (again, original) -> again.toDouble() shouldBe (original.toDouble() plusOrMinus 1.0) }
            }
        }
    }

    describe("gradientEndpoints") {
        val wide = Size(200f, 100f)

        it("runs 135 degrees from the top-left corner to the bottom-right corner") {
            gradientEndpoints(135, wide) shouldBe (Offset(0f, 0f) to Offset(200f, 100f))
        }

        it("runs 90 degrees from the left edge's middle to the right edge's middle") {
            gradientEndpoints(90, wide) shouldBe (Offset(0f, 50f) to Offset(200f, 50f))
        }

        it("runs 180 degrees from the top edge's middle to the bottom edge's middle") {
            gradientEndpoints(180, wide) shouldBe (Offset(100f, 0f) to Offset(100f, 100f))
        }

        it("runs 0 degrees bottom to top and 270 degrees right to left") {
            gradientEndpoints(0, wide) shouldBe (Offset(100f, 100f) to Offset(100f, 0f))
            gradientEndpoints(270, wide) shouldBe (Offset(200f, 50f) to Offset(0f, 50f))
        }

        it("runs 45 degrees bottom-left to top-right and 315 degrees bottom-right to top-left") {
            gradientEndpoints(45, wide) shouldBe (Offset(0f, 100f) to Offset(200f, 0f))
            gradientEndpoints(315, wide) shouldBe (Offset(200f, 100f) to Offset(0f, 0f))
        }
    }
})
