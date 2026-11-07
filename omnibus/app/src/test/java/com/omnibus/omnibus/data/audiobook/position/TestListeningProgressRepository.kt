package com.omnibus.omnibus.data.audiobook.position

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * An in-memory [ListeningProgressRepository] for the position listeners and the resume resolver. Every call is recorded,
 * and a gate holds the call that it guards until a test completes it. A missing server answer is a null answer at once.
 *
 * @param order when set, each call is logged in it as `localCandidate:<uuid>`, `serverPosition:<uuid>`,
 *   `save:<uuid>:<push>` or `queueSession:<uuid>`, in call order.
 */
class TestListeningProgressRepository(private val order: MutableList<String>? = null) : ListeningProgressRepository {

    /** The local candidate of each book. A missing book has none. */
    val locals = mutableMapOf<String, AudioPosition?>()

    /** When set, every [localCandidate] waits on it before answering. */
    var localGate: CompletableDeferred<Unit>? = null

    /** How many times [localCandidate] was called. */
    var localCalls = 0

    /** The server answer of each book. A missing book answers null at once. */
    val serverAnswers = mutableMapOf<String, CompletableDeferred<ServerAudioPosition?>>()

    /** How many times [serverPosition] was called. */
    var serverCalls = 0

    /** How many [serverPosition] calls were cancelled while waiting for their answer. */
    var serverCancellations = 0

    /** Each saved position with its `push` flag, in call order. */
    val saves = mutableListOf<Pair<AudioPosition, Boolean>>()

    /** When set, [save] throws it and records nothing. */
    var saveFailure: Throwable? = null

    /** Each queued report, in call order. */
    val reports = mutableListOf<ListeningSessionReport>()

    /** When set, every [queueSession] waits on it before recording the report. */
    var queueGate: CompletableDeferred<Unit>? = null

    /** How many times [requestFlush] was called. */
    var flushRequests = 0

    override suspend fun localCandidate(bookUuid: String): AudioPosition? {
        order?.add("localCandidate:$bookUuid")
        localCalls++
        localGate?.await()
        return locals[bookUuid]
    }

    override suspend fun serverPosition(bookUuid: String): ServerAudioPosition? {
        order?.add("serverPosition:$bookUuid")
        serverCalls++
        val answer = serverAnswers[bookUuid] ?: return null
        return try {
            answer.await()
        } catch (e: CancellationException) {
            serverCancellations++
            throw e
        }
    }

    override suspend fun save(position: AudioPosition, push: Boolean) {
        order?.add("save:${position.bookUuid}:$push")
        saveFailure?.let { throw it }
        saves += position to push
    }

    override suspend fun queueSession(report: ListeningSessionReport) {
        order?.add("queueSession:${report.bookUuid}")
        queueGate?.await()
        reports += report
    }

    override fun requestFlush() {
        flushRequests++
    }
}
