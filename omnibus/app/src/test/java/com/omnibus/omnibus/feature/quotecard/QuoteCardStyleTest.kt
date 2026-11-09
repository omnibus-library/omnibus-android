package com.omnibus.omnibus.feature.quotecard

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntSize
import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.data.quotecard.randomCoverPair
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

private val LIGHT_INK = Color(0xFFF5F0E8)
private val DARK_INK = Color(0xFF221D18)

/** Compares at 8 bits per channel, which is all a rendered card keeps. */
private infix fun Color.shouldMatch(expected: Color) = toArgb() shouldBe expected.toArgb()

private fun channels(argb: Int) = listOf(argb shr 16 and 0xFF, argb shr 8 and 0xFF, argb and 0xFF)

/** Compares every channel to within a step, since a colour passes through floating point on the way. */
private infix fun Int.shouldBeWithinAStepOf(expected: Int) {
    channels(this).zip(channels(expected)).forEach { (actual, wanted) ->
        actual.toDouble() shouldBe (wanted.toDouble() plusOrMinus 1.0)
    }
}

class QuoteCardStyleTest : DescribeSpec({

    describe("sizing a passage to the card") {
        listOf(
            0 to 0.082f, 89 to 0.082f, 90 to 0.066f, 179 to 0.066f, 180 to 0.052f,
            319 to 0.052f, 320 to 0.042f, 519 to 0.042f, 520 to 0.034f, 2_000 to 0.034f,
        ).forEach { (length, fraction) ->
            context("when the passage is $length characters") {
                it("sizes the text at $fraction of the card") {
                    passageSizeFraction("a".repeat(length)) shouldBe fraction
                }
            }
        }

        context("when the passage has a run of whitespace in it") {
            it("counts the run as one space") {
                passageSizeFraction("a".repeat(44) + "\n\n    \t" + "a".repeat(45)) shouldBe 0.066f
            }
        }

        context("when the passage has whitespace around it") {
            it("doesn't count that whitespace") {
                passageSizeFraction("  \n\t" + "a".repeat(89) + " \n\n  ") shouldBe 0.082f
            }
        }
    }

    describe("collapsing whitespace") {
        it("turns each run of spaces, tabs and newlines into one space") {
            "The  road\n\n\tgoes   on".collapsingWhitespace() shouldBe "The road goes on"
        }

        it("trims the ends") {
            " \n The road \t\n".collapsingWhitespace() shouldBe "The road"
        }
    }

    describe("the card's sizes") {
        it("scale the passage S 0.85, M 1.0 and L 1.18") {
            QuoteSize.entries.map { it.scale } shouldBe listOf(0.85f, 1.0f, 1.18f)
        }
    }

    describe("the card's shapes") {
        it("are Square, Post, Portrait and Story, with their ratios") {
            QuoteShape.entries.map { it.name to it.ratio } shouldBe listOf(
                "Square" to 1f,
                "Post" to 0.8f,
                "Portrait" to 0.75f,
                "Story" to 9f / 16f,
            )
        }
    }

    describe("the exported image's size") {
        it("is 1080 px wide, whatever the shape") {
            checkAll(Exhaustive.collection(QuoteShape.entries)) { shape ->
                exportSize(shape).width shouldBe 1080
            }
        }

        it("is as tall as the shape makes it") {
            QuoteShape.entries.map { exportSize(it).height } shouldBe listOf(1080, 1350, 1440, 1920)
        }
    }

    describe("the colours of a solid background") {
        val anyTone = Oklch(0.5, 0.1, 30.0)

        context("when it is a wash with light ink") {
            fun fill() = QuoteBackground.Wash(QuoteWash.Moss).fill(anyTone)

            it("starts from the wash's own colour") {
                fill().from shouldMatch Oklch(0.38, 0.075, 155.0).toColor()
            }

            it("drifts to a darker, quieter, hue-shifted second stop") {
                fill().to shouldMatch Oklch(0.28, 0.06375, 167.0).toColor()
            }

            it("paints light ink") {
                fill().ink shouldMatch Oklch(0.95, 0.012, 155.0).toColor()
            }
        }

        context("when it is a wash with dark ink") {
            fun fill() = QuoteBackground.Wash(QuoteWash.Paper).fill(anyTone)

            it("starts from the wash's own colour") {
                fill().from shouldMatch Oklch(0.93, 0.017, 80.0).toColor()
            }

            it("drifts to a lighter second stop") {
                fill().to shouldMatch Oklch(0.98, 0.01445, 92.0).toColor()
            }

            it("paints dark ink") {
                fill().ink shouldMatch Oklch(0.24, 0.02, 80.0).toColor()
            }
        }

        context("when it is the book's colour") {
            it("runs at 135 degrees") {
                QuoteBackground.Book.fill(anyTone).angle shouldBe 135
            }

            it("raises the tone's chroma to 0.05") {
                QuoteBackground.Book.fill(Oklch(0.4, 0.01, 200.0)).from shouldMatch Oklch(0.4, 0.05, 200.0).toColor()
            }

            listOf(
                "below the clamp" to (0.10 to 0.28),
                "inside the clamp" to (0.40 to 0.40),
                "above the clamp" to (0.80 to 0.46),
            ).forEach { (name, case) ->
                val (toneL, baseL) = case

                context("when the tone's lightness is $name") {
                    fun fill() = QuoteBackground.Book.fill(Oklch(toneL, 0.09, 200.0))

                    it("starts from the clamped lightness") {
                        fill().from shouldMatch Oklch(baseL, 0.09, 200.0).toColor()
                    }

                    it("drifts to a darker, quieter, hue-shifted second stop") {
                        fill().to shouldMatch Oklch(baseL - 0.10, 0.09 * 0.85, 212.0).toColor()
                    }

                    it("paints light ink") {
                        fill().ink shouldMatch Oklch(0.95, 0.012, 200.0).toColor()
                    }
                }
            }
        }

        context("when it is black") {
            it("floors the second stop's lightness at 0.06") {
                QuoteBackground.CustomSolid(0xFF000000.toInt()).fill(anyTone).to shouldMatch
                    Oklch(0.06, 0.0, 0.0).toColor()
            }
        }

        context("when it is a near-white custom colour") {
            it("paints dark ink") {
                QuoteBackground.CustomSolid(0xFFFAF8F4.toInt()).fill(anyTone).ink.luminance().toDouble() shouldBe
                    (0.0 plusOrMinus 0.1)
            }
        }

        context("when it is a near-black custom colour") {
            it("paints light ink") {
                QuoteBackground.CustomSolid(0xFF14110F.toInt()).fill(anyTone).ink.luminance().toDouble() shouldBe
                    (0.85 plusOrMinus 0.1)
            }
        }
    }

    describe("the colours of a gradient background") {
        val anyTone = Oklch(0.5, 0.1, 30.0)

        listOf(
            Triple(QuoteGradientPreset.Ember, 0xFF7A1F16 to 0xFFA24C16, LIGHT_INK),
            Triple(QuoteGradientPreset.Dusk, 0xFF2E2660 to 0xFF9A4060, LIGHT_INK),
            Triple(QuoteGradientPreset.Lagoon, 0xFF0B4A5A to 0xFF26357F, LIGHT_INK),
            Triple(QuoteGradientPreset.Grove, 0xFF183F2A to 0xFF7A5C18, LIGHT_INK),
            Triple(QuoteGradientPreset.Dawn, 0xFFF7E1C8 to 0xFFEEB0A8, DARK_INK),
        ).forEach { (preset, stops, ink) ->
            context("when it is the ${preset.name} preset") {
                it("fills with exactly its two stops at 135 degrees, in its ink") {
                    QuoteBackground.Preset(preset).fill(anyTone) shouldBe
                        QuoteFill(Color(stops.first), Color(stops.second), 135, ink)
                }
            }
        }

        context("when it is a cover pair that keeps light ink") {
            it("fills with the pair's stops and light ink") {
                val pair = randomCoverPair().copy(lightInk = true)

                QuoteBackground.Cover(pair).fill(anyTone) shouldBe
                    QuoteFill(Color(pair.fromArgb), Color(pair.toArgb), 135, LIGHT_INK)
            }
        }

        context("when it is a cover pair that keeps dark ink") {
            it("fills with the pair's stops and dark ink") {
                val pair = randomCoverPair().copy(lightInk = false)

                QuoteBackground.Cover(pair).fill(anyTone) shouldBe
                    QuoteFill(Color(pair.fromArgb), Color(pair.toArgb), 135, DARK_INK)
            }
        }

        context("when it is a custom gradient dark enough for light ink") {
            it("fills with its own stops and angle") {
                val fill = QuoteBackground.CustomGradient(0xFF102030.toInt(), 0xFF304050.toInt(), angle = 270).fill(anyTone)

                fill shouldBe QuoteFill(Color(0xFF102030), Color(0xFF304050), 270, LIGHT_INK)
            }
        }

        // Light ink beats dark on the first stop alone, but over the second it falls to about 2:1.
        context("when light ink suits the first stop but not the second") {
            val first = 0xFF6C6C6C.toInt()
            val second = 0xFFAAAAAA.toInt()

            it("paints light ink over the first stop alone") {
                inkFor(first, first) shouldBe LIGHT_INK
            }

            it("paints dark ink, judged by the worst contrast over both stops") {
                QuoteBackground.CustomGradient(first, second).fill(Oklch(0.5, 0.1, 30.0)).ink shouldBe DARK_INK
            }
        }
    }

    describe("the swatches in a row") {
        context("when the row is Solid") {
            it("lists the book's colour, then ink, paper, moss, rose, dusk and sea") {
                swatchesOf(QuoteBackgroundKind.Solid, List(2) { randomCoverPair() }) shouldBe listOf(
                    QuoteBackground.Book,
                    QuoteBackground.Wash(QuoteWash.Ink),
                    QuoteBackground.Wash(QuoteWash.Paper),
                    QuoteBackground.Wash(QuoteWash.Moss),
                    QuoteBackground.Wash(QuoteWash.Rose),
                    QuoteBackground.Wash(QuoteWash.Dusk),
                    QuoteBackground.Wash(QuoteWash.Sea),
                )
            }
        }

        context("when the row is Gradient") {
            val presets = listOf(
                QuoteBackground.Preset(QuoteGradientPreset.Ember),
                QuoteBackground.Preset(QuoteGradientPreset.Dusk),
                QuoteBackground.Preset(QuoteGradientPreset.Lagoon),
                QuoteBackground.Preset(QuoteGradientPreset.Grove),
                QuoteBackground.Preset(QuoteGradientPreset.Dawn),
            )

            context("when the cover offers pairs") {
                it("lists the cover's pairs in order, then Ember, Dusk, Lagoon, Grove and Dawn") {
                    val pairs: List<CoverPair> = List(2) { randomCoverPair() }

                    swatchesOf(QuoteBackgroundKind.Gradient, pairs) shouldBe pairs.map { QuoteBackground.Cover(it) } + presets
                }
            }

            context("when the cover offers no pairs") {
                it("lists only Ember, Dusk, Lagoon, Grove and Dawn") {
                    swatchesOf(QuoteBackgroundKind.Gradient, emptyList()) shouldBe presets
                }
            }
        }
    }

    describe("the colour picker's values") {
        it("turns hue 65, chroma 0.13 and lightness 0.78 into the Atrium accent, #F0A556") {
            pickerArgb(65.0, 0.13, 0.78) shouldBeWithinAStepOf 0xF0A556
        }

        context("when the colour has no chroma") {
            it("is opaque") {
                (pickerArgb(randomDouble(0.0, 360.0), 0.0, randomDouble(0.1, 0.9)) ushr 24) shouldBe 0xFF
            }
        }

        it("reads a colour back so that picking it again lands within a step of every channel") {
            val colours = listOf(0xFF7A1F16, 0xFFF0A556, 0xFF336699, 0xFF14110F, 0xFFFAF8F4, 0xFF2A9D8F).map { it.toInt() }

            checkAll(Exhaustive.collection(colours)) { argb ->
                val picked = pickerOf(argb)

                pickerArgb(picked.hue, picked.chroma, picked.lightness) shouldBeWithinAStepOf argb
            }
        }
    }

    describe("a gradient's endpoints") {
        context("when the card is 200 by 100") {
            val wide = Size(200f, 100f)

            listOf(
                135 to "from the top-left corner to the bottom-right corner" to (Offset(0f, 0f) to Offset(200f, 100f)),
                90 to "from the left edge's middle to the right edge's middle" to (Offset(0f, 50f) to Offset(200f, 50f)),
                180 to "from the top edge's middle to the bottom edge's middle" to (Offset(100f, 0f) to Offset(100f, 100f)),
                0 to "from the bottom edge's middle to the top edge's middle" to (Offset(100f, 100f) to Offset(100f, 0f)),
                270 to "from the right edge's middle to the left edge's middle" to (Offset(200f, 50f) to Offset(0f, 50f)),
                45 to "from the bottom-left corner to the top-right corner" to (Offset(0f, 100f) to Offset(200f, 0f)),
                315 to "from the bottom-right corner to the top-left corner" to (Offset(200f, 100f) to Offset(0f, 0f)),
            ).forEach { (named, endpoints) ->
                val (angle, route) = named

                it("runs $angle degrees $route") {
                    gradientEndpoints(angle, wide) shouldBe endpoints
                }
            }
        }
    }
})
