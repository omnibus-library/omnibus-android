package com.omnibus.omnibus.data.wishlist

import kotlinx.coroutines.flow.Flow

/** How a book came onto the wishlist, as the server records it. */
enum class WishlistSource { Scan, Detail, Manual, Search }

/**
 * A wishlist entry for one book.
 *
 * @property addedAtMillis When the book was added, in epoch milliseconds, or null when the server did not report it.
 * @property source How the book was added. Null for a token this app does not know.
 */
data class WishlistEntry(val id: Long, val addedAtMillis: Long?, val source: WishlistSource?)

/** What the app knows about one book's wishlist entry. */
sealed interface WishlistState {
    /** No answer from the server yet. */
    data object Unknown : WishlistState

    /** The book is not on the wishlist. */
    data object Absent : WishlistState

    /** The book is on the wishlist. */
    data class Present(val entry: WishlistEntry) : WishlistState

    /** The last read failed and no answer was ever known. */
    data object Failed : WishlistState
}

/**
 * Reads and removes a book's wishlist entry. Every failure is a result, so nothing throws except cancellation.
 *
 * Each book has one shared state. Collecting it fetches the entry once, and a successful removal sets it to
 * [WishlistState.Absent] for every collector.
 */
interface WishlistRepository {

    /**
     * The shared state for [bookUuid], which replays its current value. Collecting it when nothing is known starts a
     * fetch, so the first emission is [WishlistState.Unknown] and a blank uuid fails without a request.
     */
    fun getWishlistFlow(bookUuid: String): Flow<WishlistState>

    /**
     * The state [bookUuid] holds right now, read without starting a fetch. It is [WishlistState.Unknown] when nothing is
     * known yet. A composition uses it as the first value, so a book whose answer is already known does not start from
     * Unknown.
     */
    fun cachedState(bookUuid: String): WishlistState

    /** Fetches the entry again, unless a fetch for [bookUuid] is already in flight, which it joins. */
    suspend fun refresh(bookUuid: String)

    /**
     * Removes the book from the wishlist. The state becomes [WishlistState.Absent] on success and is left alone on
     * failure. The request is not cancelled when the caller is.
     */
    suspend fun removeFromWishlist(bookUuid: String): WishlistRemovalResult
}

/** The outcome of [WishlistRepository.removeFromWishlist]. */
sealed interface WishlistRemovalResult {
    /**
     * The server removed the entry.
     *
     * @property bookDeleted True when the server also deleted the book, which happens when it has no other copy.
     */
    data class Removed(val bookDeleted: Boolean) : WishlistRemovalResult

    /** The removal failed and the state was not changed. [error] says why. */
    data class Failed(val error: WishlistError) : WishlistRemovalResult
}

/** Why a wishlist removal failed. */
sealed interface WishlistError {
    /** No server is configured, so no request was made. */
    data object NoServer : WishlistError

    /** The server could not be reached (an IOException). */
    data object Unreachable : WishlistError

    /** The server answered 401, so the session has expired. */
    data object SessionExpired : WishlistError

    /** The server answered 404, or the book uuid was blank and no request was made. */
    data object BookNotFound : WishlistError

    /** The server answered another non-2xx code. */
    data class Http(val code: Int) : WishlistError

    /** A 2xx body that does not decode, or any other unexpected failure. */
    data object UnexpectedResponse : WishlistError
}
