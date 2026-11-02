package com.omnibus.omnibus.data.progress

/** Reads and writes this device's reading positions, and pushes them to the server. */
interface ProgressRepository {

    /** This device's last position for [bookUuid], from local storage only. Null when none. */
    suspend fun getLocalPosition(bookUuid: String): ReadingPosition?

    /** Writes [position] locally, then pushes it when [push]. Calls are applied in call order. Never throws. */
    suspend fun savePosition(position: ReadingPosition, push: Boolean): PositionSaveResult

    /**
     * The server's epub position for [bookUuid], from `GET api/progress/{uuid}?format=epub`. Never throws.
     * Without a saved server it is [ServerPositionResult.Error].
     */
    suspend fun getServerPosition(bookUuid: String): ServerPositionResult
}

/** What [ProgressRepository.getServerPosition] found. */
sealed interface ServerPositionResult {
    /** The server has an epub record for the book. */
    data class Found(val position: ServerReadingPosition) : ServerPositionResult

    /** The server has no epub record for the book. */
    data object None : ServerPositionResult

    /** The read failed: no server is saved, the server answered with an error, or the answer didn't decode. */
    data object Error : ServerPositionResult
}

/** What [ProgressRepository.savePosition] did. The local write happens in every case. */
sealed interface PositionSaveResult {
    /** The position was pushed and the server accepted it. */
    data object Pushed : PositionSaveResult

    /** The caller asked for no push, so none was made. */
    data object NotPushed : PositionSaveResult

    /** No server is saved, so the push could not be made. */
    data object NoServer : PositionSaveResult

    /** The push was made and failed. [error] says why. */
    data class Failed(val error: ProgressError) : PositionSaveResult
}

/** Why a position push failed. */
sealed interface ProgressError {
    /** The server couldn't be reached. */
    data object Unreachable : ProgressError

    /** The server answered with something that isn't a valid response. */
    data object UnexpectedResponse : ProgressError

    /** The server rejected the saved sign-in. */
    data object SessionExpired : ProgressError

    /** The server answered with a non-success status [code]. */
    data class Http(val code: Int) : ProgressError
}
