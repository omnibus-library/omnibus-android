package com.omnibus.omnibus.data.bookmarks.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The body of `POST api/bookmarks`: one flat object with snake_case names. [title] is omitted when null. [clientId] makes a
 * retried create idempotent on the server.
 */
@Serializable
internal data class BookmarkCreateRequestDto(
    @SerialName("book_uuid") val bookUuid: String,
    @SerialName("position") val position: String,
    @SerialName("title") val title: String? = null,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_created_at") val clientCreatedAt: Long,
) {
    /** This body as JSON, with the media type the server expects. */
    fun toJsonRequest(): RequestBody = BookmarksJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
