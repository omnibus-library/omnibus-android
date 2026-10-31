package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.ui.theme.OmnibusPalette
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
})
