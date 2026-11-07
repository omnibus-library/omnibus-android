package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString

fun randomPlayingBook(): PlayingBook = PlayingBook(
    bookUuid = randomString(),
    bookFileId = randomLong(1L..1_000_000L),
    title = randomString(),
    author = randomOf(listOf(null, randomString())),
    hasEbook = randomBoolean(),
)

fun randomChapter(): Chapter = Chapter(
    ordinal = randomLong(),
    title = randomString(),
    startSeconds = randomDouble(0.0, 10_000.0),
    durationSeconds = randomDouble(0.0, 10_000.0),
)

fun randomPlaybackError(): PlaybackError = randomOf(
    listOf(
        PlaybackError.NoAudioParts,
        PlaybackError.TimelineFailed,
        PlaybackError.PartUnreadable,
        PlaybackError.Server(randomOf(listOf(null, randomString()))),
        PlaybackError.StoppedAfterRetries,
    ),
)

fun randomSleepTimerOption(): SleepTimerOption =
    randomOf(listOf(SleepTimerOption.After(randomInt(1..120)), SleepTimerOption.EndOfChapter))

fun randomSleepTimerState(): SleepTimerState =
    SleepTimerState(option = randomSleepTimerOption(), remainingSeconds = randomDouble(0.0, 7_200.0))

fun randomPlaybackState(): PlaybackState {
    val chapters = List(randomInt(0..4)) { randomChapter() }
    return PlaybackState(
        book = randomOf(listOf(null, randomPlayingBook())),
        chapters = chapters,
        positionSeconds = randomDouble(0.0, 10_000.0),
        durationSeconds = randomDouble(0.0, 10_000.0),
        currentChapterIndex = chapters.indices.toList().let { randomOf(it + null) },
        isPlaying = randomBoolean(),
        isLoading = randomBoolean(),
        isBuffering = randomBoolean(),
        rate = randomDouble(MIN_RATE, MAX_RATE),
        error = randomOf(listOf(null, randomPlaybackError())),
        sleepTimer = randomOf(listOf(null, randomSleepTimerState())),
    )
}
