package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString

fun randomAudioPosition(): AudioPosition = AudioPosition(
    bookUuid = randomString(),
    positionSeconds = randomDouble(0.01, 40_000.0),
    bookFileId = randomLong(1L..1_000_000L),
    clientUpdatedAt = randomLong(),
)

fun randomServerAudioPosition(): ServerAudioPosition = ServerAudioPosition(
    positionSeconds = randomDouble(0.01, 40_000.0),
    bookFileId = randomLong(1L..1_000_000L),
    orderingClock = randomLong(),
)

fun randomLatePosition(): LatePosition = LatePosition(
    bookUuid = randomString(),
    bookFileId = randomLong(1L..1_000_000L),
    positionSeconds = randomDouble(0.01, 40_000.0),
)

fun randomListeningSessionReport(): ListeningSessionReport = ListeningSessionReport(
    bookUuid = randomString(),
    startedAt = randomLong(),
    endedAt = randomLong(),
    progressUnits = randomLong(5L..86_400L),
    clientId = randomString(16..16),
    utcOffsetMinutes = randomInt(-720..840),
    timeZone = randomOf(listOf("UTC", "Asia/Kolkata", "America/New_York")),
)

/** Lives here until the playback package has its own random fixtures: its `playingBook()` is pinned. */
fun randomPlayingBook(): PlayingBook = PlayingBook(
    bookUuid = randomString(),
    bookFileId = randomLong(1L..1_000_000L),
    title = randomString(),
    author = randomString(),
    hasEbook = randomBoolean(),
)
