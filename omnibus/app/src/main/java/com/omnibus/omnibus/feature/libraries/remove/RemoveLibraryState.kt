package com.omnibus.omnibus.feature.libraries.remove

/** Where removing a library, from its detail screen, has got to. */
sealed interface RemoveLibraryState {

    /** No removal is in progress; the confirm dialog is closed. */
    data object Hidden : RemoveLibraryState

    /** The confirm dialog is open, with Cancel and Remove. */
    data object Confirming : RemoveLibraryState

    /** The sync runs in the confirm dialog, which shows Removing. */
    data object Syncing : RemoveLibraryState

    /** The sync failed: the dialog offers Remove anyway. */
    data object SyncFailed : RemoveLibraryState

    /** Remove anyway was tapped: the failure dialog shows Removing. */
    data object RemovingAnyway : RemoveLibraryState
}
