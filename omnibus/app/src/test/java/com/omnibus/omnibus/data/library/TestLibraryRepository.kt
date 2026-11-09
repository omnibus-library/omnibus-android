package com.omnibus.omnibus.data.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * In-memory [LibraryRepository] for tests. Each [getBooksPageFlow] call takes the next queued script, or
 * emits [defaultResult] when none is queued, and every call is recorded in [pageRequests]. A read nothing scripted never answers.
 */
class TestLibraryRepository : LibraryRepository {

    /** The arguments of one [getBooksPageFlow] call, in call order. */
    data class PageRequest(val query: BooksPageQuery, val cursor: String?)

    val pageRequests = mutableListOf<PageRequest>()

    private val scripts = ArrayDeque<suspend FlowCollector<LibraryPageResult>.() -> Unit>()

    /** Emitted by [getBooksPageFlow] when no script is queued. */
    var defaultResult: LibraryPageResult? = null

    /** Queues a call that emits [result] once. */
    fun enqueue(result: LibraryPageResult) {
        scripts.addLast { emit(result) }
    }

    /** Queues a call that holds until [deferred] completes, to observe requests in flight. */
    fun enqueue(deferred: CompletableDeferred<LibraryPageResult>) {
        scripts.addLast { emit(deferred.await()) }
    }

    /** Queues a call with several emissions, such as a cached page followed by a network page. */
    fun enqueueFlow(script: suspend FlowCollector<LibraryPageResult>.() -> Unit) {
        scripts.addLast(script)
    }

    override fun getBooksPageFlow(query: BooksPageQuery, cursor: String?): Flow<LibraryPageResult> = flow {
        pageRequests += PageRequest(query, cursor)
        val script = scripts.removeFirstOrNull()
        if (script != null) script(this) else emit(defaultResult ?: awaitCancellation())
    }

    /** The number of [getRecentProgressFlow] calls, in call order. */
    var recentProgressRequests = 0

    private val recentScripts = ArrayDeque<suspend FlowCollector<RecentProgressResult>.() -> Unit>()

    /** Emitted by [getRecentProgressFlow] when no script is queued. */
    var defaultRecentProgress: RecentProgressResult? = null

    /** Queues a [getRecentProgressFlow] call that emits [result] once. */
    fun enqueueRecent(result: RecentProgressResult) {
        recentScripts.addLast { emit(result) }
    }

    /** Queues a [getRecentProgressFlow] call that holds until [deferred] completes. */
    fun enqueueRecent(deferred: CompletableDeferred<RecentProgressResult>) {
        recentScripts.addLast { emit(deferred.await()) }
    }

    override fun getRecentProgressFlow(): Flow<RecentProgressResult> = flow {
        recentProgressRequests++
        val script = recentScripts.removeFirstOrNull()
        if (script != null) script(this) else emit(defaultRecentProgress ?: awaitCancellation())
    }

    override fun getShelfPreviewsFlow(): Flow<ShelfPreviewsResult> = flow { awaitCancellation() }

    /** The uuid of every [getBookFlow] call, in call order. */
    val bookRequests = mutableListOf<String>()

    /** Emitted by [getBookFlow] unless [pendingBook] is set. */
    var bookResult: BookResult? = null

    /** When set, [getBookFlow] holds until this completes, to observe the loading state. */
    var pendingBook: CompletableDeferred<BookResult>? = null

    override fun getBookFlow(uuid: String): Flow<BookResult> = flow {
        bookRequests += uuid
        emit(pendingBook?.await() ?: bookResult ?: awaitCancellation())
    }

    /** The series id of every [getSeriesBooksFlow] call, in call order. */
    val seriesRequests = mutableListOf<Long>()

    private val seriesScripts = mutableMapOf<Long, ArrayDeque<suspend FlowCollector<SeriesBooksResult>.() -> Unit>>()

    /** Answers the next [getSeriesBooksFlow] call for [seriesId] with [result]. */
    fun enqueueSeries(seriesId: Long, result: SeriesBooksResult) {
        seriesScripts.getOrPut(seriesId) { ArrayDeque() }.addLast { emit(result) }
    }

    /** Queues a [getSeriesBooksFlow] call for [seriesId] that holds until [deferred] completes. */
    fun enqueueSeries(seriesId: Long, deferred: CompletableDeferred<SeriesBooksResult>) {
        seriesScripts.getOrPut(seriesId) { ArrayDeque() }.addLast { emit(deferred.await()) }
    }

    /** Answers a [getSeriesBooksFlow] call that has no queued script, by series id. Unset, such a call never answers. */
    var defaultSeriesResult: ((Long) -> SeriesBooksResult)? = null

    override fun getSeriesBooksFlow(seriesId: Long): Flow<SeriesBooksResult> = flow {
        seriesRequests += seriesId
        val script = seriesScripts[seriesId]?.removeFirstOrNull()
        if (script != null) script(this) else emit(defaultSeriesResult?.invoke(seriesId) ?: awaitCancellation())
    }
}

/** A successful page of random books with the given ids. */
fun page(vararg ids: Long, cursor: String? = null): LibraryPageResult.Success =
    LibraryPageResult.Success(BooksPage(ids.map { randomBook().copy(id = it) }, cursor, null, null))
