package com.omnibus.omnibus.feature.reader.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * One message from the reader page: the `{type, payload}` envelope `reader.html` posts for each
 * glue callback. [payload] is the callback's argument as text (the glue passes JSON as a string),
 * or null when it had none.
 */
data class ReaderMessage(val type: String, val payload: String?) {
    companion object {
        /** Parses a posted envelope, or returns null for anything that isn't one. */
        fun parse(text: String): ReaderMessage? {
            val envelope = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
            val type = (envelope["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val payload = when (val element = envelope["payload"]) {
                null, JsonNull -> null
                is JsonPrimitive -> element.content
                else -> element.toString()
            }
            return ReaderMessage(type, payload)
        }
    }
}

/** The part of a `relocate` payload the host itself needs: where the reader is, and whether it moved. */
internal data class ReaderPosition(val cfi: String?, val echo: Boolean) {
    companion object {
        fun parse(payload: String?): ReaderPosition? {
            val json: JsonObject = runCatching { Json.parseToJsonElement(payload ?: return null).jsonObject }
                .getOrNull() ?: return null
            val cfi = (json["cfi"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            val echo = (json["echo"] as? JsonPrimitive)?.booleanOrNull ?: false
            return ReaderPosition(cfi, echo)
        }
    }
}
