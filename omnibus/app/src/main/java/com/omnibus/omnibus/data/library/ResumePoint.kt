package com.omnibus.omnibus.data.library

/** A book the user was reading or listening to recently, with where they stopped. */
data class ResumePoint(
    val record: ProgressRecord,
    val book: Book,
    /** Audio part the user stopped in, for multi-file audiobooks. */
    val audioPart: Int?,
    val audioPartCount: Int?,
    val playbackRate: Double?,
)

/** The saved position for one book in one format. */
data class ProgressRecord(
    val bookUuid: String,
    val format: ProgressFormat,
    val epubCfi: String?,
    val audioPositionSeconds: Double?,
    val progressPercent: Double?,
    /** Server timestamp of the last update. */
    val updatedAt: Long,
    /** Client timestamp of the last update, when the client sent one. */
    val clientUpdatedAt: Long?,
    val bookFileId: Long?,
    val totalDurationSeconds: Double?,
    /** The position in chapter terms, when the server could resolve it. */
    val resolved: ResolvedPosition?,
)

/** Which kind of progress a [ProgressRecord] tracks. */
enum class ProgressFormat {
    /** Reading an EPUB. */
    Reading,

    /** Listening to an audiobook. */
    Listening,
}

/** A progress position translated into chapter terms. */
data class ResolvedPosition(
    val chapterOrdinal: Int?,
    val chaptersTotal: Int?,
    val chapterTitle: String?,
    val percentThroughBook: Double?,
    val confidence: PositionConfidence,
)

/** How sure the server is that [ResolvedPosition] matches the saved position. */
enum class PositionConfidence {
    High,
    Low,
}
