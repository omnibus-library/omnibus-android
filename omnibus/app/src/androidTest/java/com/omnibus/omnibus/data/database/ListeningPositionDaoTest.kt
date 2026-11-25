package com.omnibus.omnibus.data.database

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningPositionDaoTest {

    private val db = TestDatabase.inMemory()

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun issue379_tc9_removesOnlyTheGivenClientIdsHoweverMany() = runBlocking {
        val library = db.insertLibrary()
        val dao = db.listeningPositionDao()
        // More ids than SQLite takes as bound variables in one statement.
        val removedIds = (1..1200).map { "removed-$it" }
        val keptId = "kept"
        dao.queueSessions(library, (removedIds + keptId).map { listeningSessionReport("book", it) })

        dao.removeSessions(library, removedIds)

        assertEquals(listOf(keptId), dao.sessions(library).map { it.clientId })
    }
}
