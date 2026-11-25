package com.omnibus.omnibus.data.database.stores

import android.database.sqlite.SQLiteDiskIOException
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.local.DefaultAudioPositionStore
import com.omnibus.omnibus.data.audiobook.position.local.ListeningPositionDao
import com.omnibus.omnibus.data.audiobook.position.local.ListeningPositionEntity
import com.omnibus.omnibus.data.audiobook.position.local.ListeningSessionEntity
import com.omnibus.omnibus.data.audiobook.position.local.ListeningPositionRow
import com.omnibus.omnibus.data.audiobook.position.local.ListeningSessionRow
import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.database.insertLibrary
import com.omnibus.omnibus.data.database.listeningSessionReport
import com.omnibus.omnibus.data.libraries.TestActiveLibrary
import com.omnibus.omnibus.data.libraries.local.BookEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultAudioPositionStoreTest {

    private val db = TestDatabase.inMemory()
    private val active = TestActiveLibrary()
    private val store = DefaultAudioPositionStore(db.listeningPositionDao(), active)

    @After
    fun closeDatabase() {
        db.close()
    }

    @Test
    fun issue379_tc13_keepsTheNewestCachedAndPendingPositionAndOneEntryPerBook() = runBlocking {
        active.current = db.insertLibrary()
        val other = position(OTHER_BOOK, seconds = 5.0, clientUpdatedAt = 1L)
        store.save(position(BOOK, seconds = 10.0, clientUpdatedAt = 1L))
        store.save(other)
        val newest = position(BOOK, seconds = 20.0, clientUpdatedAt = 2L)

        store.save(newest)

        assertEquals(newest, store.cached(BOOK))
        assertEquals(newest, store.pending(BOOK))
        assertEquals(setOf(newest, other), store.allPending().toSet())
        assertEquals(2, store.allPending().size)
    }

    @Test
    fun issue379_tc13_clearsThePendingPositionWhenItStillEqualsTheSentOne() = runBlocking {
        active.current = db.insertLibrary()
        val sent = position(BOOK, seconds = 10.0, clientUpdatedAt = 1L)
        store.save(sent)

        store.clearPendingIf(sent)

        assertNull(store.pending(BOOK))
        assertEquals(sent, store.cached(BOOK))
    }

    @Test
    fun issue379_tc13_keepsANewerPendingPositionWhenAnOlderOneIsCleared() = runBlocking {
        active.current = db.insertLibrary()
        val older = position(BOOK, seconds = 10.0, clientUpdatedAt = 1L)
        val newer = position(BOOK, seconds = 20.0, clientUpdatedAt = 2L)
        store.save(older)
        store.save(newer)

        store.clearPendingIf(older)

        assertEquals(newer, store.pending(BOOK))
    }

    @Test
    fun issue379_tc13_queuesSessionsInOrderAndIgnoresARepeatedClientId() = runBlocking {
        active.current = db.insertLibrary()
        val first = listeningSessionReport(BOOK, "session-1")
        val second = listeningSessionReport(BOOK, "session-2")
        val third = listeningSessionReport(OTHER_BOOK, "session-3")
        store.queueSessions(listOf(first, second))

        store.queueSessions(listOf(second, third))

        assertEquals(listOf("session-1", "session-2", "session-3"), store.sessions().map { it.clientId })
    }

    @Test
    fun issue379_tc13_removesOnlyTheGivenSessions() = runBlocking {
        active.current = db.insertLibrary()
        val kept = listeningSessionReport(BOOK, "session-1")
        val removed = listeningSessionReport(BOOK, "session-2")
        store.queueSessions(listOf(kept, removed))

        store.removeSessions(setOf(removed.clientId))

        assertEquals(listOf(kept), store.sessions())
    }

    @Test
    fun issue379_tc13_keepsValuesAcrossReopeningTheDatabase() = runBlocking {
        val name = "audio-store-${UUID.randomUUID()}.db"
        try {
            val first = TestDatabase.onDisk(name)
            val library = first.insertLibrary()
            val saved = position(BOOK, seconds = 33.0, clientUpdatedAt = 9L)
            DefaultAudioPositionStore(first.listeningPositionDao(), TestActiveLibrary(library)).save(saved)
            first.close()

            val reopened = TestDatabase.onDisk(name)
            val after = DefaultAudioPositionStore(reopened.listeningPositionDao(), TestActiveLibrary(library))
            assertEquals(saved, after.cached(BOOK))
            reopened.close()
        } finally {
            InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name)
        }
    }

    @Test
    fun issue379_tc17_aPositionIsNotVisibleFromAnotherLibrary() = runBlocking {
        val libraryA = db.insertLibrary()
        val libraryB = db.insertLibrary()
        active.current = libraryA
        store.save(position(BOOK, seconds = 10.0, clientUpdatedAt = 1L))
        active.current = libraryB
        assertNull(store.cached(BOOK))
        store.save(position(BOOK, seconds = 99.0, clientUpdatedAt = 2L))

        active.current = libraryA

        assertEquals(10.0, store.cached(BOOK)?.positionSeconds)
    }

    @Test
    fun issue379_tc18_readsAreAbsentWithoutAnActiveLibrary() = runBlocking {
        active.current = null
        assertNull(store.cached(BOOK))
        store.save(position(BOOK, seconds = 10.0, clientUpdatedAt = 1L))

        active.current = db.insertLibrary()

        assertNull(store.cached(BOOK))
    }

    @Test
    fun issue379_tc19_readsAreAbsentWhenTheDatabaseFails() = runBlocking {
        active.current = db.insertLibrary()
        val failing = DefaultAudioPositionStore(failingDao(), active)

        assertNull(failing.cached(BOOK))
        assertNull(failing.pending(BOOK))
        assertEquals(emptyList<AudioPosition>(), failing.allPending())
    }

    @Test
    fun issue379_tc19_writesThrowNothingWhenTheDatabaseFails() = runBlocking {
        active.current = db.insertLibrary()
        val failing = DefaultAudioPositionStore(failingDao(), active)

        failing.save(position(BOOK, seconds = 10.0, clientUpdatedAt = 1L))
        failing.queueSessions(listOf(listeningSessionReport(BOOK)))
    }

    private fun failingDao(): ListeningPositionDao = object : ListeningPositionDao() {
        override suspend fun cached(libraryId: String, bookUuid: String): ListeningPositionRow? = disk()

        override suspend fun pending(libraryId: String, bookUuid: String): ListeningPositionRow? = disk()

        override suspend fun pendingPositions(libraryId: String): List<ListeningPositionRow> = disk()

        override suspend fun clearPendingIf(
            libraryId: String,
            bookUuid: String,
            seconds: Double,
            fileId: Long,
            clientUpdatedAt: Long,
        ): Int = disk()

        override suspend fun upsert(position: ListeningPositionEntity) =
            disk()

        override suspend fun sessions(libraryId: String): List<ListeningSessionRow> = disk()

        override suspend fun deleteSessions(libraryId: String, clientIds: List<String>) = disk()

        override suspend fun maxQueueOrder(): Long = disk()

        override suspend fun insertSession(session: ListeningSessionEntity): Long =
            disk()

        override suspend fun bookId(libraryId: String, bookUuid: String): Long? = disk()

        override suspend fun insertBook(book: BookEntity): Long = disk()
    }

    private fun position(bookUuid: String, seconds: Double, clientUpdatedAt: Long) =
        AudioPosition(bookUuid, positionSeconds = seconds, bookFileId = 1L, clientUpdatedAt = clientUpdatedAt)

    private companion object {
        const val BOOK = "book-1"
        const val OTHER_BOOK = "book-2"

        fun disk(): Nothing = throw SQLiteDiskIOException("disk gone")
    }
}
