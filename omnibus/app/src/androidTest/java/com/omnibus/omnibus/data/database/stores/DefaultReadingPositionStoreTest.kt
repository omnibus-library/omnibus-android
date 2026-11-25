package com.omnibus.omnibus.data.database.stores

import android.database.sqlite.SQLiteDiskIOException
import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.database.insertLibrary
import com.omnibus.omnibus.data.libraries.TestActiveLibrary
import com.omnibus.omnibus.data.libraries.local.BookEntity
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.progress.local.DefaultReadingPositionStore
import com.omnibus.omnibus.data.progress.local.ReadingPositionDao
import com.omnibus.omnibus.data.progress.local.ReadingPositionEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultReadingPositionStoreTest {

    private val db = TestDatabase.inMemory()
    private val active = TestActiveLibrary()
    private val store = DefaultReadingPositionStore(db.readingPositionDao(), active)

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun issue379_tc12_savesAndReadsBackEachShapeOfPosition() = runBlocking {
        active.current = db.insertLibrary()
        val positions = listOf(
            ReadingPosition(BOOK, cfi = "epubcfi(/6/2)", percent = 40, clientUpdatedAt = 1L),
            ReadingPosition(BOOK, cfi = "epubcfi(/6/4)", percent = null, clientUpdatedAt = 2L),
            ReadingPosition(BOOK, cfi = null, percent = 75, clientUpdatedAt = 3L),
        )

        for (position in positions) {
            store.save(position)
            assertEquals(position, store.getPositionFlow(BOOK).first())
        }
    }

    @Test
    fun issue379_tc12_readsTheLastWriteForABook() = runBlocking {
        active.current = db.insertLibrary()
        store.save(ReadingPosition(BOOK, cfi = "epubcfi(/6/2)", percent = 10, clientUpdatedAt = 1L))
        val last = ReadingPosition(BOOK, cfi = "epubcfi(/6/8)", percent = 90, clientUpdatedAt = 2L)

        store.save(last)

        assertEquals(last, store.getPositionFlow(BOOK).first())
    }

    @Test
    fun issue379_tc12_readsNullForABookNeverSaved() = runBlocking {
        active.current = db.insertLibrary()

        assertNull(store.getPositionFlow(BOOK).first())
    }

    @Test
    fun issue379_tc17_aPositionIsNotVisibleFromAnotherLibrary() = runBlocking {
        val libraryA = db.insertLibrary()
        val libraryB = db.insertLibrary()
        active.current = libraryA
        store.save(ReadingPosition(BOOK, cfi = "cfi-a", percent = 10, clientUpdatedAt = 1L))
        active.current = libraryB
        assertNull(store.getPositionFlow(BOOK).first())
        store.save(ReadingPosition(BOOK, cfi = "cfi-b", percent = 20, clientUpdatedAt = 2L))

        active.current = libraryA

        assertEquals("cfi-a", store.getPositionFlow(BOOK).first()?.cfi)
    }

    @Test
    fun issue379_tc18_readsAreAbsentWithoutAnActiveLibrary() = runBlocking {
        active.current = null
        assertNull(store.getPositionFlow(BOOK).first())
        store.save(ReadingPosition(BOOK, cfi = "cfi", percent = 10, clientUpdatedAt = 1L))

        active.current = db.insertLibrary()

        assertNull(store.getPositionFlow(BOOK).first())
    }

    @Test
    fun issue379_tc19_readsAreAbsentWhenTheDatabaseFails() = runBlocking {
        active.current = db.insertLibrary()
        val failing = DefaultReadingPositionStore(failingDao(), active)

        assertEquals(listOf<ReadingPosition?>(null), failing.getPositionFlow(BOOK).toList())
    }

    @Test
    fun issue379_tc19_writesThrowNothingWhenTheDatabaseFails() = runBlocking {
        active.current = db.insertLibrary()
        val failing = DefaultReadingPositionStore(failingDao(), active)

        failing.save(ReadingPosition(BOOK, cfi = "cfi", percent = 10, clientUpdatedAt = 1L))
    }

    private fun failingDao(): ReadingPositionDao = object : ReadingPositionDao() {
        override fun positionFlow(libraryId: String, bookUuid: String): Flow<ReadingPositionEntity?> =
            flow { disk() }

        override suspend fun bookId(libraryId: String, bookUuid: String): Long? = disk()

        override suspend fun insertBook(book: BookEntity): Long = disk()

        override suspend fun upsert(position: ReadingPositionEntity) = disk()
    }

    private companion object {
        const val BOOK = "book-1"

        fun disk(): Nothing = throw SQLiteDiskIOException("disk gone")
    }
}
