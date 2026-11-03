package com.omnibus.omnibus.data.bookmarks

import kotlinx.coroutines.flow.Flow

/**
 * The reader's bookmarks per book. Writes apply at once and roll back on failure. Every failure is a state or a result,
 * so nothing throws except cancellation.
 */
interface BookmarksRepository {

    /**
     * Hot state for [bookUuid]. Every collection starts a fetch, or joins one already in flight, so each reader open
     * re-reads the server. A loaded list is never replaced by Loading or Failed.
     */
    fun getBookmarksFlow(bookUuid: String): Flow<BookmarksState>

    /** Fetches [bookUuid]'s bookmarks again, joining a fetch already in flight, and returns when that fetch ends. */
    suspend fun refresh(bookUuid: String)

    /**
     * Adds the bookmark in [draft]. The row shows in the state at once with a freshly minted client id, then the server's
     * row replaces it on success, or it is removed on failure.
     */
    suspend fun addBookmark(draft: BookmarkDraft): BookmarkWriteResult

    /**
     * Removes [bookmark]. It leaves the state at once, and comes back where it was on failure. Writes for one book reach
     * the server in call order.
     */
    suspend fun removeBookmark(bookmark: Bookmark): BookmarkWriteResult
}
