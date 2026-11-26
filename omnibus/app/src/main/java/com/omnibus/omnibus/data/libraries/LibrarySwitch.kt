package com.omnibus.omnibus.data.libraries

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** Switches the active library and restarts the app (C11). */
interface LibrarySwitch {

    /**
     * Commits a switch of [kind] to [targetLibraryId]: stops playback and waits for it to save, saves the pending
     * opening, makes the target active, then restarts the app. Never changes a token or another library's rows.
     */
    suspend fun commit(targetLibraryId: String, kind: SwitchKind)

    /** The pending opening a restart must reopen on, or null when none is waiting. */
    fun getPendingOpeningFlow(): Flow<PendingOpening?>

    /** Removes the pending opening, once the app has opened on it. */
    suspend fun clearPendingOpening()
}

/** The library a restarted app reopens on, and the switch that asked for it. */
data class PendingOpening(val libraryId: String, val kind: SwitchKind)

/** The kinds of switch. Each kind has its own branch in [DefaultLibrarySwitch.commit]. */
@Serializable
enum class SwitchKind {
    Switch,

    /** A library was just added and signed in; switch to it. */
    Add,
}
