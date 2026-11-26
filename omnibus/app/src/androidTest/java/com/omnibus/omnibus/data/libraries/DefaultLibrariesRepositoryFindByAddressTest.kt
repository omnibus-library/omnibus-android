package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.database.TestDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultLibrariesRepositoryFindByAddressTest {

    private val db = TestDatabase.inMemory()
    private val files = TestStorageFiles()

    @After
    fun tearDown() {
        db.close()
        files.delete()
    }

    @Test
    fun issue371_tc23_matchesTheNormalizedAddressIgnoringHostCase() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        val home = checkNotNull(repository.add("https://books.home.lan", activate = false))
        val cabin = checkNotNull(repository.add("http://10.0.0.12:3000", activate = true))
        val before = repository.getLibrariesFlow().first()

        assertEquals(home.id, repository.findByAddress("https://books.home.lan")?.id)
        assertEquals(home.id, repository.findByAddress("HTTPS://Books.Home.LAN/")?.id)
        assertEquals(home.id, repository.findByAddress("https://books.home.lan:443")?.id)
        assertEquals(cabin.id, repository.findByAddress("10.0.0.12:3000")?.id)
        assertEquals(before, repository.getLibrariesFlow().first())
    }

    @Test
    fun issue371_tc24_returnsNullWhenNothingMatches() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))
        repository.add("https://books.home.lan/lib", activate = false)
        val before = repository.getLibrariesFlow().first()

        val misses = listOf(
            "https://other.lan",
            "https://books.home.lan:8443",
            "http://books.home.lan",
            "https://books.home.lan/Lib",
            "http://",
        )
        misses.forEach { address ->
            assertNull(address, repository.findByAddress(address))
        }
        assertEquals(before, repository.getLibrariesFlow().first())
    }

    @Test
    fun issue371_tc24_returnsNullOnAnEmptyTable() = runBlocking {
        val repository = files.librariesRepository(db, files.upgrade(db))

        assertNull(repository.findByAddress("https://books.home.lan"))
    }
}
