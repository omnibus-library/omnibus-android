package com.omnibus.omnibus.feature.libraries.remove

import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.database.OmnibusDatabase
import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.database.insertLibrary
import com.omnibus.omnibus.data.database.listeningSessionReport
import com.omnibus.omnibus.data.libraries.RoomInactiveLibraryRows
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

private const val GIVEN = "given"
private const val OTHER = "other"

/** The same book uuid in both libraries, so a leak across libraries would show. */
private const val BOOK = "book"

/** Gives [libraryId] one pending position, one queued session and one pending rate, all for [BOOK]. */
private suspend fun OmnibusDatabase.seedPendingRows(libraryId: String, positionSeconds: Double, rate: Double) {
    listeningPositionDao().save(libraryId, BOOK, seconds = positionSeconds, fileId = 3L, clientUpdatedAt = 4L)
    listeningPositionDao().queueSessions(
        libraryId,
        listOf(listeningSessionReport(BOOK, clientId = "$libraryId-session")),
    )
    playbackRateDao().saveLocal(libraryId, BOOK, rate = rate)
}

class RoomInactiveLibraryRowsTest {

    private val db = TestDatabase.inMemory()

    private val rows = RoomInactiveLibraryRows(db.listeningPositionDao(), db.playbackRateDao())

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun issue373_tc20_readsOnlyTheGivenLibrarysPendingRows() = runBlocking {
        db.insertLibrary(id = GIVEN)
        db.insertLibrary(id = OTHER)
        db.seedPendingRows(GIVEN, positionSeconds = 12.0, rate = 2.0)
        db.seedPendingRows(OTHER, positionSeconds = 99.0, rate = 3.0)

        assertEquals(listOf(AudioPosition(BOOK, 12.0, 3L, 4L)), rows.pendingPositions(GIVEN))
        assertEquals(listOf("given-session"), rows.sessions(GIVEN).map { it.clientId })
        assertEquals(mapOf(BOOK to 2.0), rows.pendingRates(GIVEN))
    }

    @Test
    fun issue373_tc20_returnsTheGivenLibrarysSessionsInQueueOrder() = runBlocking {
        db.insertLibrary(id = GIVEN)
        db.insertLibrary(id = OTHER)
        db.listeningPositionDao().queueSessions(GIVEN, listOf(listeningSessionReport(BOOK, clientId = "first")))
        db.listeningPositionDao().queueSessions(OTHER, listOf(listeningSessionReport(BOOK, clientId = "other")))
        db.listeningPositionDao().queueSessions(GIVEN, listOf(listeningSessionReport(BOOK, clientId = "second")))

        assertEquals(listOf("first", "second"), rows.sessions(GIVEN).map { it.clientId })
    }

    @Test
    fun issue373_tc20_clearsOnlyTheGivenLibrarysPosition() = runBlocking {
        db.insertLibrary(id = GIVEN)
        db.insertLibrary(id = OTHER)
        db.seedPendingRows(GIVEN, positionSeconds = 12.0, rate = 2.0)
        db.seedPendingRows(OTHER, positionSeconds = 99.0, rate = 3.0)

        rows.clearPosition(GIVEN, AudioPosition(BOOK, 12.0, 3L, 4L))

        assertEquals(emptyList<AudioPosition>(), rows.pendingPositions(GIVEN))
        assertEquals(listOf(AudioPosition(BOOK, 99.0, 3L, 4L)), rows.pendingPositions(OTHER))
    }

    @Test
    fun issue373_tc20_removesOnlyTheGivenLibrarysSessions() = runBlocking {
        db.insertLibrary(id = GIVEN)
        db.insertLibrary(id = OTHER)
        db.seedPendingRows(GIVEN, positionSeconds = 12.0, rate = 2.0)
        db.seedPendingRows(OTHER, positionSeconds = 99.0, rate = 3.0)

        rows.removeSessions(GIVEN, setOf("given-session", "other-session"))

        assertEquals(emptyList<String>(), rows.sessions(GIVEN).map { it.clientId })
        assertEquals(listOf("other-session"), rows.sessions(OTHER).map { it.clientId })
    }

    @Test
    fun issue373_tc20_clearsOnlyTheGivenLibrarysRate() = runBlocking {
        db.insertLibrary(id = GIVEN)
        db.insertLibrary(id = OTHER)
        db.seedPendingRows(GIVEN, positionSeconds = 12.0, rate = 2.0)
        db.seedPendingRows(OTHER, positionSeconds = 99.0, rate = 3.0)

        rows.clearRate(GIVEN, BOOK, 2.0)

        assertEquals(emptyMap<String, Double>(), rows.pendingRates(GIVEN))
        assertEquals(mapOf(BOOK to 3.0), rows.pendingRates(OTHER))
    }

    @Test
    fun issue373_tc20_leavesAPositionThatChangedSinceItWasRead() = runBlocking {
        db.insertLibrary(id = GIVEN)
        db.seedPendingRows(GIVEN, positionSeconds = 12.0, rate = 2.0)

        rows.clearPosition(GIVEN, AudioPosition(BOOK, 1.0, 3L, 4L))

        assertEquals(listOf(AudioPosition(BOOK, 12.0, 3L, 4L)), rows.pendingPositions(GIVEN))
    }

    @Test
    fun issue373_tc20_leavesARateThatChangedSinceItWasRead() = runBlocking {
        db.insertLibrary(id = GIVEN)
        db.seedPendingRows(GIVEN, positionSeconds = 12.0, rate = 2.0)

        rows.clearRate(GIVEN, BOOK, 1.5)

        assertEquals(mapOf(BOOK to 2.0), rows.pendingRates(GIVEN))
    }
}
