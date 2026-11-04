package com.omnibus.omnibus.data.audiobook.sleep

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapterIndexAt
import com.omnibus.omnibus.data.audiobook.playback.currentChapter

/**
 * Where an end-of-chapter sleep timer armed now would stop, in book seconds, or null when it can't be armed: nothing
 * loaded, loading, no chapters, or a current chapter of length 0. Capped at the book's duration when that is known.
 */
fun PlaybackState.sleepChapterEnd(): Double? {
    if (book == null || isLoading) return null
    val chapter = currentChapter ?: return null
    if (chapter.durationSeconds <= 0) return null
    val end = chapter.startSeconds + chapter.durationSeconds
    return if (durationSeconds > 0) minOf(end, durationSeconds) else end
}

/** The chapter number the sheet shows: the current chapter's 1-based position in the list. */
fun PlaybackState.sleepChapterNumber(): Int? = chapterIndexAt(chapters, positionSeconds)?.plus(1)
