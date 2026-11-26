package com.omnibus.omnibus.data.libraries

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** One kind of data a switch must send before it leaves the active library (C10). */
interface LibrarySyncParticipant {

    /** True while this participant holds a change that has not been sent. */
    suspend fun hasPending(): Boolean

    /** Sends what is pending. True only when everything was sent and accepted. */
    suspend fun sendPending(): Boolean
}

/**
 * The writes a repository has started and not yet finished, so a participant can wait for them. Writes start on
 * several threads, so this is thread-safe.
 */
class InFlightWrites {

    private val outcomes = ConcurrentHashMap.newKeySet<CompletableDeferred<Boolean>>()

    /**
     * Tracks [write] until it completes. It counts as succeeded when it completes normally and [succeeded] holds for
     * its result. Returns [write].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> track(write: Deferred<T>, succeeded: (T) -> Boolean): Deferred<T> {
        val outcome = CompletableDeferred<Boolean>()
        outcomes += outcome
        write.invokeOnCompletion { cause ->
            outcome.complete(cause == null && runCatching { succeeded(write.getCompleted()) }.getOrDefault(false))
            outcomes -= outcome
        }
        return write
    }

    /** Runs [block] as a tracked write in the caller's coroutine, and returns its result. */
    suspend fun <T> tracking(succeeded: (T) -> Boolean, block: suspend () -> T): T {
        val outcome = CompletableDeferred<Boolean>()
        outcomes += outcome
        try {
            return block().also { outcome.complete(succeeded(it)) }
        } finally {
            outcome.complete(false) // a no-op when already completed; covers a throw and cancellation
            outcomes -= outcome
        }
    }

    /** True while any tracked write has not finished. */
    fun hasPending(): Boolean = outcomes.isNotEmpty()

    /** Waits for the writes in flight now. True when every one succeeded, or when none was in flight. */
    suspend fun awaitAll(): Boolean = outcomes.toList().map { it.await() }.all { it }
}
