package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReaderScriptsTest : DescribeSpec({

    /** Splits `OmnibusReader.init('stage', "<url>", {…})` back into its URL and options. */
    fun parseBoot(script: String): Pair<String, kotlinx.serialization.json.JsonObject> {
        script.startsWith("OmnibusReader.init('stage', ") shouldBe true
        val arguments = script.removePrefix("OmnibusReader.init('stage', ").removeSuffix(")")
        val url = Json.parseToJsonElement(arguments.substringBefore(", {")).jsonPrimitive.content
        val options = Json.parseToJsonElement("{" + arguments.substringAfter(", {")).jsonObject
        return url to options
    }

    describe("boot") {

        it("TC-228: points init at the book on the asset-loader origin") {
            val (url, _) = parseBoot(ReaderScripts.boot("abc-123", ReaderSettings(), cfi = null))

            url shouldBe "https://appassets.androidplatform.net/book/abc-123.epub"
        }

        it("TC-229: carries the settings, the fonts sheet, scripted content and the locations key") {
            val settings = ReaderSettings(
                theme = ReaderTheme.Sepia,
                fontSize = 22,
                fontFamily = "'EB Garamond',Georgia,serif",
                lineHeight = 1.8,
                maxWidth = "65%",
                justify = false,
                spread = "auto",
            )

            val (_, options) = parseBoot(ReaderScripts.boot("abc-123", settings, cfi = "epubcfi(/6/4!/4/2)"))

            options["theme"]!!.jsonPrimitive.content shouldBe "sepia"
            options["fontSize"]!!.jsonPrimitive.int shouldBe 22
            options["fontFamily"]!!.jsonPrimitive.content shouldBe "'EB Garamond',Georgia,serif"
            options["lineHeight"]!!.jsonPrimitive.double shouldBe 1.8
            options["maxWidth"]!!.jsonPrimitive.content shouldBe "65%"
            options["justify"]!!.jsonPrimitive.boolean shouldBe false
            options["spread"]!!.jsonPrimitive.content shouldBe "auto"
            options["minSpreadWidth"]!!.jsonPrimitive.int shouldBe 800
            options["allowScriptedContent"]!!.jsonPrimitive.boolean shouldBe true
            options["locationsKey"]!!.jsonPrimitive.content shouldBe "abc-123"
            options["fontsHref"]!!.jsonPrimitive.content shouldBe
                "https://appassets.androidplatform.net/reader/reader-fonts.css"
            options["cfi"]!!.jsonPrimitive.content shouldBe "epubcfi(/6/4!/4/2)"
        }

        it("TC-230: leaves out cfi and fontFamily when there is no start position or face override") {
            val (_, options) = parseBoot(ReaderScripts.boot("abc-123", ReaderSettings(), cfi = null))
            options.containsKey("cfi") shouldBe false
            options.containsKey("fontFamily") shouldBe false

            val (_, blank) = parseBoot(ReaderScripts.boot("abc-123", ReaderSettings(), cfi = " "))
            blank.containsKey("cfi") shouldBe false
        }

        it("TC-231: quotes a cfi that holds characters a script would misread") {
            val cfi = "epubcfi(/6/4[id\"x']!/4)"

            val (_, options) = parseBoot(ReaderScripts.boot("abc-123", ReaderSettings(), cfi))

            options["cfi"]!!.jsonPrimitive.content shouldBe cfi
        }
    }

    describe("settingsChange") {

        val base = ReaderSettings()

        it("TC-232: sends nothing when nothing differs") {
            ReaderScripts.settingsChange(base, base).shouldBeEmpty()
        }

        it("TC-233: sends one call per changed setting") {
            val wanted = base.copy(
                fontSize = 24,
                theme = ReaderTheme.Light,
                lineHeight = 2.0,
                maxWidth = "95%",
                justify = false,
                spread = "auto",
            )

            ReaderScripts.settingsChange(base, wanted).shouldContainExactly(
                "OmnibusReader.setFontSize(24)",
                "OmnibusReader.setTheme(\"light\")",
                "OmnibusReader.setLineHeight(2.0)",
                "OmnibusReader.setMargins(\"95%\")",
                "OmnibusReader.setJustify(false)",
                "OmnibusReader.setSpread(\"auto\")",
            )
        }

        it("TC-234: clears the face override with null and sets it with a quoted stack") {
            val serif = base.copy(fontFamily = "Georgia,serif")

            ReaderScripts.settingsChange(base, serif)
                .shouldContainExactly("OmnibusReader.setFont(\"Georgia,serif\")")
            ReaderScripts.settingsChange(serif, base).shouldContainExactly("OmnibusReader.setFont(null)")
        }
    }
})
