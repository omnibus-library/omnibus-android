package com.omnibus.omnibus.feature.bookdetail.overview.reading

import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import com.omnibus.omnibus.feature.bookdetail.progress.pacePerHour
import com.omnibus.omnibus.feature.bookdetail.progress.timeLeftSeconds
import kotlin.math.roundToInt

/** The reader's sessions with a book: still loading, failed, or the answer. */
sealed interface SessionsState {
    data object Loading : SessionsState
    data object Failed : SessionsState

    /** The sessions, newest first. */
    data class Loaded(val sessions: List<ReadingSession>) : SessionsState
}

/** What the progress says about the book: its fraction (null when unknown) and whether any position is saved. */
data class CardProgress(val fraction: Float?, val hasPosition: Boolean)

/** The line under the card's heading. */
sealed interface ReadingLine {
    data object NoReading : ReadingLine
    data object Wishlist : ReadingLine
    data object HistoryFailed : ReadingLine
    data object PaperCopy : ReadingLine
    data object SyncPending : ReadingLine

    /** Reading since [millis], the start of the earliest session. */
    data class ReadingSince(val millis: Long) : ReadingLine

    /** Finished on [millis]. */
    data class Finished(val millis: Long) : ReadingLine
}

/** The kinds of row, in the order the card lists them. */
enum class ReadingRowKind { Started, TimeRead, Pace, LastRead, LongestSitting, TimeLeft }

/** One row of the card. [kind] names it for its label. */
sealed interface ReadingRow {
    val kind: ReadingRowKind

    data class Started(val millis: Long) : ReadingRow {
        override val kind: ReadingRowKind get() = ReadingRowKind.Started
    }

    /** The total of [seconds] over [sessions] sittings. */
    data class TimeRead(val seconds: Long, val sessions: Int) : ReadingRow {
        override val kind: ReadingRowKind get() = ReadingRowKind.TimeRead
    }

    /** Whole percent of the book per hour. */
    data class Pace(val percentPerHour: Int) : ReadingRow {
        override val kind: ReadingRowKind get() = ReadingRowKind.Pace
    }

    /** When the latest sitting ended, in epoch milliseconds. */
    data class LastRead(val millis: Long) : ReadingRow {
        override val kind: ReadingRowKind get() = ReadingRowKind.LastRead
    }

    /** The longest sitting, which lasted [seconds] and started at [startedAtMillis]. */
    data class LongestSitting(val seconds: Long, val startedAtMillis: Long) : ReadingRow {
        override val kind: ReadingRowKind get() = ReadingRowKind.LongestSitting
    }

    /** Seconds left at the reader's pace. */
    data class TimeLeft(val seconds: Long) : ReadingRow {
        override val kind: ReadingRowKind get() = ReadingRowKind.TimeLeft
    }
}

/**
 * What the card shows. A null [line] with no [rows] is the hold state: the heading alone, while the answers load.
 */
data class ReadingCardModel(val line: ReadingLine?, val rows: List<ReadingRow>)

private val HOLD = ReadingCardModel(line = null, rows = emptyList())

/**
 * The card for a book, from its read status, sessions and progress. [progress] is null until the progress flow emits.
 * The first matching rule wins: a wishlist-only book, then the hold while status or sessions are unknown, then a failed
 * status, a finished date, a failed sessions read, Unread, the sessions of a Reading or Finished book, and last the
 * empty states.
 */
fun readingCardModel(
    wishlistOnly: Boolean,
    hasFile: Boolean,
    status: ReadStatusState,
    sessions: SessionsState,
    progress: CardProgress?,
): ReadingCardModel {
    if (wishlistOnly) return ReadingCardModel(ReadingLine.Wishlist, emptyList())
    if (status is ReadStatusState.Unknown || sessions is SessionsState.Loading) return HOLD
    if (status !is ReadStatusState.Known) return ReadingCardModel(ReadingLine.HistoryFailed, emptyList())
    val record = status.record
    val loaded = (sessions as? SessionsState.Loaded)?.sessions.orEmpty()
    val finishedAt = record.finishedAtMillis
    if (record.status == ReadStatus.Finished && finishedAt != null) {
        return ReadingCardModel(ReadingLine.Finished(finishedAt), readingRows(loaded, progress?.fraction, isReading = false))
    }
    if (sessions is SessionsState.Failed) return ReadingCardModel(ReadingLine.HistoryFailed, emptyList())
    val emptyLine = if (hasFile) ReadingLine.NoReading else ReadingLine.PaperCopy
    if (record.status == ReadStatus.Unread) return ReadingCardModel(emptyLine, emptyList())
    val isReading = record.status == ReadStatus.Reading
    if (loaded.isNotEmpty()) {
        val line = if (isReading) ReadingLine.ReadingSince(loaded.minOf { it.startedAtMillis }) else null
        return ReadingCardModel(line, readingRows(loaded, progress?.fraction, isReading))
    }
    val line = if (isReading && progress?.hasPosition == true) ReadingLine.SyncPending else emptyLine
    return ReadingCardModel(line, emptyList())
}

/**
 * The rows for [sessions] (newest first), in display order. Pace needs [fraction]; time left needs a reading book, a
 * pace, and at least a minute left. Empty for no sessions.
 */
internal fun readingRows(sessions: List<ReadingSession>, fraction: Float?, isReading: Boolean): List<ReadingRow> {
    if (sessions.isEmpty()) return emptyList()
    val pace = fraction?.let { pacePerHour(sessions, it) }
    val timeLeft = if (isReading && fraction != null && pace != null) {
        timeLeftSeconds(fraction, pace)?.takeIf { it >= MIN_TIME_LEFT_SECONDS }
    } else {
        null
    }
    return buildList {
        add(ReadingRow.Started(sessions.minOf { it.startedAtMillis }))
        add(ReadingRow.TimeRead(sessions.sumOf { it.durationSeconds }, sessions.size))
        pace?.roundToInt()?.takeIf { it >= 1 }?.let { add(ReadingRow.Pace(it)) }
        add(ReadingRow.LastRead(sessions.maxOf { it.startedAtMillis + it.durationSeconds * 1_000L }))
        // maxBy keeps the first maximum, which is the newest sitting for a tie.
        sessions.maxBy { it.durationSeconds }.let { add(ReadingRow.LongestSitting(it.durationSeconds, it.startedAtMillis)) }
        timeLeft?.let { add(ReadingRow.TimeLeft(it)) }
    }
}

/** Time left under this is not worth a row, as the progress line drops it too. */
private const val MIN_TIME_LEFT_SECONDS = 60L
