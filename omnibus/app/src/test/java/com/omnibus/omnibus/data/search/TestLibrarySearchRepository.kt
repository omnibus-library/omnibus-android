package com.omnibus.omnibus.data.search

import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [LibrarySearchRepository] for consumers' tests. Each call is recorded, then answered from the head of
 * [answers]. A set [gate] holds the call until the test completes it. An empty queue fails the test.
 * [searchLibrary] has its own queue, [libraryAnswers], so the two searches can be scripted separately.
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

    /** Every query passed to [searchLibrary], in call order. */
    val libraryCalls = mutableListOf<String>()

    /** One pending answer per [searchLibrary] call, taken when the call starts. Complete them in any order to model late answers. */
    val libraryAnswers = ArrayDeque<CompletableDeferred<PaletteSearchResult>>()

    /** Queues an answer that is already complete. */
    fun answerLibrary(result: PaletteSearchResult) {
        libraryAnswers.addLast(CompletableDeferred(result))
    }

    /** Queues an answer the test completes later, and returns it. */
    fun holdLibrary(): CompletableDeferred<PaletteSearchResult> =
        CompletableDeferred<PaletteSearchResult>().also { libraryAnswers.addLast(it) }

    override suspend fun searchLibrary(query: String): PaletteSearchResult {
        libraryCalls += query
        val answer = libraryAnswers.removeFirstOrNull() ?: unscripted("searchLibrary")
        return answer.await()
    }
}
