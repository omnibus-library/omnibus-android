package com.omnibus.omnibus.data.deletion

import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation

/**
 * In-memory [DeletionRepository] for consumers' tests. Calls run in the caller, with no HTTP and no application scope.
 * Each answer is set by the test: a read with none never answers, and a delete with none fails. A gate holds a call
 * until the test completes it.
 */
class TestDeletionRepository : DeletionRepository {

    /** The answer to [fetchManifest]. */
    var manifestResult: DeletionManifestResult? = null

    /** The answer to [deleteItems]. */
    var deleteResult: DeleteItemsResult? = null

    /** When set, [fetchManifest] suspends until this completes, to observe the loading state. */
    var manifestGate: CompletableDeferred<Unit>? = null

    /** When set, [deleteItems] suspends until this completes, to observe the deleting state. */
    var deleteGate: CompletableDeferred<Unit>? = null

    /** Every uuid passed to [fetchManifest], in call order. */
    val manifestRequests = mutableListOf<String>()

    /** Every (uuid, file ids, copy ids) passed to [deleteItems], in call order. */
    val deleteRequests = mutableListOf<Triple<String, Set<Long>, Set<Long>>>()

    override suspend fun fetchManifest(bookUuid: String): DeletionManifestResult {
        manifestRequests += bookUuid
        manifestGate?.await()
        return manifestResult ?: awaitCancellation()
    }

    override suspend fun deleteItems(bookUuid: String, fileIds: Set<Long>, copyIds: Set<Long>): DeleteItemsResult {
        deleteRequests += Triple(bookUuid, fileIds, copyIds)
        deleteGate?.await()
        return deleteResult ?: unscripted("deleteItems")
    }
}
