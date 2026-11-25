package com.omnibus.omnibus.data.libraries.upgrade

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.database.countAll
import com.omnibus.omnibus.data.database.rowsInLibrary
import com.omnibus.omnibus.data.libraries.DefaultActiveLibrary
import com.omnibus.omnibus.data.libraries.TestStorageFiles
import com.omnibus.omnibus.data.libraries.local.LegacyTokenAdopter
import com.omnibus.omnibus.data.libraries.writeLegacy
import com.omnibus.omnibus.data.libraries.writeLegacyToken
import com.omnibus.omnibus.data.libraries.writeTypicalLegacyInstall
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageUpgradeTest {

    private val db = TestDatabase.inMemory()
    private val files = TestStorageFiles()

    @After
    fun tearDown() {
        db.close()
        files.delete()
    }

    @Test
    fun issue379_tc49_turnsTheSavedServerIntoTheFirstLibrary() = runBlocking {
        files.writeTypicalLegacyInstall(address = "example.com:8080/")

        assertTrue(files.upgrade(db).ensureDone())

        val libraries = db.librariesDao().librariesFlow().first()
        assertEquals(1, libraries.size)
        assertEquals("http://example.com:8080", libraries[0].address)
        assertEquals("example.com:8080", libraries[0].name)
        assertTrue(libraries[0].isActive)
    }

    @Test
    fun issue379_tc50_importsPositionsRatesAndDismissedOffers() = runBlocking {
        files.writeTypicalLegacyInstall()
        val libraryId = upgradeAndGetActiveId()
        val positions = db.readingPositionDao()
        val listening = db.listeningPositionDao()

        val book1 = positions.positionFlow(libraryId, "book-1").first()
        assertEquals("epubcfi(/6/2)", book1?.cfi)
        assertEquals(10, book1?.percent)
        assertEquals(20, positions.positionFlow(libraryId, "book-2").first()?.percent)
        assertEquals(30.0, listening.cached(libraryId, "book-1")?.positionSeconds)
        assertEquals(1.5, db.playbackRateDao().cached(libraryId, "book-1"))
        assertEquals(42L, db.dismissedResumeOfferDao().dismissedClock(libraryId, "book-2"))
    }

    @Test
    fun issue379_tc51_keepsPendingValuesPending() = runBlocking {
        files.writeLegacy(LegacyFiles.SERVER_CONFIGURATION) {
            this[stringPreferencesKey("server_address")] = "example.com"
        }
        files.writeLegacy(LegacyFiles.AUDIO_POSITIONS) {
            this[stringPreferencesKey("audio.pending.book-3")] =
                """{"positionSeconds":55.0,"bookFileId":8,"clientUpdatedAt":11}"""
        }
        files.writeLegacy(LegacyFiles.PLAYBACK_RATES) {
            this[doublePreferencesKey("pending:book-3")] = 2.0
        }
        val libraryId = upgradeAndGetActiveId()

        val pendingPositions = db.listeningPositionDao().pendingPositions(libraryId)
        val pendingRates = db.playbackRateDao().pendingRates(libraryId)

        assertEquals(listOf("book-3" to 55.0), pendingPositions.map { it.bookUuid to it.positionSeconds })
        assertEquals(listOf("book-3" to 2.0), pendingRates.map { it.bookUuid to it.pendingRate })
        // A pending position with no cached value also becomes the cached value, as a save would have written it.
        assertEquals(55.0, db.listeningPositionDao().cached(libraryId, "book-3")?.positionSeconds)
    }

    @Test
    fun issue379_tc52_keepsQueuedSessionsInOrder() = runBlocking {
        files.writeTypicalLegacyInstall()
        val libraryId = upgradeAndGetActiveId()

        val sessions = db.listeningPositionDao().sessions(libraryId)

        assertEquals(listOf("session-1", "session-2", "session-3"), sessions.map { it.clientId })
        assertEquals(listOf("book-1", "book-1", "book-2"), sessions.map { it.bookUuid })
    }

    @Test
    fun issue379_tc53_keepsTheReaderSignedIn() = runBlocking {
        files.writeTypicalLegacyInstall()
        val libraryId = upgradeAndGetActiveId()

        assertEquals("legacy-token", files.tokens.getTokenFlow(libraryId).first())
    }

    @Test
    fun issue379_tc54_movesCachedCoversIntoTheLibrarysDirectory() = runBlocking {
        files.writeTypicalLegacyInstall()
        val libraryId = upgradeAndGetActiveId()

        assertEquals("journal", File(files.covers.directory(libraryId), "journal").readText())
        assertEquals("cover-1", File(files.covers.directory(libraryId), "entry-1").readText())
        assertTrue(files.covers.root.listFiles()!!.none { it.isFile })
    }

    @Test
    fun issue379_tc56_deletesTheLegacyFiles() = runBlocking {
        files.writeTypicalLegacyInstall()

        assertTrue(files.upgrade(db).ensureDone())

        assertTrue(legacyFiles().none { it.exists() })
    }

    @Test
    fun issue379_tc57a_aCrashBeforeTheImportCommitsLeavesNothingAndTheRetryImportsOnce() = runBlocking {
        files.writeTypicalLegacyInstall()

        assertFalse(files.upgrade(db, beforeImportCommit = { throw IOException("crash") }).ensureDone())

        assertEquals(0, db.countAll("libraries"))
        assertEquals(0, db.countAll("books"))
        assertTrue(legacyFiles().all { it.exists() })

        assertTrue(files.upgrade(db).ensureDone())
        assertFixtureImportedOnce(activeLibraryId())
    }

    @Test
    fun issue379_tc57b_aCrashAfterTheImportCommitsDoesNotImportTwice() = runBlocking {
        files.writeTypicalLegacyInstall()

        assertFalse(files.upgrade(db, adopter = failingTokenAdopter()).ensureDone())

        assertEquals(1, db.countAll("libraries"))
        assertTrue(legacyFiles().all { it.exists() })

        assertTrue(files.upgrade(db).ensureDone())
        assertEquals(1, db.countAll("libraries"))
        assertFixtureImportedOnce(activeLibraryId())
    }

    @Test
    fun issue379_tc57c_aCrashDuringDeletionFinishesWithoutChangingTheData() = runBlocking {
        files.writeTypicalLegacyInstall()
        assertTrue(files.upgrade(db).ensureDone())
        // As if deleting the server file never happened.
        files.writeLegacy(LegacyFiles.SERVER_CONFIGURATION) {
            this[stringPreferencesKey("server_address")] = "example.com:8080/"
        }

        assertTrue(files.upgrade(db).ensureDone())

        assertFixtureImportedOnce(activeLibraryId())
        assertFalse(File(files.legacyDir, LegacyFiles.SERVER_CONFIGURATION).exists())
    }

    @Test
    fun issue379_tc58_theActiveIdWaitsForTheUpgrade() = runBlocking {
        files.writeTypicalLegacyInstall()
        val active = DefaultActiveLibrary(db.librariesDao(), files.upgrade(db))

        val id = active.id()

        assertNotNull(id)
        assertEquals(db.librariesDao().activeId(), id)
    }

    @Test
    fun issue379_tc58_theLibrariesFlowWaitsForTheUpgrade() = runBlocking {
        files.writeTypicalLegacyInstall()
        val repository = files.librariesRepository(db, files.upgrade(db))

        val libraries = repository.getLibrariesFlow().first()

        assertEquals(listOf("example.com:8080"), libraries.map { it.name })
    }

    @Test
    fun issue379_tc59_refusesToCreateALibraryUntilTheUpgradeHasFinished() = runBlocking {
        files.writeTypicalLegacyInstall()
        val adopter = SwitchableTokenAdopter(files.tokens)
        val upgrade = files.upgrade(db, adopter)
        val active = DefaultActiveLibrary(db.librariesDao(), upgrade)
        val repository = files.librariesRepository(db, upgrade, active)
        assertFalse(upgrade.ensureDone())
        val librariesBefore = db.countAll("libraries")

        assertNull(repository.add("https://other.example", activate = true))
        assertNull(active.id())
        assertTrue(repository.getLibrariesFlow().first().isEmpty())
        assertEquals(librariesBefore, db.countAll("libraries"))
        assertTrue(legacyFiles().all { it.exists() })

        adopter.failing = false
        assertTrue(upgrade.ensureDone())
        assertEquals(1, db.countAll("libraries"))
        assertEquals(activeLibraryId(), active.id())
    }

    @Test
    fun issue379_tc60_dropsLegacyDataWhenNoServerWasSaved() = runBlocking {
        files.writeLegacy(LegacyFiles.READING_POSITIONS) {
            this[stringPreferencesKey("position.book-1")] = """{"percent":5,"clientUpdatedAt":1}"""
        }
        files.writeLegacyToken("legacy-token")
        files.covers.root.mkdirs()
        File(files.covers.root, "entry-1").writeText("cover-1")

        assertTrue(files.upgrade(db).ensureDone())

        assertEquals(0, db.countAll("libraries"))
        assertTrue(legacyFiles().none { it.exists() })
        assertFalse(File(files.covers.root, "entry-1").exists())
    }

    @Test
    fun issue379_tc61_aFreshInstallHasNoLibraryUntilOneIsAdded() = runBlocking {
        val upgrade = files.upgrade(db)
        val repository = files.librariesRepository(db, upgrade)

        assertTrue(upgrade.ensureDone())
        assertTrue(repository.getLibrariesFlow().first().isEmpty())
        assertNull(db.librariesDao().activeId())

        repository.add("https://example.com", activate = true)

        val libraries = repository.getLibrariesFlow().first()
        assertEquals(1, libraries.size)
        assertTrue(libraries[0].isActive)
    }

    @Test
    fun issue379_tc62_leavesReaderAndLibraryPreferencesAlone() = runBlocking {
        files.writeTypicalLegacyInstall()
        val readerBytes = UUID.randomUUID().toString().toByteArray()
        val libraryBytes = UUID.randomUUID().toString().toByteArray()
        File(files.legacyDir, "reader_preferences.preferences_pb").writeBytes(readerBytes)
        File(files.legacyDir, "library_preferences.preferences_pb").writeBytes(libraryBytes)

        assertTrue(files.upgrade(db).ensureDone())

        assertArrayEquals(readerBytes, File(files.legacyDir, "reader_preferences.preferences_pb").readBytes())
        assertArrayEquals(libraryBytes, File(files.legacyDir, "library_preferences.preferences_pb").readBytes())
    }

    private fun legacyFiles(): List<File> =
        (LegacyFiles.PER_BOOK + LegacyFiles.SERVER_CONFIGURATION).map { File(files.legacyDir, it) }

    /** Runs the upgrade and returns the id of the library it left active. */
    private suspend fun upgradeAndGetActiveId(): String {
        assertTrue(files.upgrade(db).ensureDone())
        return activeLibraryId()
    }

    private suspend fun activeLibraryId(): String =
        db.librariesDao().activeId() ?: throw AssertionError("no active library")

    /** Each per-book table of the typical install has exactly the rows that install holds, once. */
    private fun assertFixtureImportedOnce(libraryId: String) {
        assertEquals(2, db.rowsInLibrary("books", libraryId))
        assertEquals(2, db.rowsInLibrary("reading_positions", libraryId))
        assertEquals(1, db.rowsInLibrary("listening_positions", libraryId))
        assertEquals(3, db.rowsInLibrary("listening_sessions", libraryId))
        assertEquals(1, db.rowsInLibrary("playback_rates", libraryId))
        assertEquals(1, db.rowsInLibrary("dismissed_resume_offers", libraryId))
    }

    private fun failingTokenAdopter() = object : LegacyTokenAdopter {
        override suspend fun adoptLegacyToken(libraryId: String?) {
            throw IOException("token file unavailable")
        }
    }

    /** Fails the token step while [failing] is true, then delegates to [real]. */
    private class SwitchableTokenAdopter(private val real: LegacyTokenAdopter) : LegacyTokenAdopter {
        var failing = true

        override suspend fun adoptLegacyToken(libraryId: String?) {
            if (failing) throw IOException("token file unavailable")
            real.adoptLegacyToken(libraryId)
        }
    }
}
