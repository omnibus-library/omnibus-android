package com.omnibus.omnibus.data.audiobook

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job

/** A preferences DataStore on a temporary file, which [close] deletes. */
internal class TempDataStore : AutoCloseable {
    private val directory = createTempDirectory("audiobook-data-store").toFile()
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var dataStore: DataStore<Preferences> = open()
        private set

    /** Stops the store and opens a second one on the same file, as an app restart does. */
    suspend fun reopen(): DataStore<Preferences> {
        scope.coroutineContext.job.cancelAndJoin()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = open()
        return dataStore
    }

    override fun close() {
        scope.cancel()
        directory.deleteRecursively()
    }

    private fun open(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(directory, "test.preferences_pb") },
    )
}

/** A DataStore whose reads and writes both fail, as a lost disk does. */
internal fun unreadableDataStore(): DataStore<Preferences> = FailingDataStore(flow { throw IOException("disk gone") })

/** A DataStore that reads as empty and whose writes fail, as a full disk does. */
internal fun unwritableDataStore(): DataStore<Preferences> = FailingDataStore(flowOf(emptyPreferences()))

private class FailingDataStore(override val data: Flow<Preferences>) : DataStore<Preferences> {
    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        throw IOException("disk full")
}
