package com.omnibus.omnibus.data.highlights.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The body of `POST api/highlights`: one flat object with snake_case names. [text] is omitted when null. [clientId] makes a
 * retried create idempotent on the server.
 */
@Serializable
internal data class HighlightCreateRequestDto(
    @SerialName("book_uuid") val bookUuid: String,
    @SerialName("epub_cfi_range") val epubCfiRange: String,
    @SerialName("color") val color: String,
    @SerialName("text") val text: String? = null,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_created_at") val clientCreatedAt: Long,
) {
    /** This body as JSON, with the media type the server expects. */
    fun toJsonRequest(): RequestBody = HighlightsJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
