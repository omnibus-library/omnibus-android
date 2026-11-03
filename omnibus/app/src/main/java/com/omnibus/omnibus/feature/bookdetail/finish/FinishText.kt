package com.omnibus.omnibus.feature.bookdetail.finish

import android.content.res.Resources
import com.omnibus.omnibus.R

/** The separator between totals parts, as on iOS. */
internal const val TOTALS_SEPARATOR = " · "

/** The card's words, so the wording is built on the JVM and filled from resources on the device. */
internal data class FinishWording(
    val title: (title: String) -> String,
    val days: (Int) -> String,
    val sessions: (Int) -> String,
    val highlights: (Int) -> String,
    val reviewTitle: String,
    val reviewDetail: String,
    val reviewAction: String,
    val nextTitle: (title: String) -> String,
    val nextDetail: (index: String, series: String) -> String,
    val nextDetailUnnumbered: (series: String) -> String,
    val nextAction: String,
)

/** One part's words, such as "17 days". */
internal fun FinishTotal.text(wording: FinishWording): String = when (this) {
    is FinishTotal.Days -> wording.days(count)
    is FinishTotal.Sessions -> wording.sessions(count)
    is FinishTotal.Highlights -> wording.highlights(count)
}

/** The totals line, or null when there are no parts. */
internal fun List<FinishTotal>.totalsText(wording: FinishWording): String? =
    if (isEmpty()) null else joinToString(TOTALS_SEPARATOR) { it.text(wording) }

/** "Book 3 of Sherlock Holmes", or "In Sherlock Holmes" when [index] is blank. */
internal fun nextDetail(index: String?, series: String, wording: FinishWording): String {
    val number = index?.trim()?.takeIf { it.isNotEmpty() } ?: return wording.nextDetailUnnumbered(series)
    return wording.nextDetail(number, series)
}

/** The card's words from the string resources. */
internal fun finishWording(resources: Resources): FinishWording = FinishWording(
    title = { resources.getString(R.string.book_detail_finish_title, it) },
    days = { resources.getQuantityString(R.plurals.book_detail_finish_days, it, it) },
    sessions = { resources.getQuantityString(R.plurals.book_detail_finish_sessions, it, it) },
    highlights = { resources.getQuantityString(R.plurals.book_detail_finish_highlights, it, it) },
    reviewTitle = resources.getString(R.string.book_detail_finish_review_title),
    reviewDetail = resources.getString(R.string.book_detail_finish_review_detail),
    reviewAction = resources.getString(R.string.book_detail_finish_review_action),
    nextTitle = { resources.getString(R.string.book_detail_finish_next_title, it) },
    nextDetail = { index, series -> resources.getString(R.string.book_detail_finish_next_detail, index, series) },
    nextDetailUnnumbered = { resources.getString(R.string.book_detail_finish_next_detail_unnumbered, it) },
    nextAction = resources.getString(R.string.book_detail_finish_next_action),
)
