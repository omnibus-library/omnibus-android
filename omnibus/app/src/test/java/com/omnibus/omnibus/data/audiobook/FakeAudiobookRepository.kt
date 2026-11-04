package com.omnibus.omnibus.data.audiobook

import kotlinx.coroutines.CompletableDeferred

/** In-memory [AudiobookRepository] for tests. Records every [getManifest] call. */
class FakeAudiobookRepository : AudiobookRepository {

    /** The `(bookUuid, bookFileId)` of every [getManifest] call, in call order. */
    val requests = mutableListOf<Pair<String, Long?>>()

    /** What [getManifest] answers, unless [pending] is set. */
    var result: AudiobookManifestResult = AudiobookManifestResult.Failure(AudiobookManifestError.NoServer)

    /** When set, [getManifest] holds until this completes, to observe a load in flight. */
    var pending: CompletableDeferred<AudiobookManifestResult>? = null

    override suspend fun getManifest(bookUuid: String, bookFileId: Long?): AudiobookManifestResult {
        requests += bookUuid to bookFileId
        return pending?.await() ?: result
    }
}
