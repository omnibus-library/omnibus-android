package com.omnibus.omnibus.data.database

import com.omnibus.omnibus.data.libraries.local.BookDao
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class OmnibusDatabaseCascadeTest {

    private val db = TestDatabase.inMemory()

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun issue379_tc2_deletingALibraryDeletesItsBooksAndTheirRows() = runBlocking {
        val deleted = db.insertLibrary()
        val kept = db.insertLibrary()
        db.seedAllPerBookRows(deleted, "book-1")
        db.seedAllPerBookRows(deleted, "book-2")
        db.seedAllPerBookRows(kept, "book-1")

        db.openHelper.writableDatabase.execSQL("DELETE FROM libraries WHERE id = ?", arrayOf<Any>(deleted))

        for (table in listOf("books") + PER_BOOK_TABLES) {
            assertEquals("$table rows of the deleted library", 0, db.rowsInLibrary(table, deleted))
            assertEquals("$table rows of the other library", 1, db.rowsInLibrary(table, kept))
        }
    }

    @Test
    fun issue379_tc3_deletingABookDeletesOnlyItsRows() = runBlocking {
        val library = db.insertLibrary()
        db.seedAllPerBookRows(library, "book-1")
        db.seedAllPerBookRows(library, "book-2")
        val bookId = db.bookDao().findOrCreate(library, "book-1")

        db.openHelper.writableDatabase.execSQL("DELETE FROM books WHERE id = ?", arrayOf<Any>(bookId))

        for (table in PER_BOOK_TABLES) {
            assertEquals("$table rows of the deleted book", 0, db.rowsOfBook(table, "book-1"))
            assertEquals("$table rows of the other book", 1, db.rowsOfBook(table, "book-2"))
        }
    }
}
