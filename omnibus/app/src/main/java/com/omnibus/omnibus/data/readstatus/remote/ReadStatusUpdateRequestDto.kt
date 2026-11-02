package com.omnibus.omnibus.data.readstatus.remote

import com.omnibus.omnibus.data.readstatus.ReadStatus
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** The body of `PUT api/read-status`. [status] is the lowercase token, see [token]. */
@Serializable
data class ReadStatusUpdateRequestDto(
    @SerialName("book_uuid") val bookUuid: String,
    @SerialName("status") val status: String,
) {
    fun toJsonRequest(): RequestBody =
        ReadStatusJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** The server's token for this status: `unread`, `reading` or `finished`. */
internal fun ReadStatus.token(): String = name.lowercase(Locale.ROOT)
