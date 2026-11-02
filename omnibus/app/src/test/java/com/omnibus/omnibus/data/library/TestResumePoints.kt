package com.omnibus.omnibus.data.library

/**
 * A [ResumePoint] for tests, with the record's uuid taken from [book]. Every field is empty unless
 * a test sets it.
 */
fun testResumePoint(
    book: Book = testBook(1),
    format: ProgressFormat = ProgressFormat.Reading,
    progressPercent: Double? = null,
    epubCfi: String? = null,
    audioPositionSeconds: Double? = null,
    totalDurationSeconds: Double? = null,
    updatedAt: Long = 0,
    bookFileId: Long? = null,
    resolved: ResolvedPosition? = null,
    audioPart: Int? = null,
    audioPartCount: Int? = null,
    playbackRate: Double? = null,
): ResumePoint = ResumePoint(
    record = ProgressRecord(
        bookUuid = book.uuid,
        format = format,
        epubCfi = epubCfi,
        audioPositionSeconds = audioPositionSeconds,
        progressPercent = progressPercent,
        updatedAt = updatedAt,
        clientUpdatedAt = null,
        bookFileId = bookFileId,
        totalDurationSeconds = totalDurationSeconds,
        resolved = resolved,
    ),
    book = book,
    audioPart = audioPart,
    audioPartCount = audioPartCount,
    playbackRate = playbackRate,
)
