package com.omnibus.omnibus.data.audiobook.position

import com.omnibus.omnibus.data.audiobook.playback.PendingResume
import com.omnibus.omnibus.data.audiobook.playback.ResumeStartResolver
import com.omnibus.omnibus.data.progress.PositionSync
import com.omnibus.omnibus.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select

/**
 * Chooses where a load starts from the local and the server positions (C5; I-RES-1 to I-RES-4, I-RES-6, CC1).
 *
 * [begin] starts the local read and, without an explicit position, the server read, both on [scope], so they outlive
 * the caller. The 1200 ms deadline runs from [begin] (I-RES-2). [PendingResume.settle] waits for the server only until
 * that deadline, and an answer that arrives after it may still be offered through [ListeningPositionSync] (I-RES-6).
 */
@Singleton
class DefaultResumeStartResolver @Inject constructor(
    private val repository: ListeningProgressRepository,
    private val sync: DefaultListeningPositionSync,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ResumeStartResolver {

    override fun begin(bookUuid: String, explicitFileId: Long?, explicitPositionSeconds: Double?): PendingResume {
        val token = sync.loadBegan()
        val local = scope.async { repository.localCandidate(bookUuid) }
        val server = if (explicitPositionSeconds == null) scope.async { repository.serverPosition(bookUuid) } else null
        val deadline = if (server != null) scope.launch { delay(PositionSync.OPEN_DEADLINE) } else null
        return AudioPendingResume(bookUuid, explicitPositionSeconds, token, local, server, deadline)
    }

    /** One load's resume decision. Its state is main-thread only. */
    private inner class AudioPendingResume(
        private val bookUuid: String,
        private val explicit: Double?,
        private val token: Int,
        private val local: Deferred<AudioPosition?>,
        private val server: Deferred<ServerAudioPosition?>?,
        private val deadline: Job?,
    ) : PendingResume {

        private var cancelled = false

        /** The answer [settle] gave, returned again by any later call (I-RES-6 idempotency). */
        private var answer: Answer? = null

        private var lateJob: Job? = null

        override suspend fun savedFileId(): Long? = local.await()?.bookFileId

        override suspend fun settle(loadedFileId: Long, durationSeconds: Double): Double? {
            answer?.let { return it.start }
            if (cancelled) return null
            explicit?.let {
                answer = Answer(it)
                return it
            }
            val localCandidate = local.await()
            val serverRead = server!!
            val (answered, serverAnswer) = if (serverRead.isCompleted) {
                true to serverRead.await()
            } else {
                select<Pair<Boolean, ServerAudioPosition?>> {
                    serverRead.onAwait { true to it }
                    deadline!!.onJoin { false to null }
                }
            }
            val start = ResumeRules.startFor(ResumeRules.newer(localCandidate, serverAnswer), loadedFileId)
            answer = Answer(start)
            if (!answered && !cancelled) {
                lateJob = scope.launch {
                    val late = serverRead.await() ?: return@launch
                    if (!cancelled && sync.isCurrent(token) &&
                        ResumeRules.isLateOffer(late, localCandidate, loadedFileId, start ?: 0.0)
                    ) {
                        sync.offerLate(token, LatePosition(bookUuid, loadedFileId, late.positionSeconds))
                    }
                }
            }
            return start
        }

        /**
         * Stops the deadline wait, the server read and any late offer. The local read is left to finish, since it is cheap
         * and [savedFileId] may still be awaited.
         */
        override fun cancel() {
            cancelled = true
            deadline?.cancel()
            server?.cancel()
            lateJob?.cancel()
        }
    }

    /** The start [AudioPendingResume.settle] answered with, null for a start at 0. */
    private class Answer(val start: Double?)
}
