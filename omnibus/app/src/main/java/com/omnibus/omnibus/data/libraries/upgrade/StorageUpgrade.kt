package com.omnibus.omnibus.data.libraries.upgrade

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.withTransaction
import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.local.ListeningPositionRow
import com.omnibus.omnibus.data.configuration.ServerAddress
import com.omnibus.omnibus.data.database.OmnibusDatabase
import com.omnibus.omnibus.data.libraries.LibraryHost
import com.omnibus.omnibus.data.libraries.local.LegacyTokenAdopter
import com.omnibus.omnibus.data.libraries.local.LibraryCovers
import com.omnibus.omnibus.data.libraries.local.LibraryEntity
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.di.IoDispatcher
import com.omnibus.omnibus.di.TimeSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The gate in front of every per-library read (R9, R10). The first call moves what the builds before libraries
 * saved into the database: the saved server becomes the first library, and its positions, speeds, offers, token
 * and covers come with it. Once it is done, a call costs one volatile read.
 *
 * The upgrade is resumable. Each step can run again, the import is one transaction, and the legacy files are
 * deleted only after everything else is kept. A failed run is logged and retried by the next call.
 */
@Singleton
class StorageUpgrade internal constructor(
    private val database: OmnibusDatabase,
    private val tokenAdopter: LegacyTokenAdopter,
    private val covers: LibraryCovers,
    private val legacyDir: File,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: TimeSource,
    private val beforeImportCommit: suspend () -> Unit = {},
) {

    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: OmnibusDatabase,
        tokenAdopter: LegacyTokenAdopter,
        covers: LibraryCovers,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
        clock: TimeSource,
    ) : this(database, tokenAdopter, covers, File(context.filesDir, "datastore"), ioDispatcher, clock)

    private val mutex = Mutex()

    @Volatile
    private var done = false

    /**
     * Runs the upgrade if it hasn't finished, and returns whether it has. A false return means the library data
     * can't be read or written yet: the reader sees no library, and nothing can create one.
     */
    suspend fun ensureDone(): Boolean {
        if (done) return true
        return mutex.withLock {
            if (done) return@withLock true
            done = try {
                withContext(ioDispatcher) { run() }
                true
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Storage upgrade failed; retrying later", error)
                false
            }
            done
        }
    }

    private suspend fun run() {
        val legacyNames = LegacyFiles.PER_BOOK + LegacyFiles.SERVER_CONFIGURATION
        if (legacyNames.none { File(legacyDir, it).exists() }) return
        val address = readFile(LegacyFiles.SERVER_CONFIGURATION) { LegacyStorageReader.readAddress(it) }
            ?.let(ServerAddress::normalize)
        if (address == null) {
            // Nothing to attach the legacy data to, so it is dropped.
            tokenAdopter.adoptLegacyToken(null)
            covers.deleteLooseFiles()
            deleteLegacyFiles()
            return
        }

        val libraries = database.librariesDao()
        val libraryId = libraries.activeId()
            ?: libraries.librariesFlow().first().firstOrNull()?.id
            ?: importLegacyData(address)
        tokenAdopter.adoptLegacyToken(libraryId)
        covers.moveLooseFilesInto(libraryId)
        deleteLegacyFiles()
    }

    /**
     * Creates the active library and writes the legacy entries under it, in one transaction. The legacy files are
     * read first, so no DataStore I/O runs inside the transaction.
     */
    private suspend fun importLegacyData(address: String): String {
        val reading = readFile(LegacyFiles.READING_POSITIONS) { LegacyStorageReader.readReadingPositions(it) }.orEmpty()
        val listening = readFile(LegacyFiles.AUDIO_POSITIONS) { LegacyStorageReader.readListening(it) }
            ?: LegacyListening(emptyMap(), emptyMap(), emptyList())
        val rates = readFile(LegacyFiles.PLAYBACK_RATES) { LegacyStorageReader.readRates(it) }
            ?: LegacyRates(emptyMap(), emptyMap())
        val dismissed = readFile(LegacyFiles.RESUME_OFFERS) { LegacyStorageReader.readDismissed(it) }.orEmpty()

        val libraryId = UUID.randomUUID().toString()
        val now = clock.nowMillis()
        database.withTransaction {
            database.librariesDao().insertActive(
                LibraryEntity(
                    id = libraryId,
                    address = address,
                    name = LibraryHost.of(address),
                    username = null,
                    addedAt = now,
                    lastUsedAt = now,
                    isActive = true,
                ),
            )
            reading.forEach { position -> importReadingPosition(libraryId, position) }
            importListening(libraryId, listening)
            importRates(libraryId, rates)
            dismissed.forEach { (bookUuid, sourceClock) ->
                database.dismissedResumeOfferDao().store(libraryId, bookUuid, sourceClock)
            }
            beforeImportCommit()
        }
        return libraryId
    }

    private suspend fun importReadingPosition(libraryId: String, position: ReadingPosition) {
        database.readingPositionDao().save(
            libraryId,
            position.bookUuid,
            position.cfi,
            position.percent,
            position.clientUpdatedAt,
        )
    }

    /**
     * A book's cached value is its cached entry, or its pending entry when it has no cached one, as a save would
     * have written it. The pending group is written only when there is a pending entry.
     */
    private suspend fun importListening(libraryId: String, listening: LegacyListening) {
        val positions = database.listeningPositionDao()
        (listening.cached.keys + listening.pending.keys).toSet().forEach { bookUuid ->
            val pending = listening.pending[bookUuid]
            val cached = listening.cached[bookUuid] ?: pending ?: return@forEach
            positions.importPosition(
                libraryId,
                bookUuid,
                cached.toRow(),
                pending?.toRow(),
            )
        }
        positions.queueSessions(libraryId, listening.sessions)
    }

    private suspend fun importRates(libraryId: String, rates: LegacyRates) {
        val dao = database.playbackRateDao()
        (rates.cached.keys + rates.pending.keys).toSet().forEach { bookUuid ->
            dao.importRate(libraryId, bookUuid, rates.cached[bookUuid], rates.pending[bookUuid])
        }
    }

    /** Deletes the legacy per-book files, then the server file last, so an interrupted run still finds the address. */
    private fun deleteLegacyFiles() {
        LegacyFiles.PER_BOOK.forEach { File(legacyDir, it).delete() }
        File(legacyDir, LegacyFiles.SERVER_CONFIGURATION).delete()
    }

    /**
     * Reads one legacy file through its own DataStore, then stops that DataStore before returning so the file is
     * free to delete. Returns null when the file doesn't exist.
     */
    private suspend fun <T> readFile(name: String, block: suspend (DataStore<Preferences>) -> T): T? {
        val file = File(legacyDir, name)
        if (!file.exists()) return null
        val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            return block(store)
        } finally {
            scope.coroutineContext.job.cancelAndJoin()
        }
    }

    private fun AudioPosition.toRow() = ListeningPositionRow(
        bookUuid = bookUuid,
        positionSeconds = positionSeconds,
        bookFileId = bookFileId,
        clientUpdatedAt = clientUpdatedAt,
    )

    private companion object {
        const val TAG = "StorageUpgrade"
    }
}
