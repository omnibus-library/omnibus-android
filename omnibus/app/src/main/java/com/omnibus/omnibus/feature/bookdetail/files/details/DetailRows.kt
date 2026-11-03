package com.omnibus.omnibus.feature.bookdetail.files.details

import com.omnibus.omnibus.data.library.Book
import java.util.Locale
import java.util.TimeZone

/** The rows of the Details list, in display order. */
internal enum class DetailRow { Publisher, Published, Series, Language, Isbn, Added, File }

/** One row of the Details list: what it is and the text to show for it. */
internal data class DetailItem(val row: DetailRow, val value: String)

/** The words of the list that come from resources, so the rows are built on the JVM and filled from resources on the device. */
internal data class DetailsWording(
    /** "Sherlock Holmes, book 2", from the series name and its number. */
    val seriesBook: (name: String, index: String) -> String,
    /** The name for a language code that declines to answer, such as `und`. */
    val unknownLanguage: String,
)

/**
 * The rows to show for [book]: Publisher, Published, Series, Language, ISBN, Added and File, in that order. A row the
 * record has nothing for, or only blank text, is left out (iOS `BookFilesCopy.details`).
 */
internal fun bookDetailItems(
    book: Book,
    locale: Locale,
    timeZone: TimeZone,
    wording: DetailsWording,
): List<DetailItem> {
    val series = book.series.clean()?.let { name ->
        book.seriesIndex.clean()?.let { wording.seriesBook(name, it) } ?: name
    }
    return listOf(
        DetailRow.Publisher to book.publisher.clean(),
        DetailRow.Published to book.publishedDate.clean()?.let { publishedText(it, locale) },
        DetailRow.Series to series,
        DetailRow.Language to languageName(book.language, locale, wording.unknownLanguage),
        DetailRow.Isbn to book.isbn13.clean(),
        DetailRow.Added to book.addedAt.clean()?.let { addedText(it, locale, timeZone) },
        DetailRow.File to book.filename.clean(),
    ).mapNotNull { (row, value) -> value?.let { DetailItem(row, it) } }
}

private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
