package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.ui.theme.Spacing
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File

class ReaderBandsTest : DescribeSpec({

    // Unit tests run with the module directory as the working directory.
    val readerHtml = File("src/main/assets/reader/reader.html").readText()
    val glue = File("src/main/assets/reader/epub-reader-glue.js").readText()

    describe("ReaderBands") {

        it("TC-245: match the bands reader.html reserves around #stage") {
            fun band(side: String) =
                Regex("""$side: calc\(var\(--inset-$side, 0px\) \+ (\d+)px\);""")
                    .find(readerHtml)!!.groupValues[1].toInt()

            band("top") shouldBe ReaderBands.Top.value.toInt()
            band("bottom") shouldBe ReaderBands.Bottom.value.toInt()
            band("left") shouldBe ReaderBands.Side.value.toInt()
            band("right") shouldBe ReaderBands.Side.value.toInt()
        }
    }

    describe("ReaderMenu") {

        it("RC-22: chrome controls fit the reserved bands") {
            (ReaderMenu.buttonSize >= 46.dp) shouldBe true
            (ReaderMenu.rowHeight >= 46.dp) shouldBe true
            (ReaderMenu.buttonSize + Spacing.xs <= ReaderBands.Top) shouldBe true
            (ReaderMenu.buttonSize + Spacing.xs <= ReaderBands.Bottom) shouldBe true
        }
    }

    describe("ReaderTheme") {

        it("TC-246: uses the page grounds the glue paints") {
            ReaderTheme.entries.forEach { theme ->
                Regex("""${theme.token}: "(#[0-9a-f]{6})"""").find(glue)!!.groupValues[1] shouldBe theme.groundHex
            }
        }

        it("TC-247: converts a ground to an opaque ARGB int") {
            ReaderTheme.Dark.groundArgb shouldBe 0xFF201E1B.toInt()
            ReaderTheme.Black.groundArgb shouldBe 0xFF000000.toInt()
        }
    }
})
