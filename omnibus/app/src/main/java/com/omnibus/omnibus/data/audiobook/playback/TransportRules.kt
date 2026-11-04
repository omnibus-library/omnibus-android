package com.omnibus.omnibus.data.audiobook.playback

/** How far a skip back goes, in seconds (P-PLAY-3). */
internal const val SKIP_BACK_SECONDS = 15.0

/** How far a skip forward goes, in seconds (P-PLAY-3). */
internal const val SKIP_FORWARD_SECONDS = 30.0

/** Past this many seconds into a chapter, previous chapter restarts it rather than going back (P-PLAY-4, I-TL-7). */
internal const val PREVIOUS_CHAPTER_RESTART_SECONDS = 3.0

/** The slowest playback speed (I-SPD-1). */
internal const val MIN_RATE = 0.5

/** The fastest playback speed (I-SPD-1). */
internal const val MAX_RATE = 3.0

/**
 * Clamps [seconds] to the book (I-TL-6): [0, duration], or only the lower bound while the duration is unknown (0 or
 * less), because a book with no known length can't be bounded above.
 */
internal fun clampToBook(seconds: Double, durationSeconds: Double): Double =
    if (durationSeconds > 0) seconds.coerceIn(0.0, durationSeconds) else maxOf(0.0, seconds)

/** The book position a skip of [deltaSeconds] from [positionSeconds] lands on (P-PLAY-3, I-TL-6). */
internal fun skipTarget(positionSeconds: Double, durationSeconds: Double, deltaSeconds: Double): Double =
    clampToBook(positionSeconds + deltaSeconds, durationSeconds)

/**
 * The book position previous chapter seeks to (P-PLAY-4, I-TL-7): the current chapter's start when more than 3 seconds
 * into it or in the first chapter, otherwise the previous chapter's start. Null when the book has no chapters.
 */
internal fun previousChapterTarget(chapters: List<Chapter>, positionSeconds: Double): Double? {
    val index = chapterIndexAt(chapters, positionSeconds) ?: return null
    val current = chapters[index]
    return if (positionSeconds - current.startSeconds > PREVIOUS_CHAPTER_RESTART_SECONDS || index == 0) {
        current.startSeconds
    } else {
        chapters[index - 1].startSeconds
    }
}

/** The book position next chapter seeks to (P-PLAY-5): the next chapter's start. Null on the last chapter or with none. */
internal fun nextChapterTarget(chapters: List<Chapter>, positionSeconds: Double): Double? {
    val index = chapterIndexAt(chapters, positionSeconds) ?: return null
    return chapters.getOrNull(index + 1)?.startSeconds
}

/** The speed to apply (I-SPD-1): clamped to [0.5, 3.0]. Null for a NaN or infinite rate, which is ignored. */
internal fun clampRate(rate: Double): Double? =
    if (rate.isFinite()) rate.coerceIn(MIN_RATE, MAX_RATE) else null
