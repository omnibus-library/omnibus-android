package com.omnibus.omnibus.data.audiobook.position.remote

import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** The body of `POST api/progress` for an audio position (R13). `epub_cfi` is always null. */
@Serializable
internal data class AudioProgressUpdateRequestDto(
    @SerialName("book_uuid") val bookUuid: String,
    @SerialName("format") val format: String = "audio",
    @SerialName("epub_cfi") val epubCfi: String? = null,
    @SerialName("audio_position_seconds") val audioPositionSeconds: Double,
    @SerialName("client_updated_at") val clientUpdatedAt: Long,
    @SerialName("book_file_id") val bookFileId: Long,
) {
    fun toJsonRequest(): RequestBody =
        AudioProgressJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    companion object {
        /** The request body for saving [position]. */
        fun from(position: AudioPosition) = AudioProgressUpdateRequestDto(
            bookUuid = position.bookUuid,
            audioPositionSeconds = position.positionSeconds,
            clientUpdatedAt = position.clientUpdatedAt,
            bookFileId = position.bookFileId,
        )

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
