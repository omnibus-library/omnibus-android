package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.progress.PositionSync
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.feature.bookdetail.more.delete.hasFiles
import com.omnibus.omnibus.feature.bookdetail.progress.chapterNumberAt
import java.util.Locale
import java.util.TimeZone
import kotlin.math.floor
import kotlin.math.roundToInt

// Pure rules for the Bookmarks sheet and its menu row. They port iOS `BookmarkCopy`, with no Compose or Android types.

/** The longest audio position accepted, in seconds. Larger values are text. */
internal const val MAX_LISTENING_SECONDS = 1_000_000_000.0

/** A plain decimal with no sign, exponent, hex or type suffix. Kotlin's `toDouble` accepts more than this. */
private val PLAIN_DECIMAL = Regex("""^[0-9]+(\.[0-9]+)?$""")

/** The strings a bookmark's name uses, so the functions that build a name stay pure. */
internal data class BookmarkWording(
    val savedPosition: String = "Saved position",
    val listening: (String) -> String = { "Listening, $it in" },
    val underAMinute: String = "under a minute",
)

/**
 * Where a bookmark sits in the book.
 *
 * @property chapterNumber The 1-based chapter, or null when it can't be placed.
 * @property percent The whole percent through the book, 0 to 100, or null when unknown.
 */
data class BookmarkPlacement(val chapterNumber: Int?, val percent: Int?)

/** Whether the Bookmarks menu row shows for [book]: it needs at least one file to read from. */
internal fun showsBookmarksRow(book: Book): Boolean = hasFiles(book)

/** The audio time in seconds when [position] is a plain decimal in range, or null for a text position. */
internal fun listeningSeconds(position: String): Double? {
    val text = position.trim()
    if (!PLAIN_DECIMAL.matches(text)) return null
    return text.toDoubleOrNull()?.takeIf { it.isFinite() && it in 0.0..MAX_LISTENING_SECONDS }
}

/** Whether [bookmark] is an audio bookmark. The decision reads the position only. */
internal fun isAudio(bookmark: Bookmark): Boolean = listeningSeconds(bookmark.position) != null

/**
 * The name of a bookmark: its trimmed title, else the listening time of an audio position, else "Saved position".
 * The chapter title is never used.
 */
internal fun bookmarkName(bookmark: Bookmark, wording: BookmarkWording = BookmarkWording()): String =
    bookmark.title?.trim()?.takeIf { it.isNotEmpty() }
        ?: listeningSeconds(bookmark.position)?.let {
            wording.listening(BookDetailFormat.hoursMinutes(floor(it).toLong(), wording.underAMinute))
        }
        ?: wording.savedPosition

/** A fraction as a whole percent, clamped to 0..100, or null when it is missing or NaN. */
internal fun wholePercent(fraction: Double?): Int? =
    fraction?.takeIf { !it.isNaN() }?.let { (it.coerceIn(0.0, 1.0) * 100).roundToInt() }

/**
 * Where [bookmark] sits. A text bookmark uses the server's fraction and the ebook chapters. An audio bookmark uses its
 * seconds over the known total and the audio chapters, and never the server's percent. Without a known total there
 * is no placement.
 */
internal fun bookmarkPlacement(bookmark: Bookmark, chapters: BookChaptersResult.Success?): BookmarkPlacement {
    val seconds = listeningSeconds(bookmark.position)
    if (seconds == null) {
        return BookmarkPlacement(
            chapterNumber = chapterNumberAt(bookmark.fraction, chapters?.ebookChapters.orEmpty()),
            percent = wholePercent(bookmark.fraction?.toDouble()),
        )
    }
    val loaded = chapters ?: return BookmarkPlacement(null, null)
    val total = loaded.audioTotalSeconds?.takeIf { it > 0 } ?: return BookmarkPlacement(null, null)
    val fraction = (seconds / total).coerceIn(0.0, 1.0)
    return BookmarkPlacement(
        chapterNumber = chapterNumberAt(fraction.toFloat(), loaded.audioChapters),
        percent = wholePercent(fraction),
    )
}

/**
 * The meta parts of a row, in order: chapter, percent and date. Parts that are missing are left out.
 *
 * @param chapterLabel Formats the chapter number.
 * @param percentLabel Formats the percent.
 */
internal fun bookmarkMetaParts(
    placement: BookmarkPlacement,
    createdAtMillis: Long?,
    nowMillis: Long,
    locale: Locale,
    timeZone: TimeZone,
    chapterLabel: (Int) -> String = { "Chapter $it" },
    percentLabel: (Int) -> String = { "$it%" },
): List<String> = listOfNotNull(
    placement.chapterNumber?.let(chapterLabel),
    placement.percent?.let(percentLabel),
    createdAtMillis?.let { BookDetailFormat.longDate(it, nowMillis, locale, timeZone) },
)

/** The meta line joining [parts] with [separator], or null when there are none. */
internal fun bookmarkMeta(parts: List<String>, separator: String = " · "): String? =
    parts.takeIf { it.isNotEmpty() }?.joinToString(separator)

/** The spoken description of a row: its name, then each meta part, joined with ", ". */
internal fun bookmarkRowDescription(name: String, parts: List<String>): String =
    (listOf(name) + parts).joinToString(", ")

/**
 * Where a tap on [bookmark] leads. An audio bookmark opens the player at its time. A text bookmark opens the reader at
 * its CFI, or at the saved position when the position is not an EPUB CFI.
 */
internal fun bookmarkDestination(bookUuid: String, bookmark: Bookmark): BookDetailDestination {
    val seconds = listeningSeconds(bookmark.position)
    if (seconds != null) return BookDetailDestination.Player(bookUuid, bookFileId = null, positionSeconds = seconds)
    return BookDetailDestination.Reader(
        bookUuid,
        startCfi = bookmark.position.trim().takeIf { PositionSync.isEpubCfi(it) },
    )
}
