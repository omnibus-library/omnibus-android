package com.omnibus.omnibus.data.audiobook.position

/**
 * The pure rules for choosing where a book resumes (I-RES-1, I-RES-3, I-RES-6). No coroutines, so each
 * rule is tested directly. Clocks are unix seconds.
 */
internal object ResumeRules {

    /** A resume start at or under this many seconds is ignored: the book starts at 0 (I-RES-3). */
    const val MIN_RESUME_SECONDS = 1.0

    /** A late server answer must be this many seconds ahead of the settled start to be offered (I-RES-6). */
    const val LATE_OFFER_LEAD_SECONDS = 1.0

    /** The clock that orders two positions: `client_updated_at`, or `updated_at` when that is absent. */
    fun orderingClock(clientUpdatedAt: Long?, updatedAt: Long): Long = clientUpdatedAt ?: updatedAt

    /** The position a resume would start from, and the file it was saved against. */
    data class ResumeChoice(val positionSeconds: Double, val bookFileId: Long?)

    /** Whether [server] is a positive position strictly newer than [local]. A tie keeps local. */
    fun isServerNewer(local: AudioPosition?, server: ServerAudioPosition?): Boolean =
        server != null && server.positionSeconds > 0 && (local == null || server.orderingClock > local.clientUpdatedAt)

    /** The server's choice when it is newer, else the local one, else null. */
    fun newer(local: AudioPosition?, server: ServerAudioPosition?): ResumeChoice? = when {
        isServerNewer(local, server) -> ResumeChoice(server!!.positionSeconds, server.bookFileId)
        local != null -> ResumeChoice(local.positionSeconds, local.bookFileId)
        else -> null
    }

    /** Whether a position saved against [recordFileId] applies to [loadedFileId]. Null or 0 match any file. */
    fun fileMatches(recordFileId: Long?, loadedFileId: Long): Boolean =
        recordFileId == null || loadedFileId == 0L || recordFileId == loadedFileId

    /** The start to seek to for [choice] on [loadedFileId], or null to start at 0. */
    fun startFor(choice: ResumeChoice?, loadedFileId: Long): Double? =
        choice?.takeIf { it.positionSeconds > MIN_RESUME_SECONDS && fileMatches(it.bookFileId, loadedFileId) }
            ?.positionSeconds

    /** Whether [late] may be offered after a load settled at [settledStartSeconds] (I-RES-6). */
    fun isLateOffer(
        late: ServerAudioPosition,
        local: AudioPosition?,
        loadedFileId: Long,
        settledStartSeconds: Double,
    ): Boolean =
        isServerNewer(local, late) &&
            fileMatches(late.bookFileId, loadedFileId) &&
            late.positionSeconds - settledStartSeconds > LATE_OFFER_LEAD_SECONDS
}
