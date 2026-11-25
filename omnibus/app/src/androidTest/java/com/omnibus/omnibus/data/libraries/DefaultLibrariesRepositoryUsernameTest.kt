package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.database.TestDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultLibrariesRepositoryUsernameTest {

    private val db = TestDatabase.inMemory()
    private val files = TestStorageFiles()

    @After
    fun tearDown() {
        db.close()
        files.delete()
    }

    @Test
    fun issue366_tc17_setsOnlyThatLibrarysUsername() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val first = repository.add("https://first.example", activate = false)!!
        repository.add("https://second.example", activate = false)!!
        val before = repository.getLibrariesFlow().first()

        repository.setUsername(first.id, "ana")

        val after = repository.getLibrariesFlow().first()
        assertEquals(
            before.map { if (it.id == first.id) it.copy(username = "ana") else it },
            after,
        )
    }

    @Test
    fun issue366_tc18_flowsSeeTheUsernameAndAnUnknownIdChangesNothing() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val active = repository.add("https://active.example", activate = true)!!
        repository.add("https://other.example", activate = false)!!
        val listSeen = async(Dispatchers.IO) {
            withTimeout(5_000) {
                repository.getLibrariesFlow().first { list -> list.firstOrNull { it.id == active.id }?.username == "ana" }
            }
        }
        val activeSeen = async(Dispatchers.IO) {
            withTimeout(5_000) {
                repository.getActiveLibraryFlow().first { it?.username == "ana" }
            }
        }

        repository.setUsername(active.id, "ana")

        assertEquals("ana", listSeen.await().single { it.id == active.id }.username)
        assertEquals("ana", activeSeen.await()!!.username)
        val listBeforeUnknown = repository.getLibrariesFlow().first()
        repository.setUsername("no-such-library", "ben")
        assertEquals(listBeforeUnknown, repository.getLibrariesFlow().first())
    }
}
