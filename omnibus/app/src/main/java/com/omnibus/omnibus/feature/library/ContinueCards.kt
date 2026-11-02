package com.omnibus.omnibus.feature.library

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ResumePoint

/** The most cards the Continue carousel shows. */
const val MAX_CONTINUE_CARDS = 5

/** "{bookUuid}:epub" for a reading record, "{bookUuid}:audio" for a listening one. Matches iOS ResumePoint.id. */
val ResumePoint.cardId: String
    get() = "${record.bookUuid}:" + if (record.format == ProgressFormat.Listening) "audio" else "epub"

/** The medium a card presents (iOS Book.resumeFormat). */
fun Book.resumeMedium(requested: ProgressFormat): ProgressFormat = when {
    formats.isEmpty() -> requested
    requested == ProgressFormat.Listening -> if (hasAudiobook) ProgressFormat.Listening else ProgressFormat.Reading
    else -> if (hasEbook) ProgressFormat.Reading else ProgressFormat.Listening
}

/** 0..1, or null without an honest denominator or when [presented] differs from the record's format. */
fun ResumePoint.fraction(presented: ProgressFormat): Double? {
    if (presented != record.format) return null
    val raw = when (presented) {
        ProgressFormat.Listening -> {
            val total = record.totalDurationSeconds
            val position = record.audioPositionSeconds
            if (total == null || total <= 0.0 || position == null) null else position / total
        }

        ProgressFormat.Reading -> record.progressPercent?.let { it / 100.0 }
    }
    return raw?.coerceIn(0.0, 1.0)
}

/** The line under the progress bar that says where the reader or listener stopped. */
sealed interface PositionLabel {
    data class Chapter(val number: Int, val total: Int) : PositionLabel
    data class Part(val number: Int, val total: Int) : PositionLabel
    data class TimeLeft(val seconds: Long) : PositionLabel
    data class PercentRead(val percent: Int) : PositionLabel
    data object InProgress : PositionLabel
}

/**
 * The position label for [point] shown as [presented]. The first matching rule wins: a medium
 * mismatch, then the listening rules (chapter, part, time left), then the reading percent.
 */
fun positionLabelOf(point: ResumePoint, presented: ProgressFormat): PositionLabel {
    val record = point.record
    if (presented != record.format) return PositionLabel.InProgress
    return when (presented) {
        ProgressFormat.Listening -> listeningLabel(point)
        ProgressFormat.Reading -> point.fraction(presented)
            ?.let { PositionLabel.PercentRead(Math.round(it * 100).toInt()) }
            ?: PositionLabel.InProgress
    }
}

private fun listeningLabel(point: ResumePoint): PositionLabel {
    val record = point.record
    val resolved = record.resolved
    val ordinal = resolved?.chapterOrdinal
    val chapters = resolved?.chaptersTotal
    if (resolved?.confidence == PositionConfidence.High && ordinal != null && chapters != null) {
        return PositionLabel.Chapter(ordinal, chapters)
    }
    val part = point.audioPart
    val partCount = point.audioPartCount
    if (part != null && partCount != null) return PositionLabel.Part(part, partCount)

    val total = record.totalDurationSeconds
    val position = record.audioPositionSeconds
    if (total != null && total > 0.0 && position != null) {
        // A rate that is missing, non-finite or not positive counts as normal speed.
        val rate = point.playbackRate?.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        return PositionLabel.TimeLeft(((total - position).coerceAtLeast(0.0) / rate).toLong())
    }
    return PositionLabel.InProgress
}

/** "{h}h {m}m", "{h}h", "{m}m", "{s}s", or "0m" for zero or less. Not localized, as on iOS. */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0m"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        m > 0 -> "${m}m"
        else -> "${seconds}s"
    }
}

/** How long ago a card was last opened, bucketed for the "Last opened" line. */
sealed interface RelativeAge {
    data object JustNow : RelativeAge
    data class Seconds(val count: Int) : RelativeAge
    data class Minutes(val count: Int) : RelativeAge
    data class Hours(val count: Int) : RelativeAge
    data class Days(val count: Int) : RelativeAge
    data class Weeks(val count: Int) : RelativeAge
    data class Months(val count: Int) : RelativeAge
    data class Years(val count: Int) : RelativeAge
}

/**
 * Buckets the age of [updatedAtSeconds] (unix seconds) against [nowMillis], each floored. Less than
 * a second old, or in the future, is [RelativeAge.JustNow].
 */
fun relativeAgeOf(updatedAtSeconds: Long, nowMillis: Long): RelativeAge {
    val ageMillis = nowMillis - updatedAtSeconds * 1000
    if (ageMillis < 1000) return RelativeAge.JustNow
    val s = ageMillis / 1000
    return when {
        s < 60 -> RelativeAge.Seconds(s.toInt())
        s < 3_600 -> RelativeAge.Minutes((s / 60).toInt())
        s < 86_400 -> RelativeAge.Hours((s / 3_600).toInt())
        s < 7 * 86_400 -> RelativeAge.Days((s / 86_400).toInt())
        s < 30 * 86_400 -> RelativeAge.Weeks((s / (7 * 86_400)).toInt())
        s < 365 * 86_400 -> RelativeAge.Months((s / (30 * 86_400)).toInt())
        else -> RelativeAge.Years((s / (365 * 86_400)).toInt())
    }
}

/** Keeps [current] while it is still in [ids], else the first id; null for an empty list. */
fun selectionAfterRefresh(current: String?, ids: List<String>): String? =
    if (current != null && current in ids) current else ids.firstOrNull()

/** The id at [settledIndex], or null for an index that maps to no card, which the caller ignores. */
fun settledCardId(settledIndex: Int, ids: List<String>): String? = ids.getOrNull(settledIndex)

/** One card of the Continue carousel, decided once from its resume point. */
data class ContinueCard(
    val id: String,
    val book: Book,
    /** The medium the card presents, which can differ from the record's format. */
    val medium: ProgressFormat,
    /** Progress 0..1 for the bar, or null when the bar is hidden. */
    val fraction: Double?,
    val position: PositionLabel,
    /** Shown in place of the bar when [fraction] is null. */
    val lastOpened: RelativeAge,
    /** The record's file, only when the card presents the record's own medium (A4). */
    val resumeFileId: Long?,
)

/** Builds the carousel's cards from the server's resume points, keeping server order and the first card per id. */
fun continueCardsOf(points: List<ResumePoint>, nowMillis: Long): List<ContinueCard> =
    points.distinctBy { it.cardId }.take(MAX_CONTINUE_CARDS).map { point ->
        val medium = point.book.resumeMedium(point.record.format)
        ContinueCard(
            id = point.cardId,
            book = point.book,
            medium = medium,
            fraction = point.fraction(medium),
            position = positionLabelOf(point, medium),
            lastOpened = relativeAgeOf(point.record.updatedAt, nowMillis),
            resumeFileId = point.record.bookFileId.takeIf { medium == point.record.format },
        )
    }
