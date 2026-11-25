package com.omnibus.omnibus.data.series

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/**
 * In-memory [SeriesRepository] for tests. A call takes its result from [results] *before* it
 * waits on the head of [gates], so a gated call still returns the result it queued. A call with no result never answers.
 * [getSeriesDetails] works the same way with [detailsResults], [detailsGates] and [detailsCalls].
 */
class TestSeriesRepository : SeriesRepository {

    /** The next call's result, in call order. */
    val results = ArrayDeque<SeriesResult>()

    /** When non-empty, the next call waits on the head after taking its result. */
    val gates = ArrayDeque<CompletableDeferred<Unit>>()

    /** The series id of every call, in call order. */
    val calls = mutableListOf<Long>()

    /** The next [getSeriesDetails] call's result, in call order. */
    val detailsResults = ArrayDeque<SeriesDetailsResult>()

    /** When non-empty, the next [getSeriesDetails] call waits on the head after taking its result. */
    val detailsGates = ArrayDeque<CompletableDeferred<Unit>>()

    /** The series id of every [getSeriesDetails] call, in call order. */
    val detailsCalls = mutableListOf<Long>()

    override suspend fun getSeries(seriesId: Long): SeriesResult {
        calls += seriesId
        val result = results.removeFirstOrNull() ?: awaitCancellation()
        gates.removeFirstOrNull()?.await()
        return result
    }

    override suspend fun getSeriesDetails(seriesId: Long): SeriesDetailsResult {
        detailsCalls += seriesId
        val result = detailsResults.removeFirstOrNull() ?: awaitCancellation()
        detailsGates.removeFirstOrNull()?.await()
        return result
    }
}
