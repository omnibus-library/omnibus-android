package com.omnibus.omnibus.data.bookprogress.remote

import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One page of `GET api/stats/sessions`, newest first. [nextBefore] is an opaque cursor, echoed back as `before` for the
 * next page, and null on the last page.
 */
@Serializable
internal data class SessionLogPageDto(
    val entries: List<SessionLogEntryDto> = emptyList(),
    @SerialName("next_before")
    val nextBefore: String? = null,
)

/** One session. [format] stays a plain string, so a format the app does not know never fails the page. */
@Serializable
internal data class SessionLogEntryDto(
    @SerialName("book_uuid")
    val bookUuid: String = "",
    val format: String = "",
    @SerialName("started_at")
    val startedAt: Long = 0,
    @SerialName("ended_at")
    val endedAt: Long = 0,
    val seconds: Long = 0,
) {
    /** The session in the app's terms: start in milliseconds, no fractions, a negative duration counted as zero. */
    fun toDomain(): ReadingSession = ReadingSession(
        startedAtMillis = startedAt * 1000,
        durationSeconds = seconds.coerceAtLeast(0),
        startFraction = null,
        endFraction = null,
    )
}
