package com.omnibus.omnibus.data.audiobook.playback

import kotlinx.coroutines.CompletableDeferred

/** Records each [begin] and hands out a [FakePendingResume] for it. */
class FakeResumeStartResolver : ResumeStartResolver {

    /** The arguments of each [begin] call, in call order: book uuid, explicit file id, explicit position. */
    val begins = mutableListOf<Triple<String, Long?, Double?>>()

    /** The pending resume each [begin] returned, in call order. */
    val pendings = mutableListOf<FakePendingResume>()

    override fun begin(bookUuid: String, explicitFileId: Long?, explicitPositionSeconds: Double?): PendingResume {
        begins += Triple(bookUuid, explicitFileId, explicitPositionSeconds)
        return FakePendingResume().also { pendings += it }
    }
}

/**
 * One load's resume answers. [settleAnswer] can be deferred to hold the start until the test completes it. [cancelCount]
 * counts the abandonments.
 */
class FakePendingResume : PendingResume {

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
