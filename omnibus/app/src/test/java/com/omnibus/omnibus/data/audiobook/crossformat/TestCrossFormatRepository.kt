package com.omnibus.omnibus.data.audiobook.crossformat

import kotlinx.coroutines.CompletableDeferred

/** A [CrossFormatRepository] with scripted answers and in-memory dismissed clocks. */
class TestCrossFormatRepository(var result: CrossFormatAudioResult = CrossFormatAudioResult.NoCandidate) : CrossFormatRepository {
    val dismissedClocks = mutableMapOf<String, Long>()

    /** When set, [dismissedClock] waits for it before answering. */
    var dismissedClockGate: CompletableDeferred<Unit>? = null
    private val held = ArrayDeque<CompletableDeferred<CrossFormatAudioResult>>()

    /** Makes the next [audioResume] call wait for the returned deferred instead of answering [result]. */
    fun hold(): CompletableDeferred<CrossFormatAudioResult> =
        CompletableDeferred<CrossFormatAudioResult>().also { held.addLast(it) }

    override suspend fun audioResume(bookUuid: String): CrossFormatAudioResult {
        return held.removeFirstOrNull()?.await() ?: result
    }

    override suspend fun dismissedClock(bookUuid: String): Long? {
        dismissedClockGate?.await()
        return dismissedClocks[bookUuid]
    }

    override suspend fun storeDismissedClock(bookUuid: String, sourceClock: Long) {
        dismissedClocks[bookUuid] = sourceClock
    }
}
