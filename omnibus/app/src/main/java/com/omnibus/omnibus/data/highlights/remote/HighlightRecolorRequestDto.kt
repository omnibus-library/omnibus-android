package com.omnibus.omnibus.data.highlights.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** The body of `PATCH api/highlights/{id}/color`: the colour's lowercase token. */
@Serializable
internal data class HighlightRecolorRequestDto(
    @SerialName("color") val color: String,
) {
    /** This body as JSON, with the media type the server expects. */
    fun toJsonRequest(): RequestBody = HighlightsJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
