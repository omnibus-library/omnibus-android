package com.omnibus.omnibus.data.shelves.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** The body of `POST api/shelves/{id}/books`. */
@Serializable
internal data class AddBooksRequestDto(
    @SerialName("book_uuids")
    val bookUuids: List<String>,
) {
    fun toJsonRequest(): RequestBody =
        ShelvesJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
