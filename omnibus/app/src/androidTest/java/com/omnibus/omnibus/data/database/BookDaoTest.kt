package com.omnibus.omnibus.data.database

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BookDaoTest {

    private val db = TestDatabase.inMemory()

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun issue379_tc5_findOrCreateReturnsTheSameBookForTheSameLibrary() = runBlocking {
        val library = db.insertLibrary()
        val dao = db.bookDao()
        val first = dao.findOrCreate(library, "book")

        val second = dao.findOrCreate(library, "book")

        assertEquals(first, second)
    }

    @Test
    fun issue379_tc5_findOrCreateCreatesABookPerLibrary() = runBlocking {
        val library = db.insertLibrary()
        val otherLibrary = db.insertLibrary()
        val dao = db.bookDao()
        val first = dao.findOrCreate(library, "book")

        val inOtherLibrary = dao.findOrCreate(otherLibrary, "book")

        assertNotEquals(first, inOtherLibrary)
    }
}
