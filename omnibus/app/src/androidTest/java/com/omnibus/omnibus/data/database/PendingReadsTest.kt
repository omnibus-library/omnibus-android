package com.omnibus.omnibus.data.database

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingReadsTest {

    private val db = TestDatabase.inMemory()

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun issue379_tc10_readsOneLibrarysPendingValuesByItsId() = runBlocking {
        db.insertLibrary(active = true)
        val other = db.insertLibrary()
        // The same book uuid in both libraries, with different values, so a leak would show.
        db.listeningPositionDao().save(other, "book", seconds = 12.0, fileId = 3L, clientUpdatedAt = 4L)
        db.listeningPositionDao().queueSessions(other, listOf(listeningSessionReport("book")))
        db.playbackRateDao().saveLocal(other, "book", rate = 2.0)
        db.listeningPositionDao().save(db.insertLibrary(), "book", seconds = 99.0, fileId = 9L, clientUpdatedAt = 9L)

        val positions = db.listeningPositionDao().pendingPositions(other)
        val sessions = db.listeningPositionDao().sessions(other)
        val rates = db.playbackRateDao().pendingRates(other)

        assertEquals(listOf("book" to 12.0), positions.map { it.bookUuid to it.positionSeconds })
        assertEquals(listOf("book"), sessions.map { it.bookUuid })
        assertEquals(listOf("book" to 2.0), rates.map { it.bookUuid to it.pendingRate })
    }
}
