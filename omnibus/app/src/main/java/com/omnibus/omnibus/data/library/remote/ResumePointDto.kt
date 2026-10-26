package com.omnibus.omnibus.data.library.remote

import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.library.ResolvedPosition
import com.omnibus.omnibus.data.library.ResumePoint
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ResumePointDto(
    val record: ProgressRecordDto,
    val book: BookDto,
    @SerialName("audio_part")
    val audioPart: Int? = null,
    @SerialName("audio_part_count")
    val audioPartCount: Int? = null,
    @SerialName("playback_rate")
    val playbackRate: Double? = null,
) {
    /** Null for a record whose format is neither `epub` nor `audio`, which the repository drops. */
    fun toDomain(): ResumePoint? {
        val progress = record.toDomain() ?: return null
        return ResumePoint(
            record = progress,
            book = book.toDomain(),
            audioPart = audioPart,
            audioPartCount = audioPartCount,
            playbackRate = playbackRate,
        )
    }
}

@Serializable
internal data class ProgressRecordDto(
    @SerialName("book_uuid")
    val bookUuid: String,
    val format: String,
    @SerialName("epub_cfi")
    val epubCfi: String? = null,
    @SerialName("audio_position_seconds")
    val audioPositionSeconds: Double? = null,
    @SerialName("progress_percent")
    val progressPercent: Double? = null,
    @SerialName("updated_at")
    val updatedAt: Long = 0,
    @SerialName("client_updated_at")
    val clientUpdatedAt: Long? = null,
    @SerialName("book_file_id")
    val bookFileId: Long? = null,
    @SerialName("total_duration_seconds")
    val totalDurationSeconds: Double? = null,
    val resolved: ResolvedPositionDto? = null,
) {
    /** Null when [format] is not a known progress format. */
    fun toDomain(): ProgressRecord? {
        val progressFormat = when (format) {
            "epub" -> ProgressFormat.Reading
            "audio" -> ProgressFormat.Listening
            else -> return null
        }
        return ProgressRecord(
            bookUuid = bookUuid,
            format = progressFormat,
            epubCfi = epubCfi,
            audioPositionSeconds = audioPositionSeconds,
            progressPercent = progressPercent,
            updatedAt = updatedAt,
            clientUpdatedAt = clientUpdatedAt,
            bookFileId = bookFileId,
            totalDurationSeconds = totalDurationSeconds,
            resolved = resolved?.toDomain(),
        )
    }
}

@Serializable
internal data class ResolvedPositionDto(
    @SerialName("chapter_ordinal")
    val chapterOrdinal: Int? = null,
    @SerialName("chapters_total")
    val chaptersTotal: Int? = null,
    @SerialName("chapter_title")
    val chapterTitle: String? = null,
    @SerialName("percent_through_book")
    val percentThroughBook: Double? = null,
    val confidence: String = "low",
) {
    fun toDomain(): ResolvedPosition = ResolvedPosition(
        chapterOrdinal = chapterOrdinal,
        chaptersTotal = chaptersTotal,
        chapterTitle = chapterTitle,
        percentThroughBook = percentThroughBook,
        confidence = if (confidence == "high") PositionConfidence.High else PositionConfidence.Low,
    )
}
