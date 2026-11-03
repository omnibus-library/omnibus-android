package com.omnibus.omnibus.data.bookprogress.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET api/books/{uuid}/alignment`: the book's measured EPUB chapters and audio files. */
@Serializable
internal data class AlignmentDto(
    val ebook: AlignmentEbookDto? = null,
    @SerialName("audio_files")
    val audioFiles: List<AlignmentAudioFileDto> = emptyList(),
    val listening: AlignmentListeningDto? = null,
)

@Serializable
internal data class AlignmentEbookDto(
    val chapters: List<AlignmentEbookChapterDto> = emptyList(),
)

/** One EPUB chapter, where it starts as a percentage of the book. */
@Serializable
internal data class AlignmentEbookChapterDto(
    val title: String = "",
    val percent: Double = 0.0,
)

/** One audio file of the book, with its length and chapter starts in seconds. */
@Serializable
internal data class AlignmentAudioFileDto(
    @SerialName("book_file_id")
    val bookFileId: Long = 0,
    @SerialName("duration_seconds")
    val durationSeconds: Double = 0.0,
    @SerialName("chapter_starts")
    val chapterStarts: List<Double> = emptyList(),
)

/** The audio file the reader last used, when the server knows one. */
@Serializable
internal data class AlignmentListeningDto(
    @SerialName("book_file_id")
    val bookFileId: Long? = null,
)
