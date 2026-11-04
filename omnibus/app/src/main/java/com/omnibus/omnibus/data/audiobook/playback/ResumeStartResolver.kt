package com.omnibus.omnibus.data.audiobook.playback

/**
 * Decides where a load starts. Optional: bind it with `@BindsOptionalOf`. With no binding a load starts at
 * `positionSeconds ?: 0.0`, and the saved file is not consulted.
 */
interface ResumeStartResolver {

    /**
     * Called as a load starts, before the `Book` and manifest fetches, so the resume lookup can run alongside them.
     *
     * @param explicitFileId the file the caller asked for, or null.
     * @param explicitPositionSeconds the position the caller asked for, or null.
     */
    fun begin(bookUuid: String, explicitFileId: Long?, explicitPositionSeconds: Double?): PendingResume
}

/** One load's resume decision. */
interface PendingResume {

    /**
     * The file of the locally saved position, used to choose the file when none was asked for. Local only: it never
     * waits on the server.
     */
    suspend fun savedFileId(): Long?

    /**
     * The start position once the duration is known, or null to start at 0. Called exactly once on every load that
     * completes, including a paused one. It answers by the deadline.
     *
     * @return the position in seconds, or null.
     */
    suspend fun settle(loadedFileId: Long, durationSeconds: Double): Double?

    /**
     * Called only when the load is abandoned: a stop, a new load, or a failure. A pause never cancels it, since the
     * book still loads paused and still needs its position.
     */
    fun cancel()
}
