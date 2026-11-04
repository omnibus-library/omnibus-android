package com.omnibus.omnibus.data.audiobook.playback

import kotlin.math.roundToLong

/** Seconds as whole milliseconds, rounded. */
internal fun Double.toMillis(): Long = (this * 1000).roundToLong()

/**
 * The chapter span around a book position, in milliseconds (I-SYS-1). The media session sees only this span: its duration
 * and position are the span's, and seeks map back to book time. [index] is null when the book has no chapters, and the
 * span is the whole book.
 */
internal data class Span(val index: Int?, val startMs: Long, val lengthMs: Long) {

    /** A book position in this span, as span time, clamped to [0, [lengthMs]]. */
    fun toSpanMs(bookMs: Long): Long = (bookMs - startMs).coerceIn(0L, lengthMs)

    /** A span position as book time, clamped to the span. */
    fun toBookMs(spanMs: Long): Long = startMs + spanMs.coerceIn(0L, lengthMs)
}

/**
 * The span at [bookMs]. With no chapters it is the whole book, or null when the book's length is also unknown. Chapter
 * lookup agrees with [chapterIndexAt] (I-TL-2).
 */
internal fun spanAt(chapters: List<Chapter>, bookDurationMs: Long, bookMs: Long): Span? {
    val index = chapterIndexAt(chapters, bookMs / 1000.0)
        ?: return if (bookDurationMs > 0) Span(null, 0L, bookDurationMs) else null
    val chapter = chapters[index]
    return Span(index, chapter.startSeconds.toMillis(), chapter.durationSeconds.toMillis())
}
