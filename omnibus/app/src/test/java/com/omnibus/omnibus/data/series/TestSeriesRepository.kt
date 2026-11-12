package com.omnibus.omnibus.data.series

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/**
 * In-memory [SeriesRepository] for tests. A call takes its result from [results] *before* it
 * waits on the head of [gates], so a gated call still returns the result it queued. A call with no result never answers.
 */
class TestSeriesRepository : SeriesRepository {

    /** The next call's result, in call order. */
    val results = ArrayDeque<SeriesResult>()

    /** When non-empty, the next call waits on the head after taking its result. */
    val gates = ArrayDeque<CompletableDeferred<Unit>>()

    /** The series id of every call, in call order. */
    val calls = mutableListOf<Long>()

    override suspend fun getSeries(seriesId: Long): SeriesResult {
        calls += seriesId
        val result = results.removeFirstOrNull() ?: awaitCancellation()
        gates.removeFirstOrNull()?.await()
        return result
    }
}
