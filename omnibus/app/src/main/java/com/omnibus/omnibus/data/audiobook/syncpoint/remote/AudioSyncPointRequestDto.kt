package com.omnibus.omnibus.data.audiobook.syncpoint.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The body of `POST api/books/{uuid}/sync-point` for an audio sync point (I-BMK-3). `ebook_fraction` is always null:
 * the audio side is the only one this client declares.
 */
@Serializable
internal data class AudioSyncPointRequestDto(
    @SerialName("book_uuid") val bookUuid: String,
    @SerialName("format") val format: String = "audio",
    @SerialName("ebook_fraction") val ebookFraction: Double? = null,
    @SerialName("audio_book_file_id") val audioBookFileId: Long,
    @SerialName("audio_seconds") val audioSeconds: Double,
) {
    fun toJsonRequest(): RequestBody = SyncPointJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * JSON for the sync point body. Defaults and explicit nulls are written, so `"format":"audio"` and
 * `"ebook_fraction":null` always appear in the POST body. Kept apart from the progress JSON, which belongs to another package.
 */
internal val SyncPointJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}
