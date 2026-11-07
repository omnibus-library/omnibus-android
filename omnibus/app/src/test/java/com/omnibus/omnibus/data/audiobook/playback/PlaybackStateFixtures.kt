package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudioPart
import com.omnibus.omnibus.data.audiobook.AudiobookManifest
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomOf

/**
 * Chapters of the given lengths, contiguous from 0, with resolved lengths already set. Titles default to "Chapter 1",
 * "Chapter 2" and so on, and ordinals to 1, 2 and so on.
 */
fun chapters(
    vararg lengthsSeconds: Double,
    titles: List<String> = lengthsSeconds.indices.map { "Chapter ${it + 1}" },
): List<Chapter> {
    var start = 0.0
    return lengthsSeconds.mapIndexed { index, length ->
        Chapter(
            ordinal = index + 1L,
            title = titles[index],
            startSeconds = start,
            durationSeconds = length,
        ).also { start += length }
    }
}

/**
 * A [PlaybackState] whose chapters, position and duration agree: the current chapter index is derived from
 * [positionSeconds] with [chapterIndexAt], and the duration defaults to the sum of the chapters. Everything a test does
 * not state is random. A test that needs no such agreement uses `randomPlaybackState().copy(...)` instead.
 */
fun playbackState(
    book: PlayingBook? = randomPlayingBook(),
    chapters: List<Chapter> = chapters(600.0, 1200.0, 900.0),
    positionSeconds: Double = randomDouble(0.0, 2700.0),
    durationSeconds: Double = chapters.sumOf { it.durationSeconds }.takeIf { it > 0 } ?: 2700.0,
    isPlaying: Boolean = randomBoolean(),
    isLoading: Boolean = randomBoolean(),
    isBuffering: Boolean = randomBoolean(),
    rate: Double = randomDouble(MIN_RATE, MAX_RATE),
    error: PlaybackError? = randomOf(listOf(null, randomPlaybackError())),
    sleepTimer: SleepTimerState? = randomOf(listOf(null, randomSleepTimerState())),
): PlaybackState = PlaybackState(
    book = book,
    chapters = chapters,
    positionSeconds = positionSeconds,
    durationSeconds = durationSeconds,
    currentChapterIndex = chapterIndexAt(chapters, positionSeconds),
    isPlaying = isPlaying,
    isLoading = isLoading,
    isBuffering = isBuffering,
    rate = rate,
    error = error,
    sleepTimer = sleepTimer,
)

/** A loaded book with no chapters, of [durationSeconds] in total. */
fun noChapterState(positionSeconds: Double = 0.0, durationSeconds: Double = 3600.0): PlaybackState = playbackState(
    chapters = emptyList(),
    positionSeconds = positionSeconds,
    durationSeconds = durationSeconds,
)

/**
 * A `direct` manifest of three parts (1000, 1500 and 500 s) whose chapters (700, 1200 and 1100 s) cross the part
 * boundaries, so a span can start in one part and end in another.
 */
fun multiPartManifest(bookFileId: Long = 11L): AudiobookManifest.Direct {
    val headers = mapOf("Authorization" to "Bearer secret", "Accept" to "*/*")
    val parts = listOf(1000.0, 1500.0, 500.0).mapIndexed { index, seconds ->
        val ordinal = index + 1L
        AudioPart(
            ordinal = ordinal,
            url = "https://server.test/api/audiobooks/book-1/parts/$ordinal?file_id=$bookFileId",
            durationSeconds = seconds,
            mime = "audio/mpeg",
        )
    }
    return AudiobookManifest.Direct(
        bookFileId = bookFileId,
        parts = parts,
        totalDurationSeconds = 3000.0,
        chapters = chapters(700.0, 1200.0, 1100.0),
        requestHeaders = headers,
    )
}
