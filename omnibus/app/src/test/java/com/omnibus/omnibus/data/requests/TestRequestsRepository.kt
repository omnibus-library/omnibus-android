package com.omnibus.omnibus.data.requests

import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/**
 * In-memory [RequestsRepository] for consumers' tests. Answers come from a script, first in first out per function, and
 * every call is logged with its arguments. A call can be held on a gate, so a test can hold a read while a write lands.
 *
 * A read nothing scripted never answers, and a write nothing scripted fails the test.
 */
class TestRequestsRepository : RequestsRepository {

    /** One call: the function's name and its arguments. */
    data class Call(val function: String, val args: List<Any?>)

    /** Every call, in call order. It is logged before the call waits on its gate. */
    val calls = mutableListOf<Call>()

    /** The calls to [function], in call order. */
    fun callsTo(function: String): List<Call> = calls.filter { it.function == function }

    private class Answer(val result: RequestsResult<*>, val gate: CompletableDeferred<Unit>?)

    private val queued = mutableMapOf<String, ArrayDeque<Answer>>()
    private val sticky = mutableMapOf<String, Answer>()

    /**
     * Queues the next answer of [function]. With [forFirstArg], only a call whose first argument is that value takes
     * it, such as one request's id. [gate] holds that call until it completes.
     */
    fun answer(function: String, result: RequestsResult<*>, gate: CompletableDeferred<Unit>? = null, forFirstArg: Any? = null) {
        queued.getOrPut(key(function, forFirstArg)) { ArrayDeque() }.addLast(Answer(result, gate))
    }

    /** Answers every call of [function] that has no queued answer, for [forFirstArg] only when it is given. */
    fun answerAlways(function: String, result: RequestsResult<*>, forFirstArg: Any? = null) {
        sticky[key(function, forFirstArg)] = Answer(result, null)
    }

    private fun key(function: String, firstArg: Any?) = if (firstArg == null) function else "$function:$firstArg"

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> reply(function: String, isRead: Boolean, vararg args: Any?): RequestsResult<T> {
        calls += Call(function, args.toList())
        val keys = listOf(key(function, args.firstOrNull()), function)
        val answer = keys.firstNotNullOfOrNull { queued[it]?.removeFirstOrNull() }
            ?: keys.firstNotNullOfOrNull { sticky[it] }
            ?: if (isRead) awaitCancellation() else unscripted(function)
        answer.gate?.await()
        return answer.result as RequestsResult<T>
    }

    override suspend fun list() = reply<List<RequestListing>>("list", true)

    override suspend fun bookList(uuid: String) = reply<List<RequestListing>>("bookList", true, uuid)

    override suspend fun request(id: Long) = reply<BookRequest>("request", true, id)

    override suspend fun wanters(uuid: String) = reply<List<Wanter>>("wanters", true, uuid)

    override suspend fun start(uuid: String, kind: RequestKind) = reply<BookRequest>("start", false, uuid, kind)

    override suspend fun startFromRecord(book: ProviderBook) = reply<BookRequest>("startFromRecord", false, book)

    override suspend fun choose(id: Long, resultId: Long) = reply<BookRequest>("choose", false, id, resultId)

    override suspend fun swap(id: Long) = reply<BookRequest>("swap", false, id)

    override suspend fun retry(id: Long) = reply<BookRequest>("retry", false, id)

    override suspend fun cancel(id: Long) = reply<BookRequest>("cancel", false, id)

    override suspend fun undoCancel(id: Long) = reply<BookRequest>("undoCancel", false, id)

    override suspend fun takeCover(id: Long) = reply<BookRequest>("takeCover", false, id)

    override suspend fun keepCover(id: Long) = reply<BookRequest>("keepCover", false, id)

    override suspend fun searchLibrary(query: String) = reply<List<LibraryHit>>("searchLibrary", true, query)

    override suspend fun searchProviders(query: String) = reply<List<ProviderBook>>("searchProviders", true, query)

    override suspend fun resolve(book: ProviderBook) = reply<ScanMatch>("resolve", true, book)
}
