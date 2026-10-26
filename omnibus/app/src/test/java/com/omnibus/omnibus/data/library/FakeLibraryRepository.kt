package com.omnibus.omnibus.data.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory [LibraryRepository] for tests. Each [booksPage] call takes the next queued script, or
 * emits [defaultResult] when none is queued, and every call is recorded in [pageRequests].
 */
class FakeLibraryRepository : LibraryRepository {

    /** The arguments of one [booksPage] call, in call order. */
    data class PageRequest(val query: BooksPageQuery, val cursor: String?)

    val pageRequests = mutableListOf<PageRequest>()

    private val scripts = ArrayDeque<suspend FlowCollector<LibraryPageResult>.() -> Unit>()

    /** Emitted by [booksPage] when no script is queued. */
    var defaultResult: LibraryPageResult = LibraryPageResult.Success(BooksPage(emptyList(), null, null, null))

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

    override fun booksPage(query: BooksPageQuery, cursor: String?): Flow<LibraryPageResult> = flow {
        pageRequests += PageRequest(query, cursor)
        val script = scripts.removeFirstOrNull()
        if (script != null) script(this) else emit(defaultResult)
    }

    override fun recentProgress(): Flow<RecentProgressResult> = flowOf(RecentProgressResult.Success(emptyList()))

    override fun shelfPreviews(): Flow<ShelfPreviewsResult> = flowOf(ShelfPreviewsResult.Success(emptyList()))
}

/** A successful page of books with the given ids, built with [testBook]. */
fun page(vararg ids: Long, cursor: String? = null): LibraryPageResult.Success =
    LibraryPageResult.Success(BooksPage(ids.map { testBook(it) }, cursor, null, null))
