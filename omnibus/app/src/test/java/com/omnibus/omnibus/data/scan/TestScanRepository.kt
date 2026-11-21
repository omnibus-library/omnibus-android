package com.omnibus.omnibus.data.scan

import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [ScanRepository] for consumers' tests. Each call is recorded, then answered from the head of its queue. A
 * set gate holds the call until the test completes it, so a write can be left in flight. An empty queue fails the test.
 */
class TestScanRepository : ScanRepository {

    /** One recorded [checkIn] call. */
    data class CheckInCall(val bookUuid: String, val isbn: String?, val note: String?)

    /** Every ISBN passed to [resolve], in call order. */
    val resolveCalls = mutableListOf<String>()

    /** The answers [resolve] gives, one per call. */
    val resolveAnswers = ArrayDeque<ScanResult<ScanOutcome>>()

    /** When set, [resolve] waits on it before taking its answer. */
    var resolveGate: CompletableDeferred<Unit>? = null

    /** Every call to [checkIn], in call order. */
    val checkInCalls = mutableListOf<CheckInCall>()

    /** The answers [checkIn] gives, one per call. */
    val checkInAnswers = ArrayDeque<ScanResult<String>>()

    /** When set, [checkIn] waits on it before taking its answer. */
    var checkInGate: CompletableDeferred<Unit>? = null

    /** Every call to [addPhysicalOnly], in call order. */
    val physicalOnlyCalls = mutableListOf<ProviderRecord>()

    /** The answers [addPhysicalOnly] gives, one per call. */
    val physicalOnlyAnswers = ArrayDeque<ScanResult<String>>()

    /** When set, [addPhysicalOnly] waits on it before taking its answer. */
    var physicalOnlyGate: CompletableDeferred<Unit>? = null

    /** One recorded [addToWishlist] call. */
    data class WishlistCall(val record: ProviderRecord, val source: WishlistSource)

    /** Every call to [addToWishlist], in call order. */
    val wishlistCalls = mutableListOf<WishlistCall>()

    /** The answers [addToWishlist] gives, one per call. */
    val wishlistAnswers = ArrayDeque<ScanResult<String>>()

    /** When set, [addToWishlist] waits on it before taking its answer. */
    var wishlistGate: CompletableDeferred<Unit>? = null

    /** Every query passed to [searchByTitle], in call order. */
    val searchCalls = mutableListOf<String>()

    /** The answers [searchByTitle] gives, one per call. */
    val searchAnswers = ArrayDeque<ScanResult<List<ProviderRecord>>>()

    /** When set, [searchByTitle] waits on it before taking its answer. */
    var searchGate: CompletableDeferred<Unit>? = null

    /** Every record passed to [resolveRecord], in call order. */
    val resolveRecordCalls = mutableListOf<ProviderRecord>()

    /** The answers [resolveRecord] gives, one per call. */
    val resolveRecordAnswers = ArrayDeque<ScanResult<ScanOutcome>>()

    /** When set, [resolveRecord] waits on it before taking its answer. */
    var resolveRecordGate: CompletableDeferred<Unit>? = null

    override suspend fun resolve(isbn: String): ScanResult<ScanOutcome> {
        resolveCalls += isbn
        resolveGate?.await()
        return resolveAnswers.removeFirstOrNull() ?: unscripted("resolve")
    }

    override suspend fun checkIn(bookUuid: String, isbn: String?, note: String?): ScanResult<String> {
        checkInCalls += CheckInCall(bookUuid, isbn, note)
        checkInGate?.await()
        return checkInAnswers.removeFirstOrNull() ?: unscripted("checkIn")
    }

    override suspend fun addPhysicalOnly(record: ProviderRecord): ScanResult<String> {
        physicalOnlyCalls += record
        physicalOnlyGate?.await()
        return physicalOnlyAnswers.removeFirstOrNull() ?: unscripted("addPhysicalOnly")
    }

    override suspend fun addToWishlist(record: ProviderRecord, source: WishlistSource): ScanResult<String> {
        wishlistCalls += WishlistCall(record, source)
        wishlistGate?.await()
        return wishlistAnswers.removeFirstOrNull() ?: unscripted("addToWishlist")
    }

    override suspend fun searchByTitle(query: String): ScanResult<List<ProviderRecord>> {
        searchCalls += query
        searchGate?.await()
        return searchAnswers.removeFirstOrNull() ?: unscripted("searchByTitle")
    }

    override suspend fun resolveRecord(record: ProviderRecord): ScanResult<ScanOutcome> {
        resolveRecordCalls += record
        resolveRecordGate?.await()
        return resolveRecordAnswers.removeFirstOrNull() ?: unscripted("resolveRecord")
    }
}
