package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.util.Locale
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
                typeface = ReaderTypeface.Classic,
                fontSize = 22,
                lineHeight = 1.8,
                margins = ReaderMargins.Wide,
                justify = false,
            )

            val (_, options) = parseBoot(ReaderScripts.boot("abc-123", settings, cfi = "epubcfi(/6/4!/4/2)"))

            options["theme"]!!.jsonPrimitive.content shouldBe "sepia"
            options["fontSize"]!!.jsonPrimitive.int shouldBe 22
            options["fontFamily"]!!.jsonPrimitive.content shouldBe "'EB Garamond','Noto Serif',serif"
            options["lineHeight"]!!.jsonPrimitive.double shouldBe 1.8
            options["maxWidth"]!!.jsonPrimitive.content shouldBe "65%"
            options["justify"]!!.jsonPrimitive.boolean shouldBe false
            options["spread"]!!.jsonPrimitive.content shouldBe "none"
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

        it("sends the default margins as a quoted CSS length") {
            val (_, options) = parseBoot(ReaderScripts.boot("abc-123", ReaderSettings(), cfi = null))

            options["maxWidth"]!!.jsonPrimitive.isString shouldBe true
            options["maxWidth"]!!.jsonPrimitive.content shouldBe "80%"
        }

        it("boots every margin as a JSON string, never a number") {
            ReaderMargins.entries.forEach { margins ->
                val script = ReaderScripts.boot("abc-123", ReaderSettings(margins = margins), cfi = null)
                val (_, options) = parseBoot(script)

                options["maxWidth"]!!.jsonPrimitive.isString shouldBe true
                options["maxWidth"]!!.jsonPrimitive.content shouldBe margins.css
                script.contains("\"maxWidth\":\"${margins.css}\"") shouldBe true
                script.contains("maxWidth:") shouldBe false
            }
        }

        it("sends the typeface stack as fontFamily for any face other than Original") {
            ReaderTypeface.entries.filter { it != ReaderTypeface.Original }.forEach { face ->
                val (_, options) = parseBoot(
                    ReaderScripts.boot("abc-123", ReaderSettings(typeface = face), cfi = null),
                )
                options["fontFamily"]!!.jsonPrimitive.content shouldBe face.css
            }
            val (_, original) = parseBoot(
                ReaderScripts.boot("abc-123", ReaderSettings(typeface = ReaderTypeface.Original), cfi = null),
            )
            original.containsKey("fontFamily") shouldBe false
        }
    }

    describe("settingsChange") {

        val base = ReaderSettings()

        it("TC-232: sends nothing when nothing differs") {
            ReaderScripts.settingsChange(base, base).shouldBeEmpty()
        }

        it("sends nothing for two equal settings built separately") {
            val a = ReaderSettings(fontSize = 22, lineHeight = 1.8, margins = ReaderMargins.Wide)
            val b = ReaderSettings(fontSize = 22, lineHeight = 1.8, margins = ReaderMargins.Wide)

            ReaderScripts.settingsChange(a, b).shouldBeEmpty()
        }

        it("TC-233: sends one call per changed setting") {
            val wanted = base.copy(
                fontSize = 24,
                theme = ReaderTheme.Light,
                lineHeight = 2.0,
                margins = ReaderMargins.Narrow,
                justify = false,
            )

            ReaderScripts.settingsChange(base, wanted).shouldContainExactly(
                "OmnibusReader.setFontSize(24)",
                "OmnibusReader.setTheme(\"light\")",
                "OmnibusReader.setLineHeight(2.0)",
                "OmnibusReader.setMargins(\"95%\")",
                "OmnibusReader.setJustify(false)",
            )
        }

        it("TC-234: clears the face override with null and sets it with a quoted stack") {
            val classic = base.copy(typeface = ReaderTypeface.Classic)

            ReaderScripts.settingsChange(base, classic)
                .shouldContainExactly("OmnibusReader.setFont(\"'EB Garamond','Noto Serif',serif\")")
            ReaderScripts.settingsChange(classic, base).shouldContainExactly("OmnibusReader.setFont(null)")
            ReaderScripts.settingsChange(classic, base.copy(typeface = ReaderTypeface.Mono))
                .shouldContainExactly("OmnibusReader.setFont(\"'Droid Sans Mono',monospace\")")
        }

        it("sends setMargins with the quoted CSS length for each of the three margins") {
            ReaderMargins.entries.forEach { margins ->
                // Start from a different margin, so every target is a real change.
                val shown = base.copy(margins = if (margins == ReaderMargins.Wide) ReaderMargins.Normal else ReaderMargins.Wide)
                ReaderScripts.settingsChange(shown, shown.copy(margins = margins))
                    .shouldContainExactly("OmnibusReader.setMargins(\"${margins.css}\")")
            }
        }

        it("sends only the changed setters, and line height as an exact one-decimal literal") {
            val stepped = base.withLineHeightStep(+1)

            ReaderScripts.settingsChange(base, stepped).shouldContainExactly("OmnibusReader.setLineHeight(1.7)")
            ReaderScripts.settingsChange(base, stepped).joinToString().contains("1.7000000000000002") shouldBe false
            ReaderScripts.settingsChange(
                base.copy(theme = ReaderTheme.Sepia, typeface = ReaderTypeface.Sans),
                base.copy(theme = ReaderTheme.Sepia, typeface = ReaderTypeface.Sans),
            ).shouldBeEmpty()
        }
    }

    describe("seek") {

        it("TC-92: seek script uses locale-independent numbers") {
            val previous = Locale.getDefault()
            try {
                Locale.setDefault(Locale.GERMANY)
                ReaderScripts.seek(0.57, 3) shouldBe "OmnibusReader.seek(0.57, 3)"
            } finally {
                Locale.setDefault(previous)
            }
        }
    }
})
