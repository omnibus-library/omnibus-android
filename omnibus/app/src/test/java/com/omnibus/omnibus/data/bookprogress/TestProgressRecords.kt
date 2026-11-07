package com.omnibus.omnibus.data.bookprogress

import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.library.ResolvedPosition
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString

/** An epub progress record at [percent] (0 to 100, or null for a CFI-only record) with ordering clock [clock]. */
fun readingRecord(
    percent: Double?,
    clock: Long,
    cfi: String? = randomString(),
    resolved: ResolvedPosition? = null,
    updatedAt: Long = clock,
    clientUpdatedAt: Long? = clock,
    uuid: String = "book-1",
): ProgressRecord = ProgressRecord(
    bookUuid = uuid,
    format = ProgressFormat.Reading,
    epubCfi = cfi,
    audioPositionSeconds = null,
    progressPercent = percent,
    updatedAt = updatedAt,
    clientUpdatedAt = clientUpdatedAt,
    bookFileId = null,
    totalDurationSeconds = null,
    resolved = resolved,
)

/** An audio progress record at [positionSeconds] in its file, with ordering clock [clock]. */
fun listeningRecord(
    positionSeconds: Double?,
    clock: Long,
    totalSeconds: Double? = null,
    bookFileId: Long? = randomLong(),
    updatedAt: Long = clock,
    clientUpdatedAt: Long? = clock,
    uuid: String = "book-1",
): ProgressRecord = ProgressRecord(
    bookUuid = uuid,
    format = ProgressFormat.Listening,
    epubCfi = null,
    audioPositionSeconds = positionSeconds,
    progressPercent = null,
    updatedAt = updatedAt,
    clientUpdatedAt = clientUpdatedAt,
    bookFileId = bookFileId,
    totalDurationSeconds = totalSeconds,
    resolved = null,
)

/** Chapters that start at [starts], titled null, indexed by position. */
fun chapters(vararg starts: Float): List<BookChapter> =
    starts.mapIndexed { index, start -> BookChapter(index, title = null, startFraction = start) }
