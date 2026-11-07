package com.omnibus.omnibus.feature.bookdetail.more.merge

import com.omnibus.omnibus.data.merge.MergeBooksResult
import com.omnibus.omnibus.data.merge.MergeCandidatesResult
import com.omnibus.omnibus.data.merge.MergeReceipt
import com.omnibus.omnibus.data.merge.MergeRepository
import com.omnibus.omnibus.data.merge.UndoMergeResult
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import kotlinx.coroutines.CompletableDeferred

/**
 * A [MergeRepository] whose answers a test scripts. Each call returns its `*Result` unless a hold is queued for it: a held
 * call suspends until its deferred completes. Every call is recorded.
 */
class ScriptedMergeRepository : MergeRepository {

    /** The query of every search, in call order. */
    val searches = mutableListOf<String>()

    /** The (source, target) of every merge, in call order. */
    val merges = mutableListOf<Pair<String, String>>()

    /** The log id of every undo, in call order. */
    val undos = mutableListOf<Long>()

    /** Returned by [searchCandidates] when no hold is queued. */
    var searchResult: MergeCandidatesResult = MergeCandidatesResult.Success(emptyList())

    /** Returned by [mergeBooks] when no hold is queued. */
    var mergeResult: MergeBooksResult = MergeBooksResult.Success(MergeReceipt(randomLong(), randomString()))

    /** Returned by [undoMerge] when no hold is queued. */
    var undoResult: UndoMergeResult = UndoMergeResult.Success(randomString())

    private val searchHolds = ArrayDeque<CompletableDeferred<MergeCandidatesResult>>()
    private val mergeHolds = ArrayDeque<CompletableDeferred<MergeBooksResult>>()
    private val undoHolds = ArrayDeque<CompletableDeferred<UndoMergeResult>>()

    /** Queues the next [searchCandidates] call to wait for the returned deferred. */
    fun holdSearch(): CompletableDeferred<MergeCandidatesResult> =
        CompletableDeferred<MergeCandidatesResult>().also { searchHolds.addLast(it) }

    /** Queues the next [mergeBooks] call to wait for the returned deferred. */
    fun holdMerge(): CompletableDeferred<MergeBooksResult> =
        CompletableDeferred<MergeBooksResult>().also { mergeHolds.addLast(it) }

    /** Queues the next [undoMerge] call to wait for the returned deferred. */
    fun holdUndo(): CompletableDeferred<UndoMergeResult> =
        CompletableDeferred<UndoMergeResult>().also { undoHolds.addLast(it) }

    override suspend fun searchCandidates(query: String): MergeCandidatesResult {
        searches += query
        return searchHolds.removeFirstOrNull()?.await() ?: searchResult
    }

    override suspend fun mergeBooks(sourceUuid: String, targetUuid: String): MergeBooksResult {
        merges += sourceUuid to targetUuid
        return mergeHolds.removeFirstOrNull()?.await() ?: mergeResult
    }

    override suspend fun undoMerge(mergeLogId: Long): UndoMergeResult {
        undos += mergeLogId
        return undoHolds.removeFirstOrNull()?.await() ?: undoResult
    }
}
