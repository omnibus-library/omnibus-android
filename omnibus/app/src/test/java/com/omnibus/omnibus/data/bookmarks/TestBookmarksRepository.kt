package com.omnibus.omnibus.data.bookmarks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [BookmarksRepository] for consumers' tests. There is no HTTP, and nothing fetches: collecting
 * [getBookmarksFlow] only records the request. Call [seed] to set a book's state. Each write is recorded, then answered by
 * [onAdd] or [onRemove], which a test can replace to script a result or to publish the rows it wants.
 */
class TestBookmarksRepository : BookmarksRepository {

    private val states = mutableMapOf<String, MutableStateFlow<BookmarksState>>()

    private fun stateFor(uuid: String): MutableStateFlow<BookmarksState> =
        states.getOrPut(uuid) { MutableStateFlow(BookmarksState.Loaded(emptyList())) }

    /** The uuid of every [getBookmarksFlow] call, in call order. */
    val flowRequests = mutableListOf<String>()

    /** The uuid of every [refresh] call, in call order. */
    val refreshCalls = mutableListOf<String>()

    /** Every draft passed to [addBookmark], in call order. */
    val drafts = mutableListOf<BookmarkDraft>()

    /** Every bookmark passed to [removeBookmark], in call order. */
    val removed = mutableListOf<Bookmark>()

    /** Answers an add. The default is a failure, so a test that does not script it sees no saved row. */
    var onAdd: suspend (BookmarkDraft) -> BookmarkWriteResult = { BookmarkWriteResult.Failed(BookmarkError.NoServer) }

    /** Answers a remove. The default is a removal. */
    var onRemove: suspend (Bookmark) -> BookmarkWriteResult = { BookmarkWriteResult.Removed }

    /** Sets [bookUuid]'s state as if the server had answered it. */
    fun seed(bookUuid: String, state: BookmarksState) {
        stateFor(bookUuid).value = state
    }

    override fun getBookmarksFlow(bookUuid: String): Flow<BookmarksState> {
        flowRequests += bookUuid
        return stateFor(bookUuid)
    }

    override suspend fun refresh(bookUuid: String) {
        refreshCalls += bookUuid
    }

    override suspend fun addBookmark(draft: BookmarkDraft): BookmarkWriteResult {
        drafts += draft
        return onAdd(draft)
    }

    override suspend fun removeBookmark(bookmark: Bookmark): BookmarkWriteResult {
        removed += bookmark
        return onRemove(bookmark)
    }
}
