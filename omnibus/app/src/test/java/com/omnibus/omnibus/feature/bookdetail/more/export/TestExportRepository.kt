package com.omnibus.omnibus.feature.bookdetail.more.export

import com.omnibus.omnibus.data.export.ExportRepository
import com.omnibus.omnibus.data.export.ExportSaveResult
import com.omnibus.omnibus.data.export.KindleSendResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/**
 * Scripted [ExportRepository] for the ViewModel tests. Each call records itself, then waits for the next queued gate,
 * or returns its default answer at once when none is queued.
 */
class TestExportRepository : ExportRepository {

    /** The book uuid of every [sendToKindle] call, in order. */
    val kindleCalls = mutableListOf<String>()

    /** Every save call, in order, as `kepub:<uuid>:<title>` or `<kind>:<uuid>:<title>:<extension>:<bytes>`. */
    val saveCalls = mutableListOf<String>()

    /** Gates for the next [sendToKindle] calls, first in first out. */
    val kindleGates = ArrayDeque<CompletableDeferred<KindleSendResult>>()

    /** Gates for the next save calls, first in first out. */
    val saveGates = ArrayDeque<CompletableDeferred<ExportSaveResult>>()

    /** Whether a save was cancelled while it waited on its gate. */
    var saveCancelled = false

    override suspend fun sendToKindle(bookUuid: String): KindleSendResult {
        kindleCalls += bookUuid
        val gate = kindleGates.removeFirstOrNull() ?: return KindleSendResult.Sent
        return gate.await()
    }

    override suspend fun saveKepub(bookUuid: String, title: String): ExportSaveResult {
        saveCalls += "kepub:$bookUuid:$title"
        return awaitSave()
    }

    override suspend fun saveEbook(
        bookUuid: String,
        title: String,
        fallbackExtension: String,
        expectedBytes: Long,
    ): ExportSaveResult {
        saveCalls += "ebook:$bookUuid:$title:$fallbackExtension:$expectedBytes"
        return awaitSave()
    }

    override suspend fun saveAudiobook(
        bookUuid: String,
        title: String,
        fallbackExtension: String,
        expectedBytes: Long,
    ): ExportSaveResult {
        saveCalls += "audio:$bookUuid:$title:$fallbackExtension:$expectedBytes"
        return awaitSave()
    }

    private suspend fun awaitSave(): ExportSaveResult {
        val gate = saveGates.removeFirstOrNull() ?: return ExportSaveResult.Saved(emptyList())
        return try {
            gate.await()
        } catch (ex: CancellationException) {
            saveCancelled = true
            throw ex
        }
    }
}
