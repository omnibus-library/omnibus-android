package com.omnibus.omnibus.data.reader.local

import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderPageTurn
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import com.omnibus.omnibus.data.reader.randomReaderSettings
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

class ReaderSettingsJsonTest : DescribeSpec({

    /** Settings as they can come back from storage, where spread is not kept. */
    fun randomStoredSettings(): ReaderSettings = randomReaderSettings().copy(spread = ReaderSettings().spread)

    /** The stored form of [settings] with the value of [key] replaced by raw JSON, or removed when [value] is null. */
    fun blobWith(settings: ReaderSettings, key: String, value: String?): String {
        val fields = Json.parseToJsonElement(ReaderSettingsJson.encode(settings)).jsonObject
        return JsonObject(if (value == null) fields - key else fields + (key to Json.parseToJsonElement(value))).toString()
    }

    /** [settings] with the field stored under [key] back at its default. */
    fun defaulted(settings: ReaderSettings, key: String): ReaderSettings {
        val defaults = ReaderSettings()
        return when (key) {
            "fontSize" -> settings.copy(fontSize = defaults.fontSize)
            "fontFamily" -> settings.copy(typeface = defaults.typeface)
            "lineHeight" -> settings.copy(lineHeight = defaults.lineHeight)
            "margins" -> settings.copy(margins = defaults.margins)
            "justify" -> settings.copy(justify = defaults.justify)
            "theme" -> settings.copy(theme = defaults.theme)
            "pageTurn" -> settings.copy(pageTurn = defaults.pageTurn)
            else -> error("Not a stored key: $key")
        }
    }

    describe("storing settings") {

        it("reads back the same settings") {
            val settings = randomStoredSettings()

            ReaderSettingsJson.decode(ReaderSettingsJson.encode(settings)) shouldBe settings
        }

        it("writes exactly the keys iOS uses") {
            val encoded = Json.parseToJsonElement(ReaderSettingsJson.encode(randomReaderSettings())).jsonObject

            encoded.keys shouldBe
                setOf("fontSize", "fontFamily", "lineHeight", "margins", "justify", "theme", "pageTurn")
        }

        it("TC-301: reads back every page turn regardless of the mode") {
            checkAll(Exhaustive.enum<ReaderPageTurn>()) { mode ->
                val settings = randomStoredSettings().copy(pageTurn = mode)

                ReaderSettingsJson.decode(ReaderSettingsJson.encode(settings)) shouldBe settings
            }
        }

        it("TC-301: stores every page turn under its token") {
            checkAll(Exhaustive.enum<ReaderPageTurn>()) { mode ->
                val settings = randomReaderSettings().copy(pageTurn = mode)
                val encoded = Json.parseToJsonElement(ReaderSettingsJson.encode(settings)).jsonObject

                encoded["pageTurn"] shouldBe JsonPrimitive(mode.token)
            }
        }
    }

    describe("reading stored settings") {

        it("understands every token iOS writes regardless of the key") {
            checkAll(
                Exhaustive.of<Pair<String, (ReaderSettings) -> Boolean>>(
                    """{"theme":"light"}""" to { it.theme == ReaderTheme.Light },
                    """{"theme":"sepia"}""" to { it.theme == ReaderTheme.Sepia },
                    """{"theme":"dark"}""" to { it.theme == ReaderTheme.Dark },
                    """{"theme":"black"}""" to { it.theme == ReaderTheme.Black },
                    """{"margins":"narrow"}""" to { it.margins == ReaderMargins.Narrow },
                    """{"margins":"normal"}""" to { it.margins == ReaderMargins.Normal },
                    """{"margins":"wide"}""" to { it.margins == ReaderMargins.Wide },
                    """{"fontFamily":"original"}""" to { it.typeface == ReaderTypeface.Original },
                    """{"fontFamily":"editorial"}""" to { it.typeface == ReaderTypeface.Editorial },
                    """{"fontFamily":"classic"}""" to { it.typeface == ReaderTypeface.Classic },
                    """{"fontFamily":"modern"}""" to { it.typeface == ReaderTypeface.Modern },
                    """{"fontFamily":"sans"}""" to { it.typeface == ReaderTypeface.Sans },
                    """{"fontFamily":"mono"}""" to { it.typeface == ReaderTypeface.Mono },
                    """{"pageTurn":"slide"}""" to { it.pageTurn == ReaderPageTurn.Slide },
                    """{"pageTurn":"curl"}""" to { it.pageTurn == ReaderPageTurn.Curl },
                    """{"pageTurn":"none"}""" to { it.pageTurn == ReaderPageTurn.None },
                ),
            ) { (blob, holds) ->
                withClue(blob) { holds(ReaderSettingsJson.decode(blob)) shouldBe true }
            }
        }

        it("falls back for that setting alone regardless of which key is missing") {
            val settings = randomStoredSettings()

            checkAll(
                Exhaustive.of("fontSize", "fontFamily", "lineHeight", "margins", "justify", "theme", "pageTurn"),
            ) { key ->
                ReaderSettingsJson.decode(blobWith(settings, key, value = null)) shouldBe defaulted(settings, key)
            }
        }

        it("falls back for that setting alone regardless of which key holds which unreadable value") {
            val settings = randomStoredSettings()

            checkAll(
                Exhaustive.of(
                    "fontSize" to "\"big\"",
                    "fontSize" to "\"NaN\"",
                    "fontSize" to "1e999",
                    "lineHeight" to "true",
                    "lineHeight" to "null",
                    "lineHeight" to "\"Infinity\"",
                    "lineHeight" to "1e999",
                    "justify" to "\"yes\"",
                    "margins" to "5",
                    "theme" to "{}",
                    "fontFamily" to "[]",
                    "margins" to "\"huge\"",
                    "theme" to "\"solarized\"",
                    "fontFamily" to "\"comic\"",
                    "pageTurn" to "\"flip\"",
                    "pageTurn" to "3",
                    "pageTurn" to "null",
                    "pageTurn" to "{}",
                ),
            ) { (key, value) ->
                ReaderSettingsJson.decode(blobWith(settings, key, value)) shouldBe defaulted(settings, key)
            }
        }

        it("clamps a number regardless of the key or which side it is out on") {
            val sizes = ReaderSettings.FONT_SIZE_RANGE
            val tenths = ReaderSettings.LINE_HEIGHT_TENTHS

            checkAll(
                Exhaustive.of(
                    "-3" to sizes.first,
                    "5" to sizes.first,
                    "500" to sizes.last,
                ),
            ) { (raw, clamped) ->
                ReaderSettingsJson.decode("""{"fontSize":$raw}""").fontSize shouldBe clamped
            }
            checkAll(Exhaustive.of("0.1" to tenths.first / 10.0, "7" to tenths.last / 10.0)) { (raw, clamped) ->
                ReaderSettingsJson.decode("""{"lineHeight":$raw}""").lineHeight shouldBe clamped
            }
        }

        it("rounds a line height to one decimal regardless of how it was written") {
            checkAll(
                Exhaustive.of(
                    "1.64" to 1.6,
                    "1.66" to 1.7,
                    "1.6000000000000001" to 1.6,
                    "2" to 2.0,
                ),
            ) { (raw, rounded) ->
                ReaderSettingsJson.decode("""{"lineHeight":$raw}""").lineHeight shouldBe rounded
            }
        }

        it("rounds a fractional font size to a whole point") {
            ReaderSettingsJson.decode("""{"fontSize":19.4}""").fontSize shouldBe 19
        }

        context("when the blob has keys this app does not store") {

            it("ignores them regardless of the key") {
                val settings = randomStoredSettings()

                checkAll(Exhaustive.of("spread" to "\"auto\"", "extra" to """{"a":1}""")) { (key, value) ->
                    ReaderSettingsJson.decode(blobWith(settings, key, value)) shouldBe settings
                }
            }
        }

        it("gives every default regardless of why the blob is unusable") {
            checkAll(
                Exhaustive.of(
                    null,
                    "",
                    "not json",
                    "[1,2]",
                    "\"str\"",
                    "42",
                    "{",
                    "{}",
                    // The parser takes a bare NaN, but it is not JSON, so the whole blob is unusable.
                    """{"fontSize":24,"lineHeight":NaN}""",
                ),
            ) { raw ->
                ReaderSettingsJson.decode(raw) shouldBe ReaderSettings()
            }
        }
    }
})
