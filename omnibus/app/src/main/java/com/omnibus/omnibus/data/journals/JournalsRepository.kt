package com.omnibus.omnibus.data.journals

import kotlinx.coroutines.flow.Flow

/**
 * One entry in a book's journal.
 *
 * @property authorName The author's name; empty when the server sent none.
 * @property bodyMd The entry's Markdown, unrendered.
 * @property isDraft True for an unpublished draft. The server returns the viewer's own drafts beside published entries.
 * @property createdAtMillis When the entry was written, in epoch milliseconds; null when the server omitted it.
 */
data class JournalEntry(
    val id: Long,
    val authorId: Long,
    val authorName: String,
    val bodyMd: String,
    val isDraft: Boolean,
    val createdAtMillis: Long?,
)

/** One book's journal, as the repository knows it. Every failure is a state, so nothing is thrown. */
sealed interface JournalsState {
    /** No answer yet, or a fetch is in flight. */
    data object Loading : JournalsState

    /** The last fetch failed and no list was ever loaded. */
    data object Failed : JournalsState

    /** The book's entries in the server's order, newest first, at most 1,000. */
    data class Loaded(val entries: List<JournalEntry>) : JournalsState
}

/** The journal entries per book. Every failure is a state, so nothing throws except cancellation. */
interface JournalsRepository {

    /**
     * Hot state for [bookUuid]. The first collection fetches when nothing has loaded. A loaded list is never replaced by
     * Loading or Failed.
     */
    fun getJournalsFlow(bookUuid: String): Flow<JournalsState>

    /** Fetches [bookUuid]'s entries again, joining a fetch already in flight, and returns when that fetch ends. */
    suspend fun refresh(bookUuid: String)
}
