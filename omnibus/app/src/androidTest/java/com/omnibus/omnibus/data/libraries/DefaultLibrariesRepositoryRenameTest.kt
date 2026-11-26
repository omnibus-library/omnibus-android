package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.database.TestDatabase
import com.omnibus.omnibus.feature.libraries.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultLibrariesRepositoryRenameTest {

    private val db = TestDatabase.inMemory()
    private val files = TestStorageFiles()

    @After
    fun tearDown() {
        db.close()
        files.delete()
    }

    @Test
    fun issue368_tc17_trimsThenCapsByCodePointWithoutSplittingAnEmoji() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val library = repository.add("https://books.example", activate = false)!!
        val emoji = "📚" // a surrogate pair, one code point

        repository.rename(library.id, "  " + "a".repeat(39) + emoji + "z")

        assertEquals("a".repeat(39) + emoji, repository.getLibrariesFlow().first().single().name)
    }

    @Test
    fun issue368_tc17_keepsTheFirstFortyCharactersOfALongerName() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val library = repository.add("https://books.example", activate = false)!!

        repository.rename(library.id, "b".repeat(41))

        assertEquals("b".repeat(40), repository.getLibrariesFlow().first().single().name)
    }

    @Test
    fun issue368_tc20_storesABlankNameAsEmptyAndShowsTheHost() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val library = repository.add("https://books.example:8443", activate = false)!!

        repository.rename(library.id, "   ")

        val stored = repository.getLibrariesFlow().first().single()
        assertEquals("", stored.name)
        assertEquals("books.example:8443", stored.displayName())
    }

    @Test
    fun issue368_tc21_changesOnlyThatRowsName() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val first = repository.add("https://first.example", activate = false)!!
        repository.add("https://second.example", activate = false)!!
        val before = repository.getLibrariesFlow().first()

        repository.rename(first.id, "Den")

        val after = repository.getLibrariesFlow().first()
        assertEquals(
            before.map { if (it.id == first.id) it.copy(name = "Den") else it },
            after,
        )
    }

    @Test
    fun issue368_tc21_anUnknownIdChangesNothing() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        repository.add("https://first.example", activate = false)!!
        val before = repository.getLibrariesFlow().first()

        repository.rename("no-such-library", "Ghost")

        assertEquals(before, repository.getLibrariesFlow().first())
    }

    @Test
    fun issue368_tc22_bothFlowsEmitTheNewNameForTheActiveLibrary() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val active = repository.add("https://active.example", activate = true)!!
        val other = repository.add("https://other.example", activate = false)!!
        repository.rename(other.id, "Work")
        val listSeen = async(Dispatchers.IO) {
            withTimeout(5_000) {
                repository.getLibrariesFlow().first { list -> list.single { it.id == active.id }.name == "Work" }
            }
        }
        val activeSeen = async(Dispatchers.IO) {
            withTimeout(5_000) {
                repository.getActiveLibraryFlow().first { it?.name == "Work" }
            }
        }

        repository.rename(active.id, "Work")

        val listed = listSeen.await()
        assertEquals(listOf("Work", "Work"), listed.map { it.name })
        assertEquals("Work", activeSeen.await()!!.name)
    }
}
