package com.omnibus.omnibus.data.progress.remote

import com.omnibus.omnibus.data.progress.ServerReadingPosition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The body of `GET api/progress/{uuid}`: the book's records, one per format. */
@Serializable
data class BookProgressResponseDto(
    val records: List<ProgressRecordResponseDto> = emptyList(),
)

/**
 * One record in [BookProgressResponseDto]. Fields this app doesn't read are ignored by [ProgressJson].
 */
@Serializable
data class ProgressRecordResponseDto(
    @SerialName("format") val format: String = "",
    @SerialName("epub_cfi") val epubCfi: String? = null,
    @SerialName("progress_percent") val progressPercent: Int? = null,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("client_updated_at") val clientUpdatedAt: Long? = null,
    @SerialName("derived_epub_cfi") val derivedEpubCfi: String? = null,
) {
    /** This record as a domain position. */
    fun toDomain(): ServerReadingPosition =
        ServerReadingPosition(
            cfi = epubCfi,
            percent = progressPercent,
            derivedCfi = derivedEpubCfi,
            updatedAt = updatedAt,
            clientUpdatedAt = clientUpdatedAt,
        )
}
