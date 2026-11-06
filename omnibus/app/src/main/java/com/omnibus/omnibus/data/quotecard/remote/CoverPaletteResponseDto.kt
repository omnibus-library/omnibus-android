package com.omnibus.omnibus.data.quotecard.remote

import com.omnibus.omnibus.data.quotecard.CoverPair
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement

/** Lenient JSON for the cover palette: unknown keys are ignored, and every field has a default. */
internal val QuoteCardJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * The body of `GET api/books/{uuid}/cover/palette`: up to four gradient pairs, darkest first. Pairs stay raw JSON until
 * [toDomain], so one malformed entry costs only itself.
 */
@Serializable
internal data class CoverPaletteResponseDto(val pairs: List<JsonElement> = emptyList()) {

    /** The pairs that are well formed, in server order. */
    fun toDomain(): List<CoverPair> = pairs.mapNotNull { element ->
        runCatching { QuoteCardJson.decodeFromJsonElement<CoverPairDto>(element).toDomain() }.getOrNull()
    }

    companion object {
        /** Decodes [json], throwing a `SerializationException` when it isn't a palette body. */
        fun fromJsonString(json: String): CoverPaletteResponseDto = QuoteCardJson.decodeFromString(json)
    }
}

/** One pair as the server writes it: `#rrggbb` stops and `ink` of `light` or `dark`. */
@Serializable
internal data class CoverPairDto(val from: String = "", val to: String = "", val ink: String = "") {

    /** This pair, or null when a stop isn't `#` and six hex digits or the ink isn't `light` or `dark`. */
    fun toDomain(): CoverPair? {
        val lightInk = when (ink) {
            "light" -> true
            "dark" -> false
            else -> return null
        }
        return CoverPair(opaqueArgb(from) ?: return null, opaqueArgb(to) ?: return null, lightInk)
    }
}

private val HexColor = Regex("#[0-9a-fA-F]{6}")

private fun opaqueArgb(hex: String): Int? =
    if (HexColor.matches(hex)) (0xFF000000L or hex.substring(1).toLong(16)).toInt() else null
