package com.omnibus.omnibus.data.database.stores

import android.database.sqlite.SQLiteDiskIOException
import com.omnibus.omnibus.data.audiobook.crossformat.local.DefaultCrossFormatDismissStore
import com.omnibus.omnibus.data.audiobook.crossformat.local.DismissedResumeOfferDao
import com.omnibus.omnibus.data.audiobook.crossformat.local.DismissedResumeOfferEntity
import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.database.insertLibrary
import com.omnibus.omnibus.data.libraries.TestActiveLibrary
import com.omnibus.omnibus.data.libraries.local.BookEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultCrossFormatDismissStoreTest {

    private val db = TestDatabase.inMemory()
    private val active = TestActiveLibrary()
    private val store = DefaultCrossFormatDismissStore(db.dismissedResumeOfferDao(), active)

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun issue379_tc15_keepsTheLatestDismissedClockOfABook() = runBlocking {
        active.current = db.insertLibrary()
        store.store(BOOK, 1L)

        store.store(BOOK, 2L)

        assertEquals(2L, store.dismissedClock(BOOK))
    }

    @Test
    fun issue379_tc15_readsNullForABookNeverDismissed() = runBlocking {
        active.current = db.insertLibrary()

        assertNull(store.dismissedClock(BOOK))
    }

    @Test
    fun issue379_tc17_aDismissalIsNotVisibleFromAnotherLibrary() = runBlocking {
        val libraryA = db.insertLibrary()
        val libraryB = db.insertLibrary()
        active.current = libraryA
        store.store(BOOK, 1L)
        active.current = libraryB
        assertNull(store.dismissedClock(BOOK))
        store.store(BOOK, 2L)

        active.current = libraryA

        assertEquals(1L, store.dismissedClock(BOOK))
    }

    @Test
    fun issue379_tc18_readsAreAbsentWithoutAnActiveLibrary() = runBlocking {
        active.current = null
        store.store(BOOK, 1L)

        active.current = db.insertLibrary()

        assertNull(store.dismissedClock(BOOK))
    }

    @Test
    fun issue379_tc19_readsAreAbsentWhenTheDatabaseFails() = runBlocking {
        active.current = db.insertLibrary()
        val failing = DefaultCrossFormatDismissStore(failingDao(), active)

        assertNull(failing.dismissedClock(BOOK))
    }

    @Test
    fun issue379_tc19_writesThrowNothingWhenTheDatabaseFails() = runBlocking {
        active.current = db.insertLibrary()
        val failing = DefaultCrossFormatDismissStore(failingDao(), active)

        failing.store(BOOK, 1L)
    }

    private fun failingDao(): DismissedResumeOfferDao = object : DismissedResumeOfferDao() {
        override suspend fun dismissedClock(libraryId: String, bookUuid: String): Long? = disk()

        override suspend fun upsert(offer: DismissedResumeOfferEntity) = disk()

        override suspend fun bookId(libraryId: String, bookUuid: String): Long? = disk()

        override suspend fun insertBook(book: BookEntity): Long = disk()
    }

    private companion object {
        const val BOOK = "book-1"

        fun disk(): Nothing = throw SQLiteDiskIOException("disk gone")
    }
}
