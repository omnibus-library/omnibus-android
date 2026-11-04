package com.omnibus.omnibus.data.audiobook.rate.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * The record `GET api/audiobooks/{uuid}/playback-rate` answers with (I-SPD-1). The server answers the JSON literal
 * `null` when the listener never set a rate, which the API reads as unset. Responses are decoded with `LibraryJson`.
 */
@Serializable
internal data class PlaybackRateRecordDto(
    @SerialName("book_uuid") val bookUuid: String? = null,
    @SerialName("playback_rate") val playbackRate: Double,
    @SerialName("updated_at") val updatedAt: Long? = null,
)

/** The body of `PUT api/audiobooks/{uuid}/playback-rate`: exactly the one field (I-SPD-1). */
@Serializable
internal data class PlaybackRateUpdateDto(@SerialName("playback_rate") val playbackRate: Double) {

    /** The request body carrying this rate as JSON. */
    fun toJsonRequest(): RequestBody = PlaybackRateJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** JSON for this package's request bodies. Responses are decoded with `LibraryJson`, not this. */
internal val PlaybackRateJson = Json {
    encodeDefaults = true
}
