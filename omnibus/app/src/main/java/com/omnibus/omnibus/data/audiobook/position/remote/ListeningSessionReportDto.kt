package com.omnibus.omnibus.data.audiobook.position.remote

import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/** One element of the `POST api/progress/sessions` array (I-SESS-1). Times are unix seconds. */
@Serializable
internal data class ListeningSessionReportDto(
    @SerialName("book_uuid") val bookUuid: String,
    @SerialName("format") val format: String = "audio",
    @SerialName("started_at") val startedAt: Long,
    @SerialName("ended_at") val endedAt: Long,
    @SerialName("progress_units") val progressUnits: Long,
    @SerialName("client_id") val clientId: String,
    @SerialName("utc_offset_minutes") val utcOffsetMinutes: Int,
    @SerialName("time_zone") val timeZone: String,
) {
    companion object {
        fun from(report: ListeningSessionReport) = ListeningSessionReportDto(
            bookUuid = report.bookUuid,
            startedAt = report.startedAt,
            endedAt = report.endedAt,
            progressUnits = report.progressUnits,
            clientId = report.clientId,
            utcOffsetMinutes = report.utcOffsetMinutes,
            timeZone = report.timeZone,
        )
    }
}

/** The JSON array body of `POST api/progress/sessions`. */
internal fun List<ListeningSessionReportDto>.toJsonRequest(): RequestBody =
    AudioProgressJson.encodeToString(this).toRequestBody(JSON_MEDIA_TYPE)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
