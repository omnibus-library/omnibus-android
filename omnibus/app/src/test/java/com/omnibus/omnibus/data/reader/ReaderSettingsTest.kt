package com.omnibus.omnibus.data.reader

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class ReaderSettingsTest : DescribeSpec({

    describe("ReaderSettings") {

        it("has the documented defaults") {
            val settings = ReaderSettings()

            settings.theme shouldBe ReaderTheme.Dark
            settings.typeface shouldBe ReaderTypeface.Original
            settings.fontSize shouldBe 19
            settings.lineHeight shouldBeExactly 1.6
            settings.margins shouldBe ReaderMargins.Normal
            settings.justify shouldBe true
            settings.spread shouldBe "none"
        }

        it("steps the font size by one and stops at both bounds") {
            ReaderSettings().withFontSizeStep(+1).fontSize shouldBe 20
            ReaderSettings().withFontSizeStep(-1).fontSize shouldBe 18

            ReaderSettings(fontSize = 34).withFontSizeStep(+1).fontSize shouldBe 34
            ReaderSettings(fontSize = 12).withFontSizeStep(-1).fontSize shouldBe 12
            ReaderSettings.FONT_SIZE_RANGE shouldBe 12..34
        }

        it("steps the line height in exact tenths with no float drift") {
            val stepped = ReaderSettings().withLineHeightStep(+1)
            stepped.lineHeight shouldBe 1.7
            stepped.lineHeight.toString() shouldBe "1.7"

            var walk = ReaderSettings(lineHeight = 1.2)
            val seen = mutableListOf(walk.lineHeight.toString())
            repeat(10) {
                walk = walk.withLineHeightStep(+1)
                seen += walk.lineHeight.toString()
            }
            seen shouldBe listOf("1.2", "1.3", "1.4", "1.5", "1.6", "1.7", "1.8", "1.9", "2.0", "2.1", "2.2")

            repeat(10) { walk = walk.withLineHeightStep(-1) }
            walk.lineHeight shouldBe 1.2

            ReaderSettings(lineHeight = 2.2).withLineHeightStep(+1).lineHeight shouldBe 2.2
            ReaderSettings(lineHeight = 1.2).withLineHeightStep(-1).lineHeight shouldBe 1.2
        }

        it("clamps font size and line height and rounds line height to one decimal") {
            ReaderSettings(fontSize = 5).normalized().fontSize shouldBe 12
            ReaderSettings(fontSize = 99).normalized().fontSize shouldBe 34

            ReaderSettings(lineHeight = 0.5).normalized().lineHeight shouldBe 1.2
            ReaderSettings(lineHeight = 9.0).normalized().lineHeight shouldBe 2.2
            ReaderSettings(lineHeight = 1.64).normalized().lineHeight shouldBe 1.6
            ReaderSettings(lineHeight = 1.65).normalized().lineHeight shouldBe 1.7
        }

        it("leaves in-range values unchanged when normalized") {
            val settings = ReaderSettings(fontSize = 24, lineHeight = 1.8, margins = ReaderMargins.Wide)

            settings.normalized() shouldBe settings
        }

        it("reports whether each step is still possible") {
            val low = ReaderSettings(fontSize = 12, lineHeight = 1.2)
            low.canDecreaseFontSize shouldBe false
            low.canIncreaseFontSize shouldBe true
            low.canDecreaseLineHeight shouldBe false
            low.canIncreaseLineHeight shouldBe true

            val high = ReaderSettings(fontSize = 34, lineHeight = 2.2)
            high.canDecreaseFontSize shouldBe true
            high.canIncreaseFontSize shouldBe false
            high.canDecreaseLineHeight shouldBe true
            high.canIncreaseLineHeight shouldBe false
        }
    }

    describe("ReaderMargins") {

        it("maps each margin to its CSS length") {
            ReaderMargins.Narrow.css shouldBe "95%"
            ReaderMargins.Normal.css shouldBe "80%"
            ReaderMargins.Wide.css shouldBe "65%"
            ReaderMargins.entries.size shouldBe 3
        }

        it("decodes each token and rejects unknown ones") {
            ReaderMargins.fromToken("narrow") shouldBe ReaderMargins.Narrow
            ReaderMargins.fromToken("normal") shouldBe ReaderMargins.Normal
            ReaderMargins.fromToken("wide") shouldBe ReaderMargins.Wide
            ReaderMargins.fromToken("huge").shouldBeNull()
            ReaderMargins.fromToken(null).shouldBeNull()
        }
    }

    describe("ReaderTheme") {

        it("decodes the four theme tokens and round-trips each one") {
            ReaderTheme.fromToken("light") shouldBe ReaderTheme.Light
            ReaderTheme.fromToken("sepia") shouldBe ReaderTheme.Sepia
            ReaderTheme.fromToken("dark") shouldBe ReaderTheme.Dark
            ReaderTheme.fromToken("black") shouldBe ReaderTheme.Black

            ReaderTheme.entries.forEach { theme ->
                ReaderTheme.fromToken(theme.token) shouldBe theme
            }
        }

        it("returns null for unknown, empty or missing tokens") {
            ReaderTheme.fromToken("solarized").shouldBeNull()
            ReaderTheme.fromToken("").shouldBeNull()
            ReaderTheme.fromToken(null).shouldBeNull()
        }
    }
})
