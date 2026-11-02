package com.omnibus.omnibus.data.progress

import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory [ProgressRepository] for the ViewModel tests. Every [savePosition] call is recorded
 * in [saves] when it starts and in [completedSaves] when it returns.
 */
class FakeProgressRepository : ProgressRepository {

    /** The arguments of one [savePosition] call. */
    data class Save(val position: ReadingPosition, val push: Boolean)

    /** Seed for [getLocalPosition]. [savePosition] writes here too. */
    val stored = mutableMapOf<String, ReadingPosition>()

    /** Every call, in call order. */
    val saves = mutableListOf<Save>()

    /** Calls that returned, in return order. */
    val completedSaves = mutableListOf<Save>()

    /** What [savePosition] returns for a push. */
    var pushResult: PositionSaveResult = PositionSaveResult.Pushed

    /** When set, [savePosition] suspends on it after recording the call, to hold it in flight. */
    var holdSaves: CompletableDeferred<Unit>? = null

    /** When set, [getLocalPosition] suspends on it before answering. */
    var pendingLocal: CompletableDeferred<Unit>? = null

    override suspend fun getLocalPosition(bookUuid: String): ReadingPosition? {
        pendingLocal?.await()
        return stored[bookUuid]
    }

    override suspend fun savePosition(position: ReadingPosition, push: Boolean): PositionSaveResult {
        val save = Save(position, push)
        saves += save
        stored[position.bookUuid] = position
        holdSaves?.await()
        completedSaves += save
        return if (push) pushResult else PositionSaveResult.NotPushed
    }
}
