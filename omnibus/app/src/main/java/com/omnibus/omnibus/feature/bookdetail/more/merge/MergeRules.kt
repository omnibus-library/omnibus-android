package com.omnibus.omnibus.feature.bookdetail.more.merge

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.merge.MergeError

/** How long a typed query waits for the next key before it is searched (iOS and web use the same value). */
internal const val MERGE_SEARCH_DEBOUNCE_MILLIS = 250L

/** The longest query the server accepts, in UTF-8 bytes. */
internal const val MERGE_QUERY_MAX_BYTES = 1024

/** Joins the same-series label to the formats in a candidate's tags line. */
internal const val MERGE_META_SEPARATOR = " · "

/** Joins a candidate's formats in its tags line. */
internal const val MERGE_FORMAT_SEPARATOR = ", "

/** What the More menu's "Merge with…" row shows. */
internal enum class MergeRow { Hidden, Enabled, DisabledOffline }

/**
 * The row for the More menu. It is hidden unless the user is an admin and the book has files. Offline disables the row
 * rather than hiding it, so its sub-line can say why.
 */
internal fun mergeRow(isAdmin: Boolean?, hasFiles: Boolean, isOnline: Boolean): MergeRow = when {
    isAdmin != true || !hasFiles -> MergeRow.Hidden
    !isOnline -> MergeRow.DisabledOffline
    else -> MergeRow.Enabled
}

/** The search hits without [targetUuid], which is this book and never a merge candidate. Order is kept. */
internal fun mergeCandidates(hits: List<Book>, targetUuid: String): List<Book> =
    hits.filter { it.uuid != targetUuid }

/** "Author · 1999", or the author alone when there is no year. */
internal fun candidateMeta(book: Book): String =
    book.year?.let { "${book.authorDisplay}$MERGE_META_SEPARATOR$it" } ?: book.authorDisplay

/**
 * The tags line of a candidate: "Same series" when both books have the same non-null series id, then the candidate's
 * formats upper-cased. Empty when neither applies.
 *
 * @param sameSeriesLabel The localized "Same series".
 */
internal fun candidateTags(candidate: Book, target: Book, sameSeriesLabel: String): String {
    val sameSeries = candidate.seriesId != null && candidate.seriesId == target.seriesId
    val formats = candidate.formats.joinToString(MERGE_FORMAT_SEPARATOR) { it.uppercase() }
    return listOfNotNull(
        sameSeriesLabel.takeIf { sameSeries },
        formats.takeIf { it.isNotEmpty() },
    ).joinToString(MERGE_META_SEPARATOR)
}

/** Whether [query] fits the server's cap. The cap counts UTF-8 bytes, so a multi-byte character costs more than one. */
internal fun acceptsQuery(query: String): Boolean = query.encodeToByteArray().size <= MERGE_QUERY_MAX_BYTES

/** The note line under the search field. */
internal sealed interface MergeNote {
    data object Idle : MergeNote
    data object Searching : MergeNote
    data object Offline : MergeNote
    data object Failed : MergeNote

    /** [count] results are shown. */
    data class Count(val count: Int) : MergeNote

    /** Nothing matched [query], the trimmed text the user typed. */
    data class NoMatch(val query: String) : MergeNote
}

/**
 * The note for the search state. The first matching case wins, in this order: blank query, failed search, offline,
 * searching, then the result count.
 */
internal fun mergeNote(
    query: String,
    searching: Boolean,
    candidateCount: Int,
    isOnline: Boolean,
    searchFailed: Boolean,
): MergeNote {
    val trimmed = query.trim()
    return when {
        trimmed.isEmpty() -> MergeNote.Idle
        searchFailed -> MergeNote.Failed
        !isOnline -> MergeNote.Offline
        searching -> MergeNote.Searching
        candidateCount > 0 -> MergeNote.Count(candidateCount)
        else -> MergeNote.NoMatch(trimmed)
    }
}

/**
 * Whether a failed undo can never succeed, so its handle is released. A 404 means the merge is gone, and a 409 means a
 * later change settled it. Any other failure may succeed on retry.
 */
internal fun undoFailureIsTerminal(error: MergeError): Boolean =
    error is MergeError.NotFound || error is MergeError.Conflict
