package com.omnibus.omnibus.feature.bookdetail.files.details

import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

private val UTC: TimeZone = TimeZone.getTimeZone("UTC")

private val PublishedShape = Regex("""^(\d{4})(?:-(\d{2})(?:-(\d{2}))?)?$""")
private val DateOnly = Regex("""^\d{4}-\d{2}-\d{2}$""")

/**
 * A publication date as a book would print it (iOS `Format.looseDate`). The record carries "1925-04-10", "1925-04"
 * or "1925", and each shape reads at the precision it has: "April 10, 1925", "April 1925", "1925". Only the first ten
 * characters are read, so a timestamp reads as its date. Anything else, such as "8/4/2015", is shown as the server
 * sent it, since a day-first or month-first guess could be wrong.
 */
internal fun publishedText(raw: String, locale: Locale): String {
    val text = raw.trim()
    val match = PublishedShape.matchEntire(text.take(DATE_LENGTH)) ?: return text
    val (year, month, day) = match.destructured
    val calendar = GregorianCalendar(UTC).apply {
        clear()
        isLenient = false
        set(year.toInt(), if (month.isEmpty()) 0 else month.toInt() - 1, if (day.isEmpty()) 1 else day.toInt())
    }
    val pattern = when {
        day.isNotEmpty() -> "MMMM d, yyyy"
        month.isNotEmpty() -> "MMMM yyyy"
        else -> "yyyy"
    }
    return try {
        formatUtc(calendar.timeInMillis, pattern, locale)
    } catch (e: IllegalArgumentException) {
        text // A month 13 or a Feb 30
    }
}

/**
 * When the book was added, such as "October 2, 2026", with the year always shown. A bare date is read as that day;
 * a timestamp is read in [timeZone]. Text that is not a timestamp is shown as the server sent it.
 */
internal fun addedText(raw: String, locale: Locale, timeZone: TimeZone): String {
    val text = raw.trim()
    val millis = BookDetailFormat.parseServerTimestamp(text) ?: return text
    return formatIn(millis, "MMMM d, yyyy", locale, if (DateOnly.matches(text)) UTC else timeZone)
}

private const val DATE_LENGTH = 10

private fun formatUtc(millis: Long, pattern: String, locale: Locale): String = formatIn(millis, pattern, locale, UTC)

private fun formatIn(millis: Long, pattern: String, locale: Locale, timeZone: TimeZone): String =
    SimpleDateFormat(pattern, locale).apply { this.timeZone = timeZone }.format(Date(millis))

/** ISO 639-2/B codes whose three letters differ from the ISO 639-2/T code Java knows (`ger`, not `deu`). */
private val BibliographicCodes = mapOf(
    "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "chi" to "zh", "cze" to "cs", "dut" to "nl",
    "fre" to "fr", "geo" to "ka", "ger" to "de", "gre" to "el", "ice" to "is", "mac" to "mk", "mao" to "mi",
    "may" to "ms", "per" to "fa", "rum" to "ro", "slo" to "sk", "tib" to "bo", "wel" to "cy",
)

private val TwoLetterByThree: Map<String, String> by lazy {
    Locale.getISOLanguages().associateBy { Locale(it).isO3Language }
}

/** Codes that decline to answer: undetermined, multiple, no linguistic content, uncoded. */
private val UndecidedCodes = setOf("und", "mul", "zxx", "mis")

/**
 * A language code as a reader names it (iOS `Format.language`): `en`, `eng` and `en-US` all read "English" in an
 * English [displayLocale]. A code that declines to answer reads [unknown]; a code no name is known for is shown in
 * capitals; blank or missing gives null.
 */
internal fun languageName(code: String?, displayLocale: Locale, unknown: String): String? {
    val primary = code?.trim()?.split('-', '_')?.firstOrNull()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
    if (primary in UndecidedCodes) return unknown
    val twoLetter = if (primary.length == 3) BibliographicCodes[primary] ?: TwoLetterByThree[primary] else primary
    val name = twoLetter?.let { Locale(it).getDisplayLanguage(displayLocale) }
    return if (name.isNullOrEmpty() || name.equals(twoLetter, ignoreCase = true)) primary.uppercase() else name
}
