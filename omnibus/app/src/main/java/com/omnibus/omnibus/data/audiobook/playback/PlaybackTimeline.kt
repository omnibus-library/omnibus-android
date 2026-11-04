package com.omnibus.omnibus.data.audiobook.playback

/**
 * The chapter at [bookSeconds], as an index into [chapters], or null when there are none. A position before the first
 * start is chapter 0, and a start boundary belongs to the chapter that starts there (I-TL-2).
 */
fun chapterIndexAt(chapters: List<Chapter>, bookSeconds: Double): Int? {
    if (chapters.isEmpty()) return null
    val index = chapters.indexOfLast { bookSeconds >= it.startSeconds }
    return if (index >= 0) index else 0
}

/** The chapter at the current position, or null when there are none. */
val PlaybackState.currentChapter: Chapter?
    get() = chapterIndexAt(chapters, positionSeconds)?.let { chapters[it] }

/** Where the current chapter starts in book time, or 0 with no chapters. */
val PlaybackState.spanStartSeconds: Double
    get() = currentChapter?.startSeconds ?: 0.0

/** The current chapter's resolved length, or the whole book's length with no chapters. */
val PlaybackState.spanLengthSeconds: Double
    get() = currentChapter?.durationSeconds ?: durationSeconds

/** The position within the current span, clamped to [0, [spanLengthSeconds]] (I-TL-4). */
val PlaybackState.spanPositionSeconds: Double
    get() = (positionSeconds - spanStartSeconds).coerceIn(0.0, maxOf(0.0, spanLengthSeconds))

/** Seconds left in the current span, divided by [PlaybackState.rate] and never negative (I-TL-5). */
val PlaybackState.spanSecondsLeftAtRate: Double
    get() = maxOf(0.0, spanLengthSeconds - spanPositionSeconds) / safeRate

/** Seconds left in the book, divided by [PlaybackState.rate] and never negative (I-TL-5). */
val PlaybackState.bookSecondsLeftAtRate: Double
    get() = maxOf(0.0, durationSeconds - positionSeconds) / safeRate

/** True when there are chapters and the position is in the last one. */
val PlaybackState.isLastChapter: Boolean
    get() = chapters.isNotEmpty() && chapterIndexAt(chapters, positionSeconds) == chapters.lastIndex

/** True when the book has chapters. */
val PlaybackState.hasChapters: Boolean
    get() = chapters.isNotEmpty()

private val PlaybackState.safeRate: Double
    get() = if (rate > 0) rate else 1.0

/**
 * Sorts [chapters] by start (I-TL-2). A chapter with a 0 length gets the gap to the next start, or to the book's end for
 * the last one, floored at 0 (I-TL-3).
 */
fun resolveChapters(chapters: List<Chapter>, bookDurationSeconds: Double): List<Chapter> {
    val sorted = chapters.sortedBy { it.startSeconds }
    return sorted.mapIndexed { index, chapter ->
        if (chapter.durationSeconds > 0) {
            chapter
        } else {
            val end = sorted.getOrNull(index + 1)?.startSeconds ?: bookDurationSeconds
            chapter.copy(durationSeconds = maxOf(0.0, end - chapter.startSeconds))
        }
    }
}
