package com.omnibus.omnibus.data.progress.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The body of `POST api/progress`. Null optional fields are omitted from the JSON. The fields have
 * no defaults, so every one is encoded when set.
 */
@Serializable
data class ProgressUpdateRequestDto(
    @SerialName("book_uuid") val bookUuid: String,
    @SerialName("format") val format: String,
    @SerialName("epub_cfi") val epubCfi: String?,
    @SerialName("progress_percent") val progressPercent: Int?,
    @SerialName("client_updated_at") val clientUpdatedAt: Long?,
) {
    fun toJsonRequest(): RequestBody =
        ProgressJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
