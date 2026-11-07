package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.reader.ReaderTheme
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import java.io.File

class ReaderBandsTest : DescribeSpec({

    describe("the bands around the page") {
        it("TC-245: match the ones reader.html reserves around #stage") {
            // Unit tests run with the module directory as the working directory.
            val readerHtml = File("src/main/assets/reader/reader.html").readText()
            fun band(side: String) =
                Regex("""$side: calc\(var\(--inset-$side, 0px\) \+ (\d+)px\);""")
                    .find(readerHtml)!!.groupValues[1].toInt()

            band("top") shouldBe ReaderBands.Top.value.toInt()
            band("bottom") shouldBe ReaderBands.Bottom.value.toInt()
            band("left") shouldBe ReaderBands.Side.value.toInt()
            band("right") shouldBe ReaderBands.Side.value.toInt()
        }
    }

    describe("the reading theme's page ground") {
        it("TC-246: matches what the glue paints, for every theme") {
            val glue = File("src/main/assets/reader/epub-reader-glue.js").readText()

            checkAll(Exhaustive.enum<ReaderTheme>()) { theme ->
                Regex("""${theme.token}: "(#[0-9a-f]{6})"""").find(glue)!!.groupValues[1] shouldBe theme.groundHex
            }
        }

        it("TC-247: converts to an opaque ARGB int") {
            ReaderTheme.Dark.groundArgb shouldBe 0xFF201E1B.toInt()
            ReaderTheme.Black.groundArgb shouldBe 0xFF000000.toInt()
        }
    }
})
