package com.omnibus.omnibus.data.audiobook.remote

import com.omnibus.omnibus.data.library.ResumePoint
import kotlinx.coroutines.CompletableDeferred

/** In-memory [RecentListeningSource] for tests. */
class FakeRecentListeningSource : RecentListeningSource {

    /** How many times [recentAudio] was called. */
    var calls = 0
        private set

    /** What [recentAudio] answers, unless [pending] is set. */
    var points: List<ResumePoint> = emptyList()

    /** When set, [recentAudio] holds until this completes. */
    var pending: CompletableDeferred<List<ResumePoint>>? = null

    override suspend fun recentAudio(): List<ResumePoint> {
        calls++
        return pending?.await() ?: points
    }
}
