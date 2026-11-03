package com.omnibus.omnibus.feature.bookdetail.journal

import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The byline of a journal entry: the author's name, the date and [draftLabel], joined by [separator]. The name is left
 * out for the viewer's own entries and when it is blank. Null when no part applies, and then no byline is drawn.
 */
internal fun journalByline(
    authorName: String,
    isOwn: Boolean,
    isDraft: Boolean,
    createdAtMillis: Long?,
    nowMillis: Long,
    locale: Locale,
    timeZone: TimeZone,
    draftLabel: String,
    separator: String = " · ",
): String? = listOfNotNull(
    authorName.takeIf { !isOwn && it.isNotBlank() },
    createdAtMillis?.let { BookDetailFormat.longDate(it, nowMillis, locale, timeZone) },
    draftLabel.takeIf { isDraft },
).takeIf { it.isNotEmpty() }?.joinToString(separator)
