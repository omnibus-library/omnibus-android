package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.ui.theme.contrastRatio
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

/** The red, green and blue channels of [color] on the 0 to 255 scale. */
private fun Color.channels255(): List<Double> = listOf(red, green, blue).map { it.toDouble() * 255.0 }

class ReaderChromeColorsTest : DescribeSpec({

    describe("the sync offer's Go action") {
        it("TC-116: reaches WCAG AA on the plate of every reading theme") {
            checkAll(Exhaustive.enum<ReaderTheme>()) { theme ->
                val action = readerSyncOfferActionColor(theme).luminance()
                val plate = readerChromeColors(theme).plate.luminance()

                ((maxOf(action, plate) + 0.05) / (minOf(action, plate) + 0.05)).toDouble() shouldBeGreaterThanOrEqual 4.5
            }
        }
    }

    describe("the highlight menu's Delete colour") {
        it("#300 TC-13: reaches WCAG AA on the plate of every reading theme") {
            checkAll(Exhaustive.enum<ReaderTheme>()) { theme ->
                val colors = readerChromeColors(theme)

                contrastRatio(colors.danger, colors.plate) shouldBeGreaterThanOrEqual 4.5
            }
        }
    }

    describe("the selection's tint") {
        context("on a light reading theme") {
            it("#54 TC-8: is the cool tint #516B8B, within one 8-bit step per channel") {
                checkAll(Exhaustive.of(ReaderTheme.Light, ReaderTheme.Sepia)) { theme ->
                    readerSelectionTint(theme).channels255().zip(listOf(0x51, 0x6B, 0x8B)).forEach { (actual, expected) ->
                        actual shouldBe (expected.toDouble() plusOrMinus 1.0)
                    }
                }
            }
        }

        context("on a dark reading theme") {
            it("#54 TC-8: is the cool tint #92ADD1, within one 8-bit step per channel") {
                checkAll(Exhaustive.of(ReaderTheme.Dark, ReaderTheme.Black)) { theme ->
                    readerSelectionTint(theme).channels255().zip(listOf(0x92, 0xAD, 0xD1)).forEach { (actual, expected) ->
                        actual shouldBe (expected.toDouble() plusOrMinus 1.0)
                    }
                }
            }
        }
    }

    describe("the selection's wash alpha") {
        context("on a light reading theme") {
            it("#54 TC-8: is 24%") {
                checkAll(Exhaustive.of(ReaderTheme.Light, ReaderTheme.Sepia)) { theme ->
                    readerSelectionWashAlpha(theme) shouldBe 0.24f
                }
            }
        }

        context("on a dark reading theme") {
            it("#54 TC-8: is 30%") {
                checkAll(Exhaustive.of(ReaderTheme.Dark, ReaderTheme.Black)) { theme ->
                    readerSelectionWashAlpha(theme) shouldBe 0.30f
                }
            }
        }
    }
})
