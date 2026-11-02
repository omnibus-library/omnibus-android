package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderChromeColorsTest : DescribeSpec({

    describe("readerChromeColors") {

        it("RC-21: each reading theme resolves to its chrome colours") {
            val plates = mapOf(
                ReaderTheme.Light to OmnibusPalette.Light.bg1,
                ReaderTheme.Sepia to Color(0xFFF6F0E2),
                ReaderTheme.Dark to Color(0xFF2E2B27),
                ReaderTheme.Black to Color(0xFF1A1816),
            )
            ReaderTheme.entries.forEach { theme ->
                val colors = readerChromeColors(theme)
                val ink = if (theme.isLight) OmnibusPalette.Light.ink0 else OmnibusPalette.Atrium.ink0

                colors.ink shouldBe ink
                colors.plate shouldBe plates.getValue(theme)
                colors.plate.alpha shouldBe 1f
                colors.labelInk shouldBe ink.copy(alpha = 0.5f)
                colors.border shouldBe ink.copy(alpha = 0.12f)
            }
        }
    }

    describe("readerSwatchInk") {

        it("is the light palette's ink on light pages and the Atrium ink on dark pages") {
            readerSwatchInk(ReaderTheme.Light) shouldBe OmnibusPalette.Light.ink0
            readerSwatchInk(ReaderTheme.Sepia) shouldBe OmnibusPalette.Light.ink0
            readerSwatchInk(ReaderTheme.Dark) shouldBe OmnibusPalette.Atrium.ink0
            readerSwatchInk(ReaderTheme.Black) shouldBe OmnibusPalette.Atrium.ink0
        }

        it("is the same ink the chrome uses on every reading theme") {
            ReaderTheme.entries.forEach { theme ->
                readerSwatchInk(theme) shouldBe readerChromeColors(theme).ink
            }
        }
    }

    describe("readerSyncOfferActionColor") {

        // TC-116: the Go action reaches WCAG AA (4.5:1) on every reading plate, in the colour its theme calls for.
        it("TC-116: the Go action is legible on every plate") {
            ReaderTheme.entries.forEach { theme ->
                val action = readerSyncOfferActionColor(theme)
                val plate = readerChromeColors(theme).plate
                val lighter = maxOf(action.luminance(), plate.luminance())
                val darker = minOf(action.luminance(), plate.luminance())
                val contrast = (lighter + 0.05) / (darker + 0.05)
                val expected = if (theme.isLight) readerChromeColors(theme).ink else OmnibusPalette.Atrium.accent

                withClue("$theme contrast $contrast") {
                    (contrast >= 4.5) shouldBe true
                    action shouldBe expected
                }
            }
        }
    }
})
