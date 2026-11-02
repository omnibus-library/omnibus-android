package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import java.util.Locale
import kotlin.math.floor

/** What the flag above the bar says: a chapter, the elapsed audio time, or a percentage. */
sealed interface FlagLabel {
    /** The reader's chapter, 1-based. */
    data class Chapter(val number: Int) : FlagLabel

    /** The audio position, in seconds into the file. */
    data class Elapsed(val seconds: Long) : FlagLabel

    /** A whole percentage, when no chapter is known. */
    data class Percent(val percent: Int) : FlagLabel
}

/** The part of the line after the percentage. */
sealed interface LineTail {
    /** Time left at the reader's pace, in seconds, shown for an ebook. */
    data class LeftAtPace(val seconds: Long) : LineTail

    /** Time left of the audiobook's total, in seconds, shown for audio. */
    data class LeftOf(val leftSeconds: Long, val totalSeconds: Long) : LineTail
}

/** What the progress section shows for a book. */
sealed interface ProgressDisplay {
    /** Nothing: the status is not Reading, or there is no position. */
    data object Hidden : ProgressDisplay

    /** A position is saved, but its place in the book cannot be measured. */
    data object InProgress : ProgressDisplay

    /**
     * The bar at [fraction] (0 to 1).
     *
     * @property percent The whole percentage, rounded down, 0 to 100.
     * @property isAudio True when the position is an audio position.
     * @property segments The bar's segments, with their fills.
     * @property tail The line's tail, or null when there is no time to show.
     */
    data class Bar(
        val fraction: Float,
        val percent: Int,
        val isAudio: Boolean,
        val segments: List<BarSegment>,
        val flag: FlagLabel,
        val tail: LineTail?,
    ) : ProgressDisplay
}

/**
 * The progress display for a book. Only a Reading status shows anything. A Reading status with a position shows the bar
 * when the place can be measured, else "In progress". [sessions] is null while they are not loaded or the last read
 * failed, so no pace is shown.
 */
fun progressDisplay(
    status: ReadStatusState,
    hasEbook: Boolean,
    records: List<ProgressRecord>,
    chapters: BookChaptersResult.Success?,
    sessions: List<ReadingSession>?,
): ProgressDisplay {
    if (!status.isReading()) return ProgressDisplay.Hidden
    val progress = bookReadingProgress(records, chapters) ?: return ProgressDisplay.Hidden
    val fraction = progress.fraction ?: return ProgressDisplay.InProgress
    val percent = floor(fraction.toDouble() * 100.0 + PERCENT_TOLERANCE).toInt().coerceIn(0, 100)
    val isAudio = progress.latestMedium == ProgressFormat.Listening
    val chapterList = if (isAudio || !hasEbook) chapters?.audioChapters else chapters?.ebookChapters
    val segments = barSegments(segmentWeights(chapterList.orEmpty()), fraction)
    val flag = when {
        isAudio -> FlagLabel.Elapsed(progress.audioElapsedSeconds ?: 0)
        else -> progress.chapterNumber?.let { FlagLabel.Chapter(it) } ?: FlagLabel.Percent(percent)
    }
    val tail = if (isAudio) {
        progress.audioTotalSeconds?.let { total ->
            LineTail.LeftOf((total - (progress.audioElapsedSeconds ?: 0)).coerceAtLeast(0), total)
        }
    } else {
        sessions?.let { pacePerHour(it, fraction) }
            ?.let { timeLeftSeconds(fraction, it) }
            ?.takeIf { it >= MIN_TAIL_SECONDS }
            ?.let { LineTail.LeftAtPace(it) }
    }
    return ProgressDisplay.Bar(fraction, percent, isAudio, segments, flag, tail)
}

/**
 * Whether the "Offline. This book isn't downloaded." note shows: only while the status is Reading, the device is offline
 * and the book has no downloaded copy. Unread, Finished, unknown and failed statuses never show it.
 */
fun showsOfflineNote(status: ReadStatusState, offlineNotDownloaded: Boolean): Boolean =
    offlineNotDownloaded && status.isReading()

/** The English-or-localised templates the line is built from, read from the string resources. */
data class ProgressStrings(
    /** `Ch. %1$d`. */
    val chapterFlag: String,
    /** `%1$d%%`, used for the flag's fallback and the line's percentage. */
    val percent: String,
    /** ` · about %1$s left at your pace`. */
    val leftAtPace: String,
    /** ` · %1$s left of %2$s`. */
    val leftOf: String,
    /** The duration text for under a minute, such as `0 m`. */
    val underAMinute: String,
)

/** The flag's text, such as "Ch. 6", "2 h 31 m" or "62%". */
fun FlagLabel.text(strings: ProgressStrings, locale: Locale): String = when (this) {
    is FlagLabel.Chapter -> String.format(locale, strings.chapterFlag, number)
    is FlagLabel.Elapsed -> BookDetailFormat.hoursMinutes(seconds, strings.underAMinute)
    is FlagLabel.Percent -> String.format(locale, strings.percent, percent)
}

/** The bar's percentage as the line starts with it, such as "62%". */
fun ProgressDisplay.Bar.percentText(strings: ProgressStrings, locale: Locale): String =
    String.format(locale, strings.percent, percent)

/** The line's tail, such as " · about 1 h 53 m left at your pace", or " · 1 h 49 m left of 4 h 49 m". */
fun LineTail.text(strings: ProgressStrings, locale: Locale): String = when (this) {
    is LineTail.LeftAtPace -> String.format(
        locale,
        strings.leftAtPace,
        BookDetailFormat.hoursMinutes(seconds, strings.underAMinute),
    )
    is LineTail.LeftOf -> String.format(
        locale,
        strings.leftOf,
        BookDetailFormat.hoursMinutes(leftSeconds, strings.underAMinute),
        BookDetailFormat.hoursMinutes(totalSeconds, strings.underAMinute),
    )
}

private fun ReadStatusState.isReading(): Boolean =
    (this as? ReadStatusState.Known)?.record?.status == ReadStatus.Reading

/** Adds to the percentage before flooring, so a float's rounding error (57.9999 for 58%) reads as 58. */
private const val PERCENT_TOLERANCE = 0.001

/** A time left shorter than this is not shown, as iOS does. */
private const val MIN_TAIL_SECONDS = 60L
