package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ProgressRecord

/**
 * Where the reader is in one book, taken from the newest progress record.
 *
 * @property fraction The place in the book, 0 to 1, or null when it cannot be measured (an EPUB record with no percent,
 *   or audio with no known total).
 * @property chapterNumber The 1-based chapter of an EPUB position, or null for audio or when no chapter is known.
 * @property latestMedium The format of the newest record.
 * @property audioFileId The audio file of the Listening record, or null when there is none.
 * @property audioElapsedSeconds The Listening record's position in its file, in seconds.
 * @property audioTotalSeconds The audio length in seconds: the record's total, else the chapters' total.
 */
data class BookReadingProgress(
    val fraction: Float?,
    val chapterNumber: Int?,
    val latestMedium: ProgressFormat?,
    val audioFileId: Long?,
    val audioElapsedSeconds: Long?,
    val audioTotalSeconds: Long?,
)

/**
 * A chapter's place in the book.
 *
 * @property index The chapter's 0-based position in the server's list. The number shown is `index + 1`.
 * @property title The chapter's title, or null when it has none.
 * @property startFraction Where the chapter starts, 0 to 1.
 */
data class BookChapter(val index: Int, val title: String?, val startFraction: Float)

/**
 * The chapter that contains [fraction]: the last chapter, in start order, that starts at or before it. Equal starts keep
 * list order, so the later one wins. Null for an empty list, a NaN [fraction], or a [fraction] before the first start.
 */
fun List<BookChapter>.chapterAt(fraction: Float): BookChapter? {
    if (isEmpty() || fraction.isNaN()) return null
    return sortedBy { it.startFraction }.lastOrNull { it.startFraction <= fraction + CHAPTER_EPSILON }
}

/**
 * The reading progress for a book from its [records], or null when there are none. The newest record speaks for the
 * book, by its ordering clock (`clientUpdatedAt`, else `updatedAt`); a tie goes to Reading. Audio fields come from the
 * Listening record whenever there is one, even when Reading is newer.
 *
 * @param chapters The book's chapters, or null when they are not loaded. Used for the chapter number and the audio
 *   total when the record has none.
 */
fun bookReadingProgress(records: List<ProgressRecord>, chapters: BookChaptersResult.Success?): BookReadingProgress? {
    val winner = records.maxWithOrNull(
        compareBy<ProgressRecord> { it.clientUpdatedAt ?: it.updatedAt }
            .thenBy { if (it.format == ProgressFormat.Reading) 1 else 0 },
    ) ?: return null
    val listening = records.firstOrNull { it.format == ProgressFormat.Listening }
    val audioTotal = listening?.totalDurationSeconds?.takeIf { it > 0 }
        ?: chapters?.audioTotalSeconds?.takeIf { it > 0 }?.toDouble()
    val fraction: Float? = when (winner.format) {
        ProgressFormat.Reading -> winner.progressPercent?.takeIf { !it.isNaN() }?.let { (it / 100).coerceIn(0.0, 1.0).toFloat() }
        ProgressFormat.Listening -> {
            val position = winner.audioPositionSeconds
            if (position == null || position.isNaN() || audioTotal == null) null
            else (position / audioTotal).coerceIn(0.0, 1.0).toFloat()
        }
    }
    val chapterNumber = if (winner.format != ProgressFormat.Reading) null else
        winner.resolved?.chapterOrdinal?.takeIf { it > 0 }
            ?: fraction?.let { f -> chapters?.ebookChapters?.chapterAt(f)?.let { it.index + 1 } }
    return BookReadingProgress(
        fraction = fraction,
        chapterNumber = chapterNumber,
        latestMedium = winner.format,
        audioFileId = listening?.bookFileId,
        audioElapsedSeconds = listening?.audioPositionSeconds?.toLong(),
        audioTotalSeconds = audioTotal?.toLong(),
    )
}

/** Tolerance for a chapter start that sits a float's rounding error above the fraction. */
private const val CHAPTER_EPSILON = 1e-4f
