package com.omnibus.omnibus.data.audiobook.playback

import kotlinx.coroutines.CompletableDeferred

/**
 * One load's resume answers. [settleAnswer] can be deferred to hold the start until the test completes it. [cancelCount]
 * counts the abandonments.
 */
class TestPendingResume : PendingResume {

    /** The saved file id answered by [savedFileId]. */
    var savedFileId: Long? = null

    /** What [settle] answers. Completed with null by default, which starts at 0. */
    var settleAnswer: CompletableDeferred<Double?> = CompletableDeferred<Double?>().apply { complete(null) }

    /** The `(loadedFileId, durationSeconds)` of each [settle] call. */
    val settleCalls = mutableListOf<Pair<Long, Double>>()

    /** How many times [cancel] was called. */
    var cancelCount = 0

    override suspend fun savedFileId(): Long? = savedFileId

    override suspend fun settle(loadedFileId: Long, durationSeconds: Double): Double? {
        settleCalls += loadedFileId to durationSeconds
        return settleAnswer.await()
    }

    override fun cancel() {
        cancelCount++
    }
}
