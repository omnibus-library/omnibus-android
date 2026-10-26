package com.omnibus.omnibus.data.library

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** User-facing failure text for library reads. The wording matches the iOS client. */
internal object LibraryErrorMessages {

    const val UNREACHABLE = "Could not connect to the server."
    const val UNEXPECTED = "Unexpected response from the server."
    const val SESSION_EXPIRED = "Your session expired. Sign in again."

    private const val MAX_BODY_LENGTH = 300
    private val SERVER_MESSAGE_KEYS = listOf("error", "message", "detail")

    /**
     * The message for a non-2xx response. Prefers the server's own `error`, `message` or `detail`
     * text, then the plain-text body, then a generic message with the status code.
     */
    fun forHttp(code: Int, body: String): String {
        if (code == 401) return SESSION_EXPIRED
        serverMessage(body)?.let { return it }
        return body.trim().take(MAX_BODY_LENGTH).takeIf { it.isNotBlank() }
            ?: "Server error ($code)."
    }

    private fun serverMessage(body: String): String? {
        val json = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (ex: SerializationException) {
            null
        } ?: return null
        return SERVER_MESSAGE_KEYS.firstNotNullOfOrNull { key ->
            (json[key] as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
        }
    }
}
