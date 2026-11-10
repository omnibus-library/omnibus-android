package com.omnibus.omnibus.feature.reader.ui.curl

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull

/** What a neighbour in one direction is: another page, the start of another section, or nothing to turn to. */
internal enum class NeighbourKind {
    Page,
    Section,
    None;

    companion object {
        fun of(token: String?): NeighbourKind = when (token) {
            "page" -> Page
            "section" -> Section
            else -> None
        }
    }
}

/** What `OmnibusReader.whenSettled()` resolves to. [spine] is in CSS px of the WebView, or null. */
internal data class SettledPage(
    val columns: Int?,
    val spine: Float?,
    val next: NeighbourKind,
    val previous: NeighbourKind,
) {
    /** The neighbour in [dir]: forward for 1 and above, back otherwise. */
    fun kind(dir: Int): NeighbourKind = if (dir > 0) next else previous

    companion object {
        /** Reads the glue's reply, or null when it isn't an object. Never throws. */
        fun decode(value: JsonElement?): SettledPage? = runCatching {
            val obj = value as? JsonObject ?: return null
            SettledPage(
                columns = (obj["columns"] as? JsonPrimitive)?.intOrNull,
                spine = (obj["spine"] as? JsonPrimitive)?.floatOrNull,
                next = NeighbourKind.of((obj["next"] as? JsonPrimitive)?.content),
                previous = NeighbourKind.of((obj["prev"] as? JsonPrimitive)?.content),
            )
        }.getOrNull()
    }
}
