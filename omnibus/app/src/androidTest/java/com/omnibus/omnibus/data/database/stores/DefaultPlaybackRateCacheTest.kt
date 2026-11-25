package com.omnibus.omnibus.data.database.stores

import android.database.sqlite.SQLiteDiskIOException
import com.omnibus.omnibus.data.audiobook.rate.local.DefaultPlaybackRateCache
import com.omnibus.omnibus.data.audiobook.rate.local.PendingRateRow
import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateDao
import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateEntity
import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.database.insertLibrary
import com.omnibus.omnibus.data.libraries.TestActiveLibrary
import com.omnibus.omnibus.data.libraries.local.BookEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultPlaybackRateCacheTest {

    private val db = TestDatabase.inMemory()
    private val active = TestActiveLibrary()
    private val cache = DefaultPlaybackRateCache(db.playbackRateDao(), active)

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun issue379_tc14_keepsTheNewestCachedAndPendingRateAndOneEntryPerBook() = runBlocking {
        active.current = db.insertLibrary()
        cache.saveLocal(BOOK, 1.0)
        cache.saveLocal(OTHER_BOOK, 2.0)

        cache.saveLocal(BOOK, 1.5)

        assertEquals(1.5, cache.cached(BOOK))
        assertEquals(1.5, cache.pending(BOOK))
        assertEquals(mapOf(BOOK to 1.5, OTHER_BOOK to 2.0), cache.allPending())
    }

    @Test
    fun issue379_tc14_storeServerChangesTheCachedRateAndKeepsThePendingOne() = runBlocking {
        active.current = db.insertLibrary()
        cache.saveLocal(BOOK, 1.0)

        cache.storeServer(BOOK, 2.0)

        assertEquals(2.0, cache.cached(BOOK))
        assertEquals(1.0, cache.pending(BOOK))
    }

    @Test
    fun issue379_tc14_storeServerWithNoRateRemovesTheCachedRateAndKeepsThePendingOne() = runBlocking {
        active.current = db.insertLibrary()
        cache.saveLocal(BOOK, 1.0)

        cache.storeServer(BOOK, null)

        assertNull(cache.cached(BOOK))
        assertEquals(1.0, cache.pending(BOOK))
    }

    @Test
    fun issue379_tc14_clearsThePendingRateWhenItStillEqualsTheSentOne() = runBlocking {
        active.current = db.insertLibrary()
        cache.saveLocal(BOOK, 1.25)

        cache.clearPendingIf(BOOK, 1.25)

        assertNull(cache.pending(BOOK))
    }

    @Test
    fun issue379_tc14_keepsThePendingRateWhenADifferentOneIsCleared() = runBlocking {
        active.current = db.insertLibrary()
        cache.saveLocal(BOOK, 1.25)

        cache.clearPendingIf(BOOK, 1.5)

        assertEquals(1.25, cache.pending(BOOK))
    }

    @Test
    fun issue379_tc17_aRateIsNotVisibleFromAnotherLibrary() = runBlocking {
        val libraryA = db.insertLibrary()
        val libraryB = db.insertLibrary()
        active.current = libraryA
        cache.saveLocal(BOOK, 1.25)
        active.current = libraryB
        assertNull(cache.cached(BOOK))
        cache.saveLocal(BOOK, 2.0)

        active.current = libraryA

        assertEquals(1.25, cache.cached(BOOK))
    }

    @Test
    fun issue379_tc18_readsAreAbsentWithoutAnActiveLibrary() = runBlocking {
        active.current = null
        assertNull(cache.cached(BOOK))
        cache.saveLocal(BOOK, 1.25)

        active.current = db.insertLibrary()

        assertNull(cache.cached(BOOK))
        assertNull(cache.pending(BOOK))
    }

    @Test
    fun issue379_tc19_readsAreAbsentWhenTheDatabaseFails() = runBlocking {
        active.current = db.insertLibrary()
        val failing = DefaultPlaybackRateCache(failingDao(), active)

        assertNull(failing.cached(BOOK))
        assertEquals(emptyMap<String, Double>(), failing.allPending())
    }

    @Test
    fun issue379_tc19_writesThrowNothingWhenTheDatabaseFails() = runBlocking {
        active.current = db.insertLibrary()
        val failing = DefaultPlaybackRateCache(failingDao(), active)

        failing.saveLocal(BOOK, 1.25)
        failing.storeServer(BOOK, 1.5)
    }

    private fun failingDao(): PlaybackRateDao = object : PlaybackRateDao() {
        override suspend fun cached(libraryId: String, bookUuid: String): Double? = disk()

        override suspend fun pending(libraryId: String, bookUuid: String): Double? = disk()

        override suspend fun pendingRates(libraryId: String): List<PendingRateRow> = disk()

        override suspend fun clearPendingIf(libraryId: String, bookUuid: String, rate: Double): Int = disk()

        override suspend fun clearCached(libraryId: String, bookUuid: String): Int = disk()

        override suspend fun updateCached(bookId: Long, rate: Double): Int = disk()

        override suspend fun insertRate(rate: PlaybackRateEntity): Long = disk()

        override suspend fun upsert(rate: PlaybackRateEntity) = disk()

        override suspend fun bookId(libraryId: String, bookUuid: String): Long? = disk()

        override suspend fun insertBook(book: BookEntity): Long = disk()
    }

    private companion object {
        const val BOOK = "book-1"
        const val OTHER_BOOK = "book-2"

        fun disk(): Nothing = throw SQLiteDiskIOException("disk gone")
    }
}
