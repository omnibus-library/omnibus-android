package com.omnibus.omnibus.data.bookprogress.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET api/audiobooks/{uuid}/manifest?file_id=`, decoded flat. The `file` mode carries a total and chapters; other modes
 * (such as `hls`) leave them out, and their extra keys are ignored.
 */
@Serializable
internal data class AudiobookManifestDto(
    val mode: String = "",
    @SerialName("book_file_id")
    val bookFileId: Long = 0,
    @SerialName("total_duration_seconds")
    val totalDurationSeconds: Double? = null,
    val chapters: List<ManifestChapterDto> = emptyList(),
)

/** One chapter of the manifest, starting at [startSeconds] into the file. */
@Serializable
internal data class ManifestChapterDto(
    val ordinal: Long = 0,
    val title: String = "",
    @SerialName("start_seconds")
    val startSeconds: Double = 0.0,
    @SerialName("duration_seconds")
    val durationSeconds: Double = 0.0,
)
