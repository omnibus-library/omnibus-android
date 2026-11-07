package com.omnibus.omnibus.data.reader

import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

private val fontSizes = ReaderSettings.FONT_SIZE_RANGE
private val tenths = ReaderSettings.LINE_HEIGHT_TENTHS

class ReaderSettingsTest : DescribeSpec({

    /** The line height for [tenths] as a person would write it, which is also how it must print. */
    fun printed(tenths: Int): String = "${tenths / 10}.${tenths % 10}"

    describe("stepping the font size") {

        it("moves by one regardless of the direction") {
            val size = randomInt(fontSizes.first + 1 until fontSizes.last)

            checkAll(Exhaustive.of(+1, -1)) { step ->
                randomReaderSettings().copy(fontSize = size).withFontSizeStep(step).fontSize shouldBe size + step
            }
        }

        context("when the size is at a bound") {

            it("stays there regardless of the bound") {
                checkAll(Exhaustive.of(fontSizes.last to +1, fontSizes.first to -1)) { (bound, step) ->
                    randomReaderSettings().copy(fontSize = bound).withFontSizeStep(step).fontSize shouldBe bound
                }
            }
        }
    }

    describe("stepping the line height") {

        it("moves by exactly one tenth regardless of the starting height or direction") {
            checkAll(Exhaustive.collection((tenths.first + 1 until tenths.last).toList()), Exhaustive.of(+1, -1)) { start, step ->
                val stepped = randomReaderSettings().copy(lineHeight = start / 10.0).withLineHeightStep(step)

                stepped.lineHeight.toString() shouldBe printed(start + step)
            }
        }

        context("when the height is at a bound") {

            it("stays there regardless of the bound") {
                checkAll(Exhaustive.of(tenths.last to +1, tenths.first to -1)) { (bound, step) ->
                    val height = bound / 10.0

                    randomReaderSettings().copy(lineHeight = height).withLineHeightStep(step).lineHeight shouldBe height
                }
            }
        }
    }

    describe("normalizing the settings") {

        it("clamps a font size regardless of which side it is out on") {
            checkAll(Exhaustive.of(fontSizes.first - 1 to fontSizes.first, fontSizes.last + 1 to fontSizes.last)) { (size, clamped) ->
                randomReaderSettings().copy(fontSize = size).normalized().fontSize shouldBe clamped
            }
        }

        it("clamps a line height regardless of which side it is out on") {
            checkAll(
                Exhaustive.of(
                    (tenths.first - 1) / 10.0 to tenths.first / 10.0,
                    (tenths.last + 1) / 10.0 to tenths.last / 10.0,
                ),
            ) { (height, clamped) ->
                randomReaderSettings().copy(lineHeight = height).normalized().lineHeight shouldBe clamped
            }
        }

        it("rounds a line height to one decimal in either direction") {
            checkAll(Exhaustive.of(1.64 to 1.6, 1.66 to 1.7)) { (height, rounded) ->
                randomReaderSettings().copy(lineHeight = height).normalized().lineHeight shouldBe rounded
            }
        }

        context("when every value is in range") {

            it("changes nothing") {
                val settings = randomReaderSettings()

                settings.normalized() shouldBe settings
            }
        }
    }

    describe("whether a step is possible") {

        context("when both values are inside the bounds") {

            it("allows every step") {
                val settings = randomReaderSettings().copy(
                    fontSize = randomInt(fontSizes.first + 1 until fontSizes.last),
                    lineHeight = randomInt(tenths.first + 1 until tenths.last) / 10.0,
                )

                settings.canDecreaseFontSize shouldBe true
                settings.canIncreaseFontSize shouldBe true
                settings.canDecreaseLineHeight shouldBe true
                settings.canIncreaseLineHeight shouldBe true
            }
        }

        context("when both values are at their smallest") {

            it("refuses to go smaller") {
                val settings = randomReaderSettings().copy(fontSize = fontSizes.first, lineHeight = tenths.first / 10.0)

                settings.canDecreaseFontSize shouldBe false
                settings.canDecreaseLineHeight shouldBe false
            }
        }

        context("when both values are at their largest") {

            it("refuses to go larger") {
                val settings = randomReaderSettings().copy(fontSize = fontSizes.last, lineHeight = tenths.last / 10.0)

                settings.canIncreaseFontSize shouldBe false
                settings.canIncreaseLineHeight shouldBe false
            }
        }
    }

    describe("the margins' CSS") {

        it("is a percentage length regardless of the margin, because a bare number is dropped") {
            checkAll(Exhaustive.enum<ReaderMargins>()) { margins ->
                margins.css shouldMatch Regex("""\d+%""")
            }
        }
    }

    describe("decoding a margins token") {

        it("reads back its own token regardless of the margin") {
            checkAll(Exhaustive.enum<ReaderMargins>()) { margins ->
                ReaderMargins.fromToken(margins.token) shouldBe margins
            }
        }

        it("gives nothing regardless of whether the token is unknown, empty or missing") {
            checkAll(Exhaustive.of(null, "", "unknown-${randomString()}")) { token ->
                ReaderMargins.fromToken(token).shouldBeNull()
            }
        }
    }

    describe("decoding a theme token") {

        it("reads back its own token regardless of the theme") {
            checkAll(Exhaustive.enum<ReaderTheme>()) { theme ->
                ReaderTheme.fromToken(theme.token) shouldBe theme
            }
        }

        it("gives nothing regardless of whether the token is unknown, empty or missing") {
            checkAll(Exhaustive.of(null, "", "unknown-${randomString()}")) { token ->
                ReaderTheme.fromToken(token).shouldBeNull()
            }
        }
    }
})
