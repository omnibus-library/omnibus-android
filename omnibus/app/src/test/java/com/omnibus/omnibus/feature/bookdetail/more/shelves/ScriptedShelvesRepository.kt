package com.omnibus.omnibus.feature.bookdetail.more.shelves

import com.omnibus.omnibus.data.shelves.BookShelves
import com.omnibus.omnibus.data.shelves.BookShelvesResult
import com.omnibus.omnibus.data.shelves.FilterClause
import com.omnibus.omnibus.data.shelves.RuleMatchCount
import com.omnibus.omnibus.data.shelves.RulePreviewResult
import com.omnibus.omnibus.data.shelves.ShelfBooksResult
import com.omnibus.omnibus.data.shelves.ShelfDetailResult
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRulesResult
import com.omnibus.omnibus.data.shelves.ShelfUpdate
import com.omnibus.omnibus.data.shelves.ShelfWriteResult
import com.omnibus.omnibus.data.shelves.ShelvesRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

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

    /** The shelf id of every [getShelfFlow] call, in call order. */
    val shelfRequests = mutableListOf<Long>()

    /** Emissions of [getShelfFlow] by shelf id. A shelf with no entry emits [ShelfDetailResult.NoServer]. */
    val shelfEmissions = mutableMapOf<Long, List<ShelfDetailResult>>()

    /** Returned by [getShelf], the network-only read the editor makes. */
    var freshShelf: ShelfDetailResult = ShelfDetailResult.NoServer

    /** The shelf id and clauses of every [getShelfBooksFlow] call, in call order. */
    val booksRequests = mutableListOf<Pair<Long, List<FilterClause>>>()

    /** Returned by [getShelfBooksFlow] when no emission script is queued. */
    var books: ShelfBooksResult = ShelfBooksResult.NoServer

    /** Every update, in call order. */
    val updates = mutableListOf<Pair<Long, ShelfUpdate>>()

    /** Every delete, in call order. */
    val deletes = mutableListOf<Long>()

    /** Every preview, in call order. */
    val previews = mutableListOf<Pair<ShelfMatchMode, List<ShelfRule>>>()

    /** Returned by [updateShelf] when no hold is queued. */
    var updateResult: ShelfWriteResult = ShelfWriteResult.Success

    /** Returned by [deleteShelf] when no hold is queued. */
    var deleteResult: ShelfWriteResult = ShelfWriteResult.Success

    /** Returned by [previewRules] when no hold is queued. */
    var previewResult: RulePreviewResult = RulePreviewResult.Success(RuleMatchCount(matched = 0, total = 0))

    private val bookShelvesHolds = ArrayDeque<CompletableDeferred<BookShelvesResult>>()
    private val rulesHolds = mutableMapOf<Long, CompletableDeferred<ShelfRulesResult>>()
    private val writeHolds = mutableMapOf<Long, CompletableDeferred<ShelfWriteResult>>()
    private val bookEmissions = ArrayDeque<suspend FlowCollector<ShelfBooksResult>.() -> Unit>()
    private var updateHold: CompletableDeferred<ShelfWriteResult>? = null
    private var deleteHold: CompletableDeferred<ShelfWriteResult>? = null
    private var previewHold: CompletableDeferred<RulePreviewResult>? = null

    /** Queues the next [getBookShelves] call to wait for the returned deferred. */
    fun holdBookShelves(): CompletableDeferred<BookShelvesResult> =
        CompletableDeferred<BookShelvesResult>().also { bookShelvesHolds.addLast(it) }

    /** Makes the next rules read of [shelfId] wait for the returned deferred. */
    fun holdRules(shelfId: Long): CompletableDeferred<ShelfRulesResult> =
        CompletableDeferred<ShelfRulesResult>().also { rulesHolds[shelfId] = it }

    /** Makes the next write to [shelfId] wait for the returned deferred. */
    fun holdWrite(shelfId: Long): CompletableDeferred<ShelfWriteResult> =
        CompletableDeferred<ShelfWriteResult>().also { writeHolds[shelfId] = it }

    /** Queues the next [getShelfBooksFlow] call to run [script] instead of emitting [books]. */
    fun enqueueBooksFlow(script: suspend FlowCollector<ShelfBooksResult>.() -> Unit) {
        bookEmissions.addLast(script)
    }

    /** Queues the next [getShelfBooksFlow] call to emit [result] once. */
    fun enqueueBooks(result: ShelfBooksResult) = enqueueBooksFlow { emit(result) }

    /** Queues the next [getShelfBooksFlow] call to emit whatever [deferred] completes with. */
    fun enqueueBooks(deferred: CompletableDeferred<ShelfBooksResult>) = enqueueBooksFlow { emit(deferred.await()) }

    /** Makes the next [updateShelf] wait for the returned deferred. */
    fun holdUpdate(): CompletableDeferred<ShelfWriteResult> =
        CompletableDeferred<ShelfWriteResult>().also { updateHold = it }

    /** Makes the next [deleteShelf] wait for the returned deferred. */
    fun holdDelete(): CompletableDeferred<ShelfWriteResult> =
        CompletableDeferred<ShelfWriteResult>().also { deleteHold = it }

    /** Makes the next [previewRules] wait for the returned deferred. */
    fun holdPreview(): CompletableDeferred<RulePreviewResult> =
        CompletableDeferred<RulePreviewResult>().also { previewHold = it }

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

    override fun getShelfFlow(shelfId: Long): Flow<ShelfDetailResult> = flow {
        shelfRequests += shelfId
        for (result in shelfEmissions[shelfId] ?: listOf(ShelfDetailResult.NoServer)) emit(result)
    }

    override suspend fun getShelf(shelfId: Long): ShelfDetailResult {
        shelfRequests += shelfId
        return freshShelf
    }

    override fun getShelfBooksFlow(shelfId: Long, clauses: List<FilterClause>): Flow<ShelfBooksResult> = flow {
        booksRequests += shelfId to clauses
        val script = bookEmissions.removeFirstOrNull()
        if (script != null) script() else emit(books)
    }

    override suspend fun updateShelf(shelfId: Long, update: ShelfUpdate): ShelfWriteResult {
        updates += shelfId to update
        return updateHold?.also { updateHold = null }?.await() ?: updateResult
    }

    override suspend fun deleteShelf(shelfId: Long): ShelfWriteResult {
        deletes += shelfId
        return deleteHold?.also { deleteHold = null }?.await() ?: deleteResult
    }

    override suspend fun previewRules(matchMode: ShelfMatchMode, rules: List<ShelfRule>): RulePreviewResult {
        previews += matchMode to rules
        return previewHold?.also { previewHold = null }?.await() ?: previewResult
    }
}

/** One write the ViewModel sent. */
data class ShelfWrite(val add: Boolean, val shelfId: Long, val bookUuid: String)
