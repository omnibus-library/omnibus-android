package com.omnibus.omnibus.data.libraries.local

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultLibraryTokenStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File(context.cacheDir, "tokens-${UUID.randomUUID()}")

    @After
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun issue379_tc30_keepsOneTokenPerLibrary() = runBlocking {
        directory.mkdirs()
        val store = DefaultLibraryTokenStore(context, Dispatchers.IO) { File(directory, "auth_token.preferences_pb") }
        val libraryA = UUID.randomUUID().toString()
        val libraryB = UUID.randomUUID().toString()
        store.save(libraryA, "token-a")
        store.save(libraryB, "token-b")

        store.clear(libraryA)

        assertNull(store.getTokenFlow(libraryA).first())
        assertEquals("token-b", store.getTokenFlow(libraryB).first())
    }
}
