package com.omnibus.omnibus.feature.bookdetail.more.shelves

import com.omnibus.omnibus.data.shelves.BookShelves
import com.omnibus.omnibus.data.shelves.BookShelvesResult
import com.omnibus.omnibus.data.shelves.ShelfRulesResult
import com.omnibus.omnibus.data.shelves.ShelfWriteResult
import com.omnibus.omnibus.data.shelves.ShelvesRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * A [ShelvesRepository] whose answers a test scripts. Reads and writes return the `default*` result unless a
 * hold is queued for them: a held call suspends until its deferred completes. Every call is recorded.
 */
class ScriptedShelvesRepository : ShelvesRepository {

    /** The uuid of every [getBookShelves] call, in call order. */
    val bookShelvesRequests = mutableListOf<String>()

    /** The shelf id of every [getShelfRules] call, in call order. */
    val rulesRequests = mutableListOf<Long>()

    /** Every write, in call order. */
    val writes = mutableListOf<ShelfWrite>()

    /** Returned by [getBookShelves] when no hold is queued. */
    var bookShelves: BookShelvesResult = BookShelvesResult.Success(BookShelves(emptyList(), emptySet(), emptySet()))

    /** Returned by [getShelfRules] for a shelf with no entry in [rulesFor]. */
    var rules: ShelfRulesResult = ShelfRulesResult.NoServer

    /** Rules by smart shelf id. */
    val rulesFor = mutableMapOf<Long, ShelfRulesResult>()

    /** Returned by [addBook] and [removeBook] when no hold is queued for the shelf. */
    var writeResult: ShelfWriteResult = ShelfWriteResult.Success

    private val bookShelvesHolds = ArrayDeque<CompletableDeferred<BookShelvesResult>>()
    private val rulesHolds = mutableMapOf<Long, CompletableDeferred<ShelfRulesResult>>()
    private val writeHolds = mutableMapOf<Long, CompletableDeferred<ShelfWriteResult>>()

    /** Queues the next [getBookShelves] call to wait for the returned deferred. */
    fun holdBookShelves(): CompletableDeferred<BookShelvesResult> =
        CompletableDeferred<BookShelvesResult>().also { bookShelvesHolds.addLast(it) }

    /** Makes the next rules read of [shelfId] wait for the returned deferred. */
    fun holdRules(shelfId: Long): CompletableDeferred<ShelfRulesResult> =
        CompletableDeferred<ShelfRulesResult>().also { rulesHolds[shelfId] = it }

    /** Makes the next write to [shelfId] wait for the returned deferred. */
    fun holdWrite(shelfId: Long): CompletableDeferred<ShelfWriteResult> =
        CompletableDeferred<ShelfWriteResult>().also { writeHolds[shelfId] = it }

    override suspend fun getBookShelves(bookUuid: String): BookShelvesResult {
        bookShelvesRequests += bookUuid
        val hold = bookShelvesHolds.removeFirstOrNull()
        return hold?.await() ?: bookShelves
    }

    override suspend fun getShelfRules(shelfId: Long): ShelfRulesResult {
        rulesRequests += shelfId
        rulesHolds.remove(shelfId)?.let { return it.await() }
        return rulesFor[shelfId] ?: rules
    }

    override suspend fun addBook(shelfId: Long, bookUuid: String): ShelfWriteResult {
        writes += ShelfWrite(add = true, shelfId = shelfId, bookUuid = bookUuid)
        return writeHolds.remove(shelfId)?.await() ?: writeResult
    }

    override suspend fun removeBook(shelfId: Long, bookUuid: String): ShelfWriteResult {
        writes += ShelfWrite(add = false, shelfId = shelfId, bookUuid = bookUuid)
        return writeHolds.remove(shelfId)?.await() ?: writeResult
    }
}

/** One write the ViewModel sent. */
data class ShelfWrite(val add: Boolean, val shelfId: Long, val bookUuid: String)
