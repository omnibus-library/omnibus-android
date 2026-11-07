package com.omnibus.omnibus.data.audiobook.playback

import kotlinx.coroutines.CompletableDeferred

/** In-memory [PlaybackRateStore]. [answer] can be deferred to hold a rate read until the test completes it. */
class TestPlaybackRateStore : PlaybackRateStore {

    /** What [rate] answers. Completed with null by default, which means the rate is unknown. */
    var answer: CompletableDeferred<Double?> = CompletableDeferred<Double?>().apply { complete(null) }

    /** The book uuid of each [rate] call, in call order. */
    val rateCalls = mutableListOf<String>()

    /** The `(bookUuid, rate)` of each [save] call, in call order. */
    val saves = mutableListOf<Pair<String, Double>>()

    override suspend fun rate(bookUuid: String): Double? {
        rateCalls += bookUuid
        return answer.await()
    }

    override suspend fun save(bookUuid: String, rate: Double) {
        saves += bookUuid to rate
    }
}
