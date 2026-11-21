package com.omnibus.omnibus.data.search

import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [LibrarySearchRepository] for consumers' tests. Each call is recorded, then answered from the head of
 * [answers]. A set [gate] holds the call until the test completes it. An empty queue fails the test.
 */
class TestLibrarySearchRepository : LibrarySearchRepository {

    /** Every query passed to [searchBooks], in call order. */
    val calls = mutableListOf<String>()

    /** The answers [searchBooks] gives, one per call. */
    val answers = ArrayDeque<LibrarySearchResult>()

    /** When set, [searchBooks] waits on it before taking its answer. */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun searchBooks(query: String): LibrarySearchResult {
        calls += query
        gate?.await()
        return answers.removeFirstOrNull() ?: unscripted("searchBooks")
    }
}
