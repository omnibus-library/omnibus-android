package com.omnibus.omnibus.data.audiobook.crossformat.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The `state` of a cross-format answer that carries a candidate (I-RES-7). Any other state is no candidate. */
internal const val CROSS_FORMAT_STATE_CANDIDATE = "candidate"

/**
 * `GET api/books/{uuid}/cross-format-resume?target=audio`. [state] stays a string, so a state the server adds later
 * decodes as no candidate instead of failing the whole answer.
 */
@Serializable
internal data class CrossFormatResumeDto(
    val state: String = "",
    val candidate: CrossFormatCandidateDto? = null,
    val follow: Boolean = false,
)

/** The mapped position in the other format. The field names are the server's wire format. */
@Serializable
internal data class CrossFormatCandidateDto(
    @SerialName("source_client_updated_at")
    val sourceClientUpdatedAt: Long = 0,
    @SerialName("book_file_id")
    val bookFileId: Long? = null,
    @SerialName("audio_position_seconds")
    val audioPositionSeconds: Double? = null,
    @SerialName("source_ahead")
    val sourceAhead: Boolean? = null,
)
