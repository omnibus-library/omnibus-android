package com.omnibus.omnibus.data.progress

import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/**
 * In-memory [ProgressRepository] for the ViewModel tests. Every [savePosition] call is recorded
 * in [saves] when it starts and in [completedSaves] when it returns.
 */
class TestProgressRepository : ProgressRepository {

    /** The arguments of one [savePosition] call. */
    data class Save(val position: ReadingPosition, val push: Boolean)

    /** Seed for [getLocalPosition]. [savePosition] writes here too. */
    val stored = mutableMapOf<String, ReadingPosition>()

    /** Every call, in call order. */
    val saves = mutableListOf<Save>()

    /** Calls that returned, in return order. */
    val completedSaves = mutableListOf<Save>()

    /** What [savePosition] returns for a push. */
    var pushResult: PositionSaveResult? = null

    /** When set, [savePosition] suspends on it after recording the call, to hold it in flight. */
    var holdSaves: CompletableDeferred<Unit>? = null

    /** What [getServerPosition] returns, unless [serverAnswer] is set. */
    var serverResult: ServerPositionResult? = null

    /** Virtual milliseconds [getServerPosition] takes before answering. */
    var serverDelayMillis = 0L

    /** When set, [getServerPosition] answers with this instead, once it completes. */
    var serverAnswer: CompletableDeferred<ServerPositionResult>? = null

    /** The uuid of every [getServerPosition] call, in call order. */
    val serverReads = mutableListOf<String>()

    override suspend fun getLocalPosition(bookUuid: String): ReadingPosition? {
        return stored[bookUuid]
    }

    override suspend fun savePosition(position: ReadingPosition, push: Boolean): PositionSaveResult {
        val save = Save(position, push)
        saves += save
        stored[position.bookUuid] = position
        holdSaves?.await()
        completedSaves += save
        return if (push) pushResult ?: unscripted("savePosition with a push") else PositionSaveResult.NotPushed
    }

    override suspend fun getServerPosition(bookUuid: String): ServerPositionResult {
        serverReads += bookUuid
        if (serverDelayMillis > 0) delay(serverDelayMillis)
        return serverAnswer?.await() ?: serverResult ?: awaitCancellation()
    }
}
