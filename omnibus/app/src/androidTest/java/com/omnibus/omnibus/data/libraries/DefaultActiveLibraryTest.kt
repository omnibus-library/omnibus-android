package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.database.insertLibrary
import com.omnibus.omnibus.data.libraries.local.LibraryEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class DefaultActiveLibraryTest {

    private val db = TestDatabase.inMemory()
    private val files = TestStorageFiles()

    @After
    fun tearDown() {
        db.close()
        files.delete()
    }

    @Test
    fun issue379_tc20_readsTheDatabaseOnFirstNeedAndDoesNotCacheNull() = runBlocking {
        val active = DefaultActiveLibrary(db.librariesDao(), files.upgrade(db))
        val first = active.id()
        val library = db.insertLibrary(active = true)

        val second = active.id()

        assertNull(first)
        assertEquals(library, second)
    }

    @Test
    fun issue379_tc21_keepsTheFirstIdItFound() = runBlocking {
        val first = db.insertLibrary(active = true)
        val active = DefaultActiveLibrary(db.librariesDao(), files.upgrade(db))
        active.id()
        db.librariesDao().insertActive(
            LibraryEntity(
                id = UUID.randomUUID().toString(),
                address = "https://other.example",
                name = "other",
                username = null,
                addedAt = 0L,
                lastUsedAt = 0L,
                isActive = true,
            ),
        )

        val again = active.id()

        assertEquals(first, again)
    }

    @Test
    fun issue379_tc22_followsAnInProcessAddThatActivates() = runBlocking {
        db.insertLibrary(active = true)
        val upgrade = files.upgrade(db)
        val active = DefaultActiveLibrary(db.librariesDao(), upgrade)
        val repository = files.librariesRepository(db, upgrade, active)
        active.id()

        val added = repository.add("https://other.example", activate = true)

        assertEquals(added?.id, active.id())
    }
}
