package com.omnibus.omnibus.data.reader.local

import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ReaderSettingsJsonTest : DescribeSpec({

    /** Every stored field set away from its default. */
    val changed = ReaderSettings(
        theme = ReaderTheme.Sepia,
        typeface = ReaderTypeface.Classic,
        fontSize = 24,
        lineHeight = 1.8,
        margins = ReaderMargins.Wide,
        justify = false,
    )

    /** A stored blob with every key set away from its default. */
    val changedBlob = """
        {"fontSize":24,"fontFamily":"classic","lineHeight":1.8,"margins":"wide","justify":false,"theme":"sepia"}
    """.trimIndent()

    fun objectOf(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    /** [raw] with [key] removed. */
    fun without(raw: String, key: String): String = JsonObject(objectOf(raw) - key).toString()

    /** [raw] with the value of [key] replaced by [value] (raw JSON). */
    fun replacing(raw: String, key: String, value: String): String =
        JsonObject(objectOf(raw) + (key to Json.parseToJsonElement(value))).toString()

    describe("encode and decode") {

        it("round-trips a value with every field changed") {
            ReaderSettingsJson.decode(ReaderSettingsJson.encode(changed)) shouldBe changed
        }

        it("round-trips the defaults") {
            ReaderSettingsJson.decode(ReaderSettingsJson.encode(ReaderSettings())) shouldBe ReaderSettings()
        }

        it("writes exactly the six iOS keys with the iOS tokens") {
            val encoded = objectOf(ReaderSettingsJson.encode(changed))

            encoded.keys shouldBe setOf(
                "fontSize",
                "fontFamily",
                "lineHeight",
                "margins",
                "justify",
                "theme",
            )
            encoded["fontFamily"]!!.jsonPrimitive.content shouldBe "classic"
            encoded["margins"]!!.jsonPrimitive.content shouldBe "wide"
            encoded["theme"]!!.jsonPrimitive.content shouldBe "sepia"
        }

        it("never writes spread, which is fixed") {
            ReaderSettingsJson.encode(ReaderSettings()).contains("spread") shouldBe false
        }
    }

    describe("decode") {

        it("reads a complete blob into the stored values") {
            ReaderSettingsJson.decode(changedBlob) shouldBe changed
        }

        it("falls back to the default for a missing key and keeps the other five") {
            val keys = listOf("fontSize", "fontFamily", "lineHeight", "margins", "justify", "theme")
            val defaults = ReaderSettings()
            val fieldDefault = mapOf(
                "fontSize" to changed.copy(fontSize = defaults.fontSize),
                "fontFamily" to changed.copy(typeface = defaults.typeface),
                "lineHeight" to changed.copy(lineHeight = defaults.lineHeight),
                "margins" to changed.copy(margins = defaults.margins),
                "justify" to changed.copy(justify = defaults.justify),
                "theme" to changed.copy(theme = defaults.theme),
            )

            keys.forEach { key ->
                ReaderSettingsJson.decode(without(changedBlob, key)) shouldBe fieldDefault.getValue(key)
            }
        }

        it("falls back to the default for a wrong JSON type and keeps the rest") {
            ReaderSettingsJson.decode(replacing(changedBlob, "fontSize", "\"big\"")) shouldBe
                changed.copy(fontSize = ReaderSettings().fontSize)
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "true")) shouldBe
                changed.copy(lineHeight = ReaderSettings().lineHeight)
            ReaderSettingsJson.decode(replacing(changedBlob, "justify", "\"yes\"")) shouldBe
                changed.copy(justify = ReaderSettings().justify)
            ReaderSettingsJson.decode(replacing(changedBlob, "margins", "5")) shouldBe
                changed.copy(margins = ReaderSettings().margins)
            ReaderSettingsJson.decode(replacing(changedBlob, "theme", "{}")) shouldBe
                changed.copy(theme = ReaderSettings().theme)
            ReaderSettingsJson.decode(replacing(changedBlob, "fontFamily", "[]")) shouldBe
                changed.copy(typeface = ReaderSettings().typeface)
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "null")) shouldBe
                changed.copy(lineHeight = ReaderSettings().lineHeight)
        }

        it("falls back to the default for an unknown enum token and keeps the rest") {
            ReaderSettingsJson.decode(replacing(changedBlob, "margins", "\"huge\"")) shouldBe
                changed.copy(margins = ReaderSettings().margins)
            ReaderSettingsJson.decode(replacing(changedBlob, "theme", "\"solarized\"")) shouldBe
                changed.copy(theme = ReaderSettings().theme)
            ReaderSettingsJson.decode(replacing(changedBlob, "fontFamily", "\"comic\"")) shouldBe
                changed.copy(typeface = ReaderSettings().typeface)
        }

        it("falls back for a non-finite or string-typed number and never clamps an infinity") {
            ReaderSettingsJson.decode(replacing(changedBlob, "fontSize", "\"NaN\"")) shouldBe
                changed.copy(fontSize = ReaderSettings().fontSize)
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "\"Infinity\"")) shouldBe
                changed.copy(lineHeight = ReaderSettings().lineHeight)
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "1e999")) shouldBe
                changed.copy(lineHeight = ReaderSettings().lineHeight)
            ReaderSettingsJson.decode(replacing(changedBlob, "fontSize", "1e999")) shouldBe
                changed.copy(fontSize = ReaderSettings().fontSize)
        }

        it("takes the all-defaults path for a bare NaN, which is not JSON") {
            val blob = """{"fontSize":24,"lineHeight":NaN}"""

            ReaderSettingsJson.decode(blob) shouldBe ReaderSettings()
        }

        it("clamps out-of-range font sizes and line heights into range") {
            ReaderSettingsJson.decode(replacing(changedBlob, "fontSize", "5")).fontSize shouldBe 12
            ReaderSettingsJson.decode(replacing(changedBlob, "fontSize", "500")).fontSize shouldBe 34
            ReaderSettingsJson.decode(replacing(changedBlob, "fontSize", "-3")).fontSize shouldBe 12
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "0.1")).lineHeight shouldBe 1.2
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "7")).lineHeight shouldBe 2.2
        }

        it("rounds a stored line height to one decimal") {
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "1.64")).lineHeight shouldBeExactly 1.6
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "1.66")).lineHeight shouldBeExactly 1.7
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "1.6000000000000001"))
                .lineHeight shouldBeExactly 1.6
            ReaderSettingsJson.decode(replacing(changedBlob, "lineHeight", "2")).lineHeight shouldBeExactly 2.0
        }

        it("rounds a fractional font size to a whole point") {
            ReaderSettingsJson.decode(replacing(changedBlob, "fontSize", "19.0")).fontSize shouldBe 19
            ReaderSettingsJson.decode(replacing(changedBlob, "fontSize", "19.4")).fontSize shouldBe 19
        }

        it("maps the legacy typeface tokens to their faces") {
            ReaderSettingsJson.decode(replacing(changedBlob, "fontFamily", "\"serif\"")).typeface shouldBe
                ReaderTypeface.Original
            ReaderSettingsJson.decode(replacing(changedBlob, "fontFamily", "\"sans-serif\"")).typeface shouldBe
                ReaderTypeface.Sans
            ReaderSettingsJson.decode(replacing(changedBlob, "fontFamily", "\"monospace\"")).typeface shouldBe
                ReaderTypeface.Mono
        }

        it("ignores unknown extra keys and keeps spread fixed") {
            val blob = replacing(changedBlob, "spread", "\"double\"").let {
                JsonObject(objectOf(it) + ("extra" to Json.parseToJsonElement("""{"a":1}"""))).toString()
            }

            ReaderSettingsJson.decode(blob) shouldBe changed
            ReaderSettingsJson.decode(blob).spread shouldBe "none"
        }

        it("gives every default for an absent, non-JSON or non-object blob") {
            listOf(null, "", "not json", "[1,2]", "\"str\"", "42", "{").forEach { raw ->
                ReaderSettingsJson.decode(raw) shouldBe ReaderSettings()
            }
        }

        it("gives every default for an empty object without an error") {
            ReaderSettingsJson.decode("{}") shouldBe ReaderSettings()
        }
    }
})
