package com.omnibus.omnibus.data.readstatus.remote

import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A `ReadStatusRecord` from `GET` or `PUT api/read-status`. The server's times are Unix seconds. Its
 * `updated_at_iso` is not read.
 */
@Serializable
data class ReadStatusRecordDto(
    @SerialName("book_uuid") val bookUuid: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
    @SerialName("finished_at") val finishedAt: Long? = null,
) {
    /** This record as a domain record, with times in milliseconds. */
    fun toDomain(): ReadStatusRecord = ReadStatusRecord(
        status = statusFromToken(status),
        updatedAtMillis = updatedAt?.times(1_000L),
        finishedAtMillis = finishedAt?.times(1_000L),
    )
}

/** The status for a server token. Matching ignores case, and an unknown or missing token reads as Unread, as the server does. */
internal fun statusFromToken(token: String?): ReadStatus =
    ReadStatus.entries.firstOrNull { it.name.equals(token?.trim(), ignoreCase = true) } ?: ReadStatus.Unread
