package com.omnibus.omnibus.data.libraries

import android.database.SQLException
import android.util.Log
import com.omnibus.omnibus.data.libraries.local.LibrariesDao
import com.omnibus.omnibus.data.libraries.upgrade.StorageUpgrade
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The id of the library the app reads from (C7). */
interface ActiveLibrary {

    /** The active library's id, or null when there is none or the upgrade hasn't finished. */
    suspend fun id(): String?
}

/**
 * Reads the active library's id from the database on first need, and keeps it for the process. A null is never
 * kept, so a library added later is found. [update] follows a change made in this process.
 */
@Singleton
class DefaultActiveLibrary @Inject constructor(
    private val dao: LibrariesDao,
    private val upgrade: StorageUpgrade,
) : ActiveLibrary {

    private val mutex = Mutex()
    private var cached: String? = null

    override suspend fun id(): String? {
        if (!upgrade.ensureDone()) return null
        return mutex.withLock {
            cached ?: try {
                dao.activeId()?.also { cached = it }
            } catch (error: SQLException) {
                Log.e(TAG, "Unable to read the active library", error)
                null
            }
        }
    }

    /** Sets the cached id to [id]. Called when this process changes the active library. */
    internal suspend fun update(id: String?) {
        mutex.withLock { cached = id }
    }

    private companion object {
        const val TAG = "DefaultActiveLibrary"
    }
}
