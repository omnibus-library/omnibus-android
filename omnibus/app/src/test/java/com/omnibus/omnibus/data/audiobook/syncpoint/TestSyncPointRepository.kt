package com.omnibus.omnibus.data.audiobook.syncpoint

/**
 * In-memory [SyncPointRepository] for consumers' tests. Each call is recorded, then answered by [onDeclare]. Nothing is sent.
 */
class TestSyncPointRepository : SyncPointRepository {

    /** Each call's book uuid, file id and audio seconds, in call order. */
    val calls = mutableListOf<Triple<String, Long, Double>>()

    /** Answers a call. The default is [SyncPointResult.Declared]. */
    var onDeclare: suspend (String, Long, Double) -> SyncPointResult = { _, _, _ -> SyncPointResult.Declared }

    override suspend fun declareAudioSyncPoint(
        bookUuid: String,
        bookFileId: Long,
        audioSeconds: Double,
    ): SyncPointResult {
        calls += Triple(bookUuid, bookFileId, audioSeconds)
        return onDeclare(bookUuid, bookFileId, audioSeconds)
    }
}
