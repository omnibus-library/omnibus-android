package com.omnibus.omnibus.data.bookmarks

/**
 * A saved place in a book.
 *
 * @property id The server's numeric id, or null for a row not yet saved (an optimistic add).
 * @property clientId The id the app minted when the bookmark was created, or null for a row made elsewhere.
 * @property position The CFI the bookmark points at.
 * @property title The reader's title for the bookmark, or null when there is none.
 * @property chapterTitle The server's chapter title for [position], or null when unknown.
 * @property fraction How far through the book the bookmark is, 0..1; null when the server can't place it.
 * @property createdAtSeconds When the bookmark was made, in Unix seconds, or null when unknown.
 */
data class Bookmark(
    val id: Long?,
    val clientId: String?,
    val bookUuid: String,
    val position: String,
    val title: String?,
    val chapterTitle: String?,
    val fraction: Float?,
    val createdAtSeconds: Long?,
) {
    /** The id the server addresses this bookmark by on delete: the client id when there is one, else the numeric id. */
    val pathId: String? get() = clientId ?: id?.toString()

    /** Identifies a bookmark across fetches and optimistic rows. Client id first, so a saved row matches its optimistic one. */
    internal val key: String? get() = clientId ?: id?.let { "id:$it" }
}

/** What the app knows about one book's bookmarks. Every failure is a state, so nothing is thrown. */
sealed interface BookmarksState {
    /** No answer yet, or a fetch is in flight with nothing loaded. */
    data object Loading : BookmarksState

    /** The last fetch failed and no list was ever loaded. */
    data object Failed : BookmarksState

    /** The book's bookmarks in the repository's order. */
    data class Loaded(val bookmarks: List<Bookmark>) : BookmarksState
}

/** The outcome of a bookmark write. A failed write has already been rolled back in the state. */
sealed interface BookmarkWriteResult {
    /** The server accepted the create. [bookmark] is the server's row, which replaces the optimistic one. */
    data class Saved(val bookmark: Bookmark) : BookmarkWriteResult

    /** The server removed the bookmark, or had no such bookmark to remove. */
    data object Removed : BookmarkWriteResult

    /** The write did not reach the server. [error] says why. */
    data class Failed(val error: BookmarkError) : BookmarkWriteResult
}

/** Why a bookmark write failed. */
sealed interface BookmarkError {
    /** No server is configured, so no request was made. */
    data object NoServer : BookmarkError

    /** The server could not be reached (an IOException). */
    data object Unreachable : BookmarkError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : BookmarkError

    /** The server answered 404 to a create, or the book uuid was blank and no request was made. */
    data object BookNotFound : BookmarkError

    /** The server answered 400, so it rejected the bookmark's fields. */
    data object Rejected : BookmarkError

    /** The server answered another non-2xx code. */
    data class Http(val code: Int) : BookmarkError

    /** A 2xx body that does not decode, or any other unexpected failure. */
    data object UnexpectedResponse : BookmarkError
}
