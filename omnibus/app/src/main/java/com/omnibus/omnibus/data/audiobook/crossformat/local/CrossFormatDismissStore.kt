package com.omnibus.omnibus.data.audiobook.crossformat.local

import com.omnibus.omnibus.data.libraries.ActiveLibrary
import com.omnibus.omnibus.data.libraries.readFromActiveLibrary
import com.omnibus.omnibus.data.libraries.writeToActiveLibrary
import javax.inject.Inject

/**
 * The ebook offers the listener dismissed, kept per book as the source clock of the dismissed position (R8). It is
 * this device's memory and is not account-scoped (A1).
 */
interface CrossFormatDismissStore {

    /** The dismissed source clock of [bookUuid], or null when none is stored or the memory can't be read. */
    suspend fun dismissedClock(bookUuid: String): Long?

    /** Stores [sourceClock] as the dismissed clock of [bookUuid], replacing any earlier one. */
    suspend fun store(bookUuid: String, sourceClock: Long)
}

/**
 * [CrossFormatDismissStore] backed by the `dismissed_resume_offers` table, scoped to the active library. A read failure
 * reads as absent, so the offer shows again; a write failure is logged. Neither throws an IO error.
 */
class DefaultCrossFormatDismissStore @Inject constructor(
    private val dao: DismissedResumeOfferDao,
    private val activeLibrary: ActiveLibrary,
) : CrossFormatDismissStore {

    override suspend fun dismissedClock(bookUuid: String): Long? =
        activeLibrary.readFromActiveLibrary(TAG) { dao.dismissedClock(it, bookUuid) }

    override suspend fun store(bookUuid: String, sourceClock: Long) {
        activeLibrary.writeToActiveLibrary(TAG) { libraryId -> dao.store(libraryId, bookUuid, sourceClock) }
    }

    private companion object {
        const val TAG = "CrossFormatDismissStore"
    }
}
