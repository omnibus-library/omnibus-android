package com.omnibus.omnibus.data.highlights

import kotlinx.coroutines.flow.Flow

/** A highlight's colour, as the server's lowercase token names it. */
enum class HighlightColor { Amber, Green, Blue, Rose, Violet }

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
}
