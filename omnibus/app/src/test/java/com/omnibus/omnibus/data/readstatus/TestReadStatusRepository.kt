package com.omnibus.omnibus.data.readstatus

import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.unscripted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [ReadStatusRepository] for consumers' tests. It mirrors [DefaultReadStatusRepository]'s optimistic,
 * rollback and superseded-write rules, so a test that passes here behaves the same against production. Writes run
 * in the caller, with no HTTP and no application scope.
 *
 * Unlike production, collecting [getReadStatusFlow] does not fetch. Call [seed] or [refresh] first.
 */
class TestReadStatusRepository(private val now: () -> Long = { randomLong() }) : ReadStatusRepository {

    /** One book's state, with the same bookkeeping as production's entry. */
    private class Entry {
        val state = MutableStateFlow<ReadStatusState>(ReadStatusState.Unknown)
        var confirmed: ReadStatusState = ReadStatusState.Unknown
        var confirmedSeq = 0L
        var writeSeq = 0L
        var pendingWrites = 0
    }

    private val entries = mutableMapOf<String, Entry>()

    private fun entry(uuid: String): Entry = entries.getOrPut(uuid) { Entry() }

    /** The next GET's outcome. When empty, a GET answers the state [seed] set, and otherwise never answers. */
    val readResults = ArrayDeque<ReadStatusState>()

    /** The next PUT's outcome. */
    val writeResults = ArrayDeque<ReadStatusWriteResult>()

    /** When non-empty, the next write waits on the head after its optimistic apply, before taking its result. */
    val writeGates = ArrayDeque<CompletableDeferred<Unit>>()

    /** Every write that would make a request, in call order. The same-status case is not recorded. */
    val setStatusCalls = mutableListOf<Pair<String, ReadStatus>>()

    /** How many times [refresh] was called. */
    var refreshCount = 0

    /** The uuid of every GET, from [refresh] or [fetchStatus], in call order. */
    val fetchCalls = mutableListOf<String>()

    /** Sets [bookUuid]'s state as if the server had answered it. */
    fun seed(bookUuid: String, state: ReadStatusState) {
        val entry = entry(bookUuid)
        entry.state.value = state
        entry.confirmed = state
    }

    override fun getReadStatusFlow(bookUuid: String): Flow<ReadStatusState> = entry(bookUuid).state

    override suspend fun refresh(bookUuid: String) {
        refreshCount++
        fetchStatus(bookUuid)
    }

    override suspend fun fetchStatus(bookUuid: String): ReadStatusState {
        if (bookUuid.isBlank()) return ReadStatusState.Failed
        fetchCalls += bookUuid
        val entry = entry(bookUuid)
        val outcome = readResults.removeFirstOrNull()
            ?: entry.confirmed.takeIf { it is ReadStatusState.Known }
            ?: awaitCancellation()
        val seqAtStart = entry.writeSeq
        if (outcome is ReadStatusState.Known) {
            if (seqAtStart >= entry.confirmedSeq) {
                entry.confirmed = outcome
                entry.confirmedSeq = seqAtStart
            }
        } else if (entry.confirmed !is ReadStatusState.Known) {
            entry.confirmed = ReadStatusState.Failed
        }
        if (entry.pendingWrites == 0) entry.state.value = entry.confirmed
        return outcome
    }

    override suspend fun setStatus(bookUuid: String, status: ReadStatus): ReadStatusWriteResult {
        if (bookUuid.isBlank()) return ReadStatusWriteResult.Failed(ReadStatusError.BookNotFound)
        val entry = entry(bookUuid)
        val current = entry.state.value
        if (current is ReadStatusState.Known && current.record.status == status) return ReadStatusWriteResult.Unchanged

        setStatusCalls += bookUuid to status
        val optimisticRecord = optimistic(current, status, now())
        entry.pendingWrites++
        val seq = ++entry.writeSeq
        entry.state.value = ReadStatusState.Known(optimisticRecord)

        writeGates.removeFirstOrNull()?.await()
        val result = writeResults.removeFirstOrNull() ?: unscripted("setStatus")

        entry.pendingWrites--
        if (result is ReadStatusWriteResult.Saved && seq >= entry.confirmedSeq) {
            entry.confirmed = ReadStatusState.Known(result.record)
            entry.confirmedSeq = seq
        }
        if (seq == entry.writeSeq) entry.state.value = entry.confirmed
        return result
    }

    private fun optimistic(current: ReadStatusState, status: ReadStatus, now: Long) = ReadStatusRecord(
        status = status,
        updatedAtMillis = now,
        finishedAtMillis = when {
            status != ReadStatus.Finished -> null
            current is ReadStatusState.Known && current.record.status == ReadStatus.Finished -> current.record.finishedAtMillis
            else -> now
        },
    )
}
