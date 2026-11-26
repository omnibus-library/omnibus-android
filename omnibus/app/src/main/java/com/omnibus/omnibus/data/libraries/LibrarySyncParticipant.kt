package com.omnibus.omnibus.data.libraries

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
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
     * Set when a tracked write fails, and cleared by [awaitAll]. A write that fails before [awaitAll] takes its
     * snapshot has already left [outcomes], so without this its failure would read as a success.
     */
    private val failedSinceLastWait = AtomicBoolean(false)

    private fun finish(outcome: CompletableDeferred<Boolean>, succeeded: Boolean) {
        if (outcome.complete(succeeded) && !succeeded) failedSinceLastWait.set(true)
        outcomes -= outcome
    }

    /**
     * Tracks [write] until it completes. It counts as succeeded when it completes normally and [succeeded] holds for
     * its result. Returns [write].
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> track(write: Deferred<T>, succeeded: (T) -> Boolean): Deferred<T> {
        val outcome = CompletableDeferred<Boolean>()
        outcomes += outcome
        write.invokeOnCompletion { cause ->
            finish(outcome, cause == null && runCatching { succeeded(write.getCompleted()) }.getOrDefault(false))
        }
        return write
    }

    /** Runs [block] as a tracked write in the caller's coroutine, and returns its result. */
    suspend fun <T> tracking(succeeded: (T) -> Boolean, block: suspend () -> T): T {
        val outcome = CompletableDeferred<Boolean>()
        outcomes += outcome
        try {
            return block().also { finish(outcome, succeeded(it)) }
        } finally {
            finish(outcome, false) // a no-op when already finished; covers a throw and cancellation
        }
    }

    /** True while any tracked write has not finished. */
    fun hasPending(): Boolean = outcomes.isNotEmpty()

    /**
     * Waits for the writes in flight now. True when every one succeeded and no tracked write has failed since the
     * last wait, or when none was in flight and none failed.
     */
    suspend fun awaitAll(): Boolean {
        val allSucceeded = outcomes.toList().map { it.await() }.all { it }
        return !failedSinceLastWait.getAndSet(false) && allSucceeded
    }
}
