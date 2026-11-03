package com.omnibus.omnibus.data.household

import android.graphics.Bitmap

/**
 * Another reader's time in one book. The server reports how long and how lately, never how far, so there is no
 * read status or position here.
 *
 * @property userId The reader's user id, which addresses their avatar.
 * @property seconds Seconds they read and listened in the book.
 * @property sessions How many times they sat down with it for a minute or more.
 * @property lastSessionAtMillis When their latest sitting ended, in epoch milliseconds, or null when the server gave none.
 */
data class HouseholdReader(
    val userId: Long,
    val name: String,
    val hasAvatar: Boolean,
    val seconds: Long,
    val sessions: Long,
    val lastSessionAtMillis: Long?,
)

/** The outcome of [HouseholdRepository.getHousehold]. */
sealed interface HouseholdResult {
    /** The readers who share their stats and have spent time in the book, newest sitting first. May be empty. */
    data class Success(val readers: List<HouseholdReader>) : HouseholdResult

    /** The book is unknown to the server (404) or no server is saved. */
    data object NotFound : HouseholdResult

    /** The request failed: the server was unreachable, answered an error, or sent a body that does not decode. */
    data object Failed : HouseholdResult
}

/** Reads who else in the household has been reading a book, and their avatars. Nothing throws except cancellation. */
interface HouseholdRepository {

    /** One read of `GET api/books/{uuid}/household`. */
    suspend fun getHousehold(bookUuid: String): HouseholdResult

    /**
     * The avatar of [userId] decoded to at most [maxEdgePx] pixels on its long edge, or null when the reader has
     * none or it can't be loaded. Decoded avatars are kept in memory.
     */
    suspend fun getAvatar(userId: Long, maxEdgePx: Int): Bitmap?
}
