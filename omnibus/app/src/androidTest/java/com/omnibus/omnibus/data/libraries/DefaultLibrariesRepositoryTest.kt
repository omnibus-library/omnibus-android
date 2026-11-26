package com.omnibus.omnibus.data.libraries

import android.database.sqlite.SQLiteFullException
import com.omnibus.omnibus.data.database.OmnibusDatabase
import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.libraries.local.LibrariesDao
import com.omnibus.omnibus.data.libraries.local.LibraryEntity
import com.omnibus.omnibus.di.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class DefaultLibrariesRepositoryTest {

    private val db = TestDatabase.inMemory()
    private val files = TestStorageFiles()

    @After
    fun tearDown() {
        db.close()
        files.delete()
    }

    @Test
    fun issue379_tc23_addsANamedAndStampedLibrary() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))

        val added = repository.add("  example.com:8080/  ", activate = false)

        assertNotNull(added)
        assertEquals("http://example.com:8080", added!!.address)
        assertEquals("example.com:8080", added.name)
        assertEquals(TestStorageFiles.NOW, added.addedAt)
        assertEquals(TestStorageFiles.NOW, added.lastUsedAt)
        assertNull(added.username)
        assertEquals(added.id, UUID.fromString(added.id).toString())
    }

    @Test
    fun issue379_tc24_activatesOnlyTheLibraryItWasToldTo() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val first = repository.add("https://a.example", activate = true)
        val second = repository.add("https://b.example", activate = true)

        val afterSecond = repository.getActiveLibraryFlow().first()
        val firstStored = repository.getLibrariesFlow().first().single { it.id == first?.id }
        repository.add("https://c.example", activate = false)
        val afterInactive = repository.getActiveLibraryFlow().first()

        assertEquals(second?.id, afterSecond?.id)
        assertFalse(firstStored.isActive)
        assertEquals(second?.id, afterInactive?.id)
    }

    @Test
    fun issue379_tc25_listsLibrariesInTheOrderAdded() = runBlocking {
        var now = 1L
        val upgrade = files.upgrade(db)
        val repository = DefaultLibrariesRepository(
            ioDispatcher = Dispatchers.IO,
            dao = db.librariesDao(),
            activeLibrary = DefaultActiveLibrary(db.librariesDao(), upgrade),
            tokens = files.tokens,
            covers = files.covers,
            upgrade = upgrade,
            clock = TimeSource { now },
        )
        val addresses = listOf("https://a.example", "https://b.example", "https://c.example")
        for (address in addresses) {
            repository.add(address, activate = false)
            now++
        }

        val listed = repository.getLibrariesFlow().first().map { it.address }

        assertEquals(addresses, listed)
    }

    @Test
    fun issue379_tc25_emitsTheNewlyActivatedLibrary() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        repository.add("https://a.example", activate = true)
        val second = repository.add("https://b.example", activate = true)

        val active = repository.getActiveLibraryFlow().first()

        assertEquals(second?.id, active?.id)
    }

    @Test
    fun issue379_tc26_returnsNullWhenTheWriteFails() = runBlocking {
        val upgrade = files.upgrade(db)
        val repository = DefaultLibrariesRepository(
            ioDispatcher = Dispatchers.IO,
            dao = failingWrites(db),
            activeLibrary = DefaultActiveLibrary(db.librariesDao(), upgrade),
            tokens = files.tokens,
            covers = files.covers,
            upgrade = upgrade,
            clock = TimeSource { TestStorageFiles.NOW },
        )

        val added = repository.add("https://example.com", activate = true)

        assertNull(added)
        assertFalse(db.librariesDao().count() > 0)
    }

    /** A DAO whose writes fail with a storage error, as a full disk does. Reads go to [database]. */
    private fun failingWrites(database: OmnibusDatabase): LibrariesDao {
        val real = database.librariesDao()
        return object : LibrariesDao() {
            override fun librariesFlow() = real.librariesFlow()
            override fun activeLibraryFlow() = real.activeLibraryFlow()
            override suspend fun activeLibrary() = real.activeLibrary()
            override suspend fun activeId() = real.activeId()
            override suspend fun count() = real.count()
            override suspend fun insert(library: LibraryEntity) {
                throw SQLiteFullException("disk full")
            }
            override suspend fun deactivateAll() = real.deactivateAll()
            override suspend fun insertActive(library: LibraryEntity) {
                throw SQLiteFullException("disk full")
            }
            override suspend fun delete(id: String) = real.delete(id)
            override suspend fun setUsername(id: String, username: String) = real.setUsername(id, username)
            override suspend fun rename(id: String, name: String) = real.rename(id, name)
            override suspend fun setActive(id: String, now: Long) = real.setActive(id, now)
        }
    }
}
