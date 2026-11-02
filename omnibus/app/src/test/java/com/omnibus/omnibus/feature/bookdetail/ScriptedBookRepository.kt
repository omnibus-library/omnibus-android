package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.FakeLibraryRepository
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.testBook
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

/**
 * A [LibraryRepository] whose [getBookFlow] follows a queue of scripts, one per call, so a test can hold a load
 * in flight or emit several results from one load. Calls with no script emit [defaultResult].
 * Everything else is delegated to [delegate].
 */
class ScriptedBookRepository(
    private val delegate: FakeLibraryRepository = FakeLibraryRepository(),
) : LibraryRepository by delegate {

    /** The uuid of every [getBookFlow] call, in call order. */
    val bookRequests = mutableListOf<String>()

    /** Emitted by [getBookFlow] when no script is queued. */
    var defaultResult: BookResult = BookResult.Success(testBook(1))

    private val scripts = ArrayDeque<suspend FlowCollector<BookResult>.() -> Unit>()

    /** Queues a call that emits [result] once. */
    fun enqueue(result: BookResult) {
        scripts.addLast { emit(result) }
    }

    /** Queues a call that holds until [deferred] completes, to observe requests in flight. */
    fun enqueue(deferred: CompletableDeferred<BookResult>) {
        scripts.addLast { emit(deferred.await()) }
    }

    /** Queues a call with several emissions, such as a cached book followed by a network book. */
    fun enqueueFlow(script: suspend FlowCollector<BookResult>.() -> Unit) {
        scripts.addLast(script)
    }

    override fun getBookFlow(uuid: String): Flow<BookResult> = flow {
        bookRequests += uuid
        val script = scripts.removeFirstOrNull()
        if (script != null) script(this) else emit(defaultResult)
    }
}
