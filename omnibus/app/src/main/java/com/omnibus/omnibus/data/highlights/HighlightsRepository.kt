package com.omnibus.omnibus.data.highlights

import kotlinx.coroutines.flow.Flow

/** A highlight's colour, as the server's lowercase token names it. */
enum class HighlightColor { Amber, Green, Blue, Rose, Violet }

/** The server's and the page glue's lowercase name for this colour. */
val HighlightColor.token: String get() = name.lowercase()

/**
 * A passage the reader kept.
 *
 * @property text The passage, or null when the highlight has no text (one made on another device).
 * @property note The reader's note on the passage, or null when there is none.
 * @property location The server's `epub_cfi_range`; null for an unresolved Kobo anchor.
 * @property format "epub" or "pdf", from the [location] prefix; null when unknown.
 * @property createdAtMillis When the highlight was made, in epoch milliseconds.
 * @property fraction How far through the book the highlight's spine document starts, 0..1; null when the server can't
 *   place it. The server measures this to the start of the document, so a chapter that starts mid-document resolves to
 *   the first chapter in that document.
 */
data class Highlight(
    val id: Long,
    val text: String?,
    val note: String?,
    val color: HighlightColor,
    val location: String?,
    val format: String?,
    val createdAtMillis: Long?,
    val fraction: Float? = null,
)

/** One book's highlights, as the repository knows them. Every failure is a state, so nothing is thrown. */
sealed interface HighlightsState {
    /** No answer yet, or a fetch is in flight. */
    data object Loading : HighlightsState

    /** The last fetch failed and no list was ever loaded. */
    data object Failed : HighlightsState

    /** The book's highlights in the server's reading order, at most 1,000. */
    data class Loaded(val highlights: List<Highlight>) : HighlightsState
}

/** The reader's highlights per book. Every failure is a state, so nothing throws except cancellation. */
interface HighlightsRepository {

    /**
     * Hot state for [bookUuid]. The first collection fetches when nothing has loaded. A loaded list is never replaced by
     * Loading or Failed.
     */
    fun getHighlightsFlow(bookUuid: String): Flow<HighlightsState>

    /** Fetches [bookUuid]'s highlights again, joining a fetch already in flight, and returns when that fetch ends. */
    suspend fun refresh(bookUuid: String)

    /**
     * Saves [draft] on the server in the application scope, so leaving the reader cannot cancel it, and adds the saved row
     * to a loaded list. Writes reach the server in call order.
     */
    suspend fun addHighlight(draft: HighlightDraft): HighlightWriteResult

    /**
     * Deletes the highlight with server [id] in [bookUuid] in the application scope, so leaving the reader cannot cancel
     * it, and drops it from a loaded list. Ordered after any create for the book. A 404 counts as deleted.
     */
    suspend fun deleteHighlight(bookUuid: String, id: Long): HighlightDeleteResult
}

/** The outcome of a highlight create. A failed create leaves the loaded list as it was. */
sealed interface HighlightWriteResult {
    /** The server accepted the create. [highlight] is the server's row. */
    data class Saved(val highlight: Highlight) : HighlightWriteResult

    /** The create did not reach the server. [error] says why. */
    data class Failed(val error: HighlightError) : HighlightWriteResult
}

/** The outcome of a highlight delete. A failed delete leaves the loaded list as it was. */
sealed interface HighlightDeleteResult {
    /** The server deleted the row, or had no such row (404). */
    data object Deleted : HighlightDeleteResult

    /** The delete did not take. [error] says why. */
    data class Failed(val error: HighlightError) : HighlightDeleteResult
}

/** Why a highlight write (a create or a delete) failed. */
sealed interface HighlightError {
    /** No server is configured, so no request was made. */
    data object NoServer : HighlightError

    /** The server could not be reached (an IOException). */
    data object Unreachable : HighlightError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : HighlightError

    /**
     * The server answered 404 to a create, or the book uuid was blank and no request was made. A delete never returns it.
     */
    data object BookNotFound : HighlightError

    /** The server answered 400, so it rejected the highlight's fields. */
    data object Rejected : HighlightError

    /** The server answered another non-2xx code. */
    data class Http(val code: Int) : HighlightError

    /** A 2xx body that does not decode, or any other unexpected failure. */
    data object UnexpectedResponse : HighlightError
}
