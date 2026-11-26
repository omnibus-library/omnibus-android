package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.data.database.insertLibrary
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultLibrariesRepositorySetActiveTest {

    private val db = TestDatabase.inMemory()
    private val files = TestStorageFiles()

    @After
    fun tearDown() {
        db.close()
        files.delete()
    }

    @Test
    fun issue369_tc1_makesOnlyThatLibraryActiveAndLeavesTheCache() = runBlocking {
        db.insertLibrary("a", active = true)
        db.insertLibrary("b")
        db.insertLibrary("c")
        val upgrade = files.upgrade(db)
        val active = DefaultActiveLibrary(db.librariesDao(), upgrade)
        val repository = files.librariesRepository(db, upgrade, active)
        val before = repository.getLibrariesFlow().first()
        assertEquals("a", active.id())

        repository.setActive("b")

        val after = repository.getLibrariesFlow().first()
        assertEquals(
            before.map { library ->
                when (library.id) {
                    "b" -> library.copy(isActive = true, lastUsedAt = TestStorageFiles.NOW)
                    "a" -> library.copy(isActive = false)
                    else -> library
                }
            },
            after,
        )
        assertEquals("a", active.id())
    }

    @Test
    fun issue369_tc2_anUnknownIdChangesNothing() = runBlocking {
        db.insertLibrary("a", active = true)
        db.insertLibrary("b")
        val repository = files.librariesRepository(db, files.upgrade(db))
        val before = repository.getLibrariesFlow().first()

        repository.setActive("missing")

        assertEquals(before, repository.getLibrariesFlow().first())
    }
}
