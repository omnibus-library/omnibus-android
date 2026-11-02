package com.omnibus.omnibus.data.reader.local

import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * The stored form of [ReaderSettings], as one JSON object. The keys and tokens match iOS, and the
 * key names are part of the stored format, so keep them stable.
 *
 * Decoding is per field, so one unreadable value costs one setting and the rest keep their stored
 * values (the iOS #2117 failure, where one bad key reset everything). A new field must be additive
 * and defaulted, and its cases must be added to `ReaderSettingsJsonTest`. `spread` is not stored.
 */
internal object ReaderSettingsJson {

    private const val FONT_SIZE = "fontSize"

    /** Holds the typeface's token. The key keeps its iOS name. */
    private const val FONT_FAMILY = "fontFamily"
    private const val LINE_HEIGHT = "lineHeight"
    private const val MARGINS = "margins"
    private const val JUSTIFY = "justify"
    private const val THEME = "theme"

    private val JSON_LITERAL = Regex("""null|true|false|-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?""")

    /** The JSON object text for [settings]. */
    fun encode(settings: ReaderSettings): String = buildJsonObject {
        put(FONT_SIZE, settings.fontSize)
        put(FONT_FAMILY, settings.typeface.token)
        put(LINE_HEIGHT, settings.lineHeight)
        put(MARGINS, settings.margins.token)
        put(JUSTIFY, settings.justify)
        put(THEME, settings.theme.token)
    }.toString()

    /**
     * The settings in [raw]. An absent, non-JSON or non-object value gives every default; otherwise
     * each field is read on its own and falls back alone.
     */
    fun decode(raw: String?): ReaderSettings {
        val parsed = raw?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() }
        val obj = parsed as? JsonObject ?: return ReaderSettings()
        // The parser takes bare NaN and Infinity as literals, which are not JSON, so the whole blob is unusable.
        if (!parsed.isStrictJson()) return ReaderSettings()
        val defaults = ReaderSettings() // the one place defaults come from
        return ReaderSettings(
            theme = obj.token(THEME)?.let(ReaderTheme::fromToken) ?: defaults.theme,
            typeface = obj.token(FONT_FAMILY)?.let(ReaderTypeface::fromToken) ?: defaults.typeface,
            fontSize = obj.number(FONT_SIZE)?.roundToInt() ?: defaults.fontSize,
            lineHeight = obj.number(LINE_HEIGHT) ?: defaults.lineHeight,
            margins = obj.token(MARGINS)?.let(ReaderMargins::fromToken) ?: defaults.margins,
            justify = obj.bool(JUSTIFY) ?: defaults.justify,
        ).normalized()
    }

    /** Whether every unquoted literal in [this] is a JSON number, `true`, `false` or `null`. */
    private fun JsonElement.isStrictJson(): Boolean = when (this) {
        is JsonObject -> values.all { it.isStrictJson() }
        is JsonArray -> all { it.isStrictJson() }
        is JsonPrimitive -> isString || JSON_LITERAL.matches(content)
    }

    /** A JSON string, never a number or boolean dressed as one. */
    private fun JsonObject.token(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** A finite JSON number. Strings ("19"), booleans, null, NaN and Infinity (1e999) are rejected. */
    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }
            ?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.booleanOrNull
}
