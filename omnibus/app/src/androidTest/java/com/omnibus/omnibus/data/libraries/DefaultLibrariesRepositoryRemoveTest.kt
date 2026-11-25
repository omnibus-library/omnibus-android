package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.database.PER_BOOK_TABLES
import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.database.rowsInLibrary
import com.omnibus.omnibus.data.database.seedAllPerBookRows
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultLibrariesRepositoryRemoveTest {

    private val db = TestDatabase.inMemory()
    private val files = TestStorageFiles()

    @After
    fun tearDown() {
        db.close()
        files.delete()
    }

    @Test
    fun issue379_tc65_removesTheLibrarysRowsAndKeepsTheOthers() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val removed = repository.add("https://removed.example", activate = false)!!.id
        val kept = repository.add("https://kept.example", activate = false)!!.id
        db.seedAllPerBookRows(removed, "book-a")
        db.seedAllPerBookRows(kept, "book-b")
        assertEquals(1, db.listeningPositionDao().pendingPositions(removed).size)
        assertEquals(1, db.playbackRateDao().pendingRates(removed).size)

        repository.remove(removed)

        assertEquals(0, db.rowsInLibrary("books", removed))
        for (table in PER_BOOK_TABLES) {
            assertEquals("$table rows of the removed library", 0, db.rowsInLibrary(table, removed))
        }
        assertTrue(db.listeningPositionDao().pendingPositions(removed).isEmpty())
        assertTrue(db.listeningPositionDao().sessions(removed).isEmpty())
        assertTrue(db.playbackRateDao().pendingRates(removed).isEmpty())
        assertEquals(1, db.rowsInLibrary("books", kept))
        for (table in PER_BOOK_TABLES) {
            assertEquals("$table rows of the other library", 1, db.rowsInLibrary(table, kept))
        }
        assertEquals(listOf("book-b"), db.listeningPositionDao().pendingPositions(kept).map { it.bookUuid })
        assertEquals(listOf(kept), repository.getLibrariesFlow().first().map { it.id })
    }

    @Test
    fun issue379_tc66_clearsTheRemovedLibrarysToken() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val removed = repository.add("https://removed.example", activate = false)!!.id
        val kept = repository.add("https://kept.example", activate = false)!!.id
        files.tokens.save(removed, "token-a")
        files.tokens.save(kept, "token-b")

        repository.remove(removed)

        assertNull(files.tokens.getTokenFlow(removed).first())
        assertEquals("token-b", files.tokens.getTokenFlow(kept).first())
    }

    @Test
    fun issue379_tc67_deletesTheRemovedLibrarysCovers() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val removed = repository.add("https://removed.example", activate = false)!!.id
        val kept = repository.add("https://kept.example", activate = false)!!.id
        val removedCovers = files.covers.directory(removed).apply { mkdirs() }
        File(removedCovers, "entry-a").writeText("cover-a")
        val keptCovers = files.covers.directory(kept).apply { mkdirs() }
        File(keptCovers, "entry-b").writeText("cover-b")

        repository.remove(removed)

        assertFalse(removedCovers.exists())
        assertEquals("cover-b", File(keptCovers, "entry-b").readText())
    }

    @Test
    fun issue379_tc68_leavesNoActiveLibraryAfterRemovingTheActiveOne() = runBlocking {
        val upgrade = files.upgrade(db)
        val active = DefaultActiveLibrary(db.librariesDao(), upgrade)
        val repository = files.librariesRepository(db, upgrade, active)
        val removed = repository.add("https://removed.example", activate = true)!!.id
        assertEquals(removed, active.id())

        repository.remove(removed)

        assertNull(repository.getActiveLibraryFlow().first())
        assertNull(active.id())
    }
}
