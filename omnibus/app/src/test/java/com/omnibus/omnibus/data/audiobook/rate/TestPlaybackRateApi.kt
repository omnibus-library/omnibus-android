package com.omnibus.omnibus.data.audiobook.rate

import com.omnibus.omnibus.data.audiobook.rate.remote.PlaybackRateApi
import com.omnibus.omnibus.data.audiobook.rate.remote.PutOutcome
import com.omnibus.omnibus.data.audiobook.rate.remote.RateFetch
import kotlinx.coroutines.CompletableDeferred

/**
 * Scripted [PlaybackRateApi] for the store tests. A fetch answers the book's entry in [fetchAnswers] when set, else
 * [defaultFetch]. Each put takes the next entry of [putAnswers] when there is one, else [defaultPut]. Each put is
 * logged. [failWith] is thrown by every call when set.
 */
class TestPlaybackRateApi : PlaybackRateApi {

    /** Held or completed answers per book, for reads that must wait. */
    val fetchAnswers = mutableMapOf<String, CompletableDeferred<RateFetch>>()

    /** The answer of a fetch with no entry in [fetchAnswers]. */
    var defaultFetch: RateFetch = RateFetch.Unset

    /** Held or completed answers for puts, taken in call order. */
    val putAnswers = ArrayDeque<CompletableDeferred<PutOutcome>>()

    /** The answer of a put when [putAnswers] is empty. */
    var defaultPut: PutOutcome = PutOutcome.Sent

    /** The `(bookUuid, rate)` of each put, in call order. */
    val puts = mutableListOf<Pair<String, Double>>()

    /** Thrown by every call when set. */
    var failWith: Exception? = null

    override suspend fun fetch(bookUuid: String): RateFetch {
        failWith?.let { throw it }
        return fetchAnswers[bookUuid]?.await() ?: defaultFetch
    }

    override suspend fun put(bookUuid: String, rate: Double): PutOutcome {
        failWith?.let { throw it }
        puts += bookUuid to rate
        val held = putAnswers.removeFirstOrNull()
        return held?.await() ?: defaultPut
    }
}
