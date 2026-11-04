package com.omnibus.omnibus.data.bookprogress.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET api/audiobooks/{uuid}/manifest?file_id=`, decoded flat. The `direct` mode carries a total, the [parts] and the
 * chapters; the `hls` mode carries only a [playlistUrl]. Every field has a default, so older bodies still decode.
 */
@Serializable
internal data class AudiobookManifestDto(
    val mode: String = "",
    @SerialName("book_file_id")
    val bookFileId: Long = 0,
    @SerialName("total_duration_seconds")
    val totalDurationSeconds: Double? = null,
    val chapters: List<ManifestChapterDto> = emptyList(),
    val parts: List<ManifestPartDto> = emptyList(),
    @SerialName("playlist_url")
    val playlistUrl: String? = null,
)

/** One audio part of a `direct` manifest. [url] is server-absolute, such as `/api/audiobooks/{uuid}/parts/1`. */
@Serializable
internal data class ManifestPartDto(
    val ordinal: Long = 0,
    val url: String = "",
    @SerialName("duration_seconds")
    val durationSeconds: Double = 0.0,
    val mime: String = "",
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
