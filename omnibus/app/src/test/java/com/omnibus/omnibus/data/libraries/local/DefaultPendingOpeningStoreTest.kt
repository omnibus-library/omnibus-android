package com.omnibus.omnibus.data.libraries.local

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.libraries.PendingOpening
import com.omnibus.omnibus.data.libraries.SwitchKind
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking

/** Runs [block] with a fresh directory holding [FILE_NAME], then deletes the directory. */
private fun withPendingOpeningFile(block: (File) -> Unit) {
    val directory = Files.createTempDirectory("pending-opening").toFile()
    try {
        block(File(directory, FILE_NAME))
    } finally {
        directory.deleteRecursively()
    }
}

/** Opens a store on [file] with its own scope, runs [block] with it, then stops it so the file is released. */
private fun withStore(file: File, block: suspend (DefaultPendingOpeningStore) -> Unit) = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        block(DefaultPendingOpeningStore(ioDispatcher = Dispatchers.IO, scope = scope, file = { file }))
    } finally {
        scope.coroutineContext.job.cancelAndJoin()
    }
}

/** Writes [values] under their names into [file] through a plain DataStore, then stops that DataStore. */
private fun writePreferences(file: File, values: Map<String, String>) = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }).updateData { preferences ->
            preferences.toMutablePreferences().apply {
                values.forEach { (name, value) -> this[stringPreferencesKey(name)] = value }
            }
        }
    } finally {
        scope.coroutineContext.job.cancelAndJoin()
    }
}

private const val FILE_NAME = "pending_opening.preferences_pb"

class DefaultPendingOpeningStoreTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("keeping the pending opening") {

        it("#369 TC-11: reads back the saved opening after the store is reopened") {
            withPendingOpeningFile { file ->
                val opening = PendingOpening("library-b", SwitchKind.Switch)
                withStore(file) { it.save(opening) }

                withStore(file) { it.getFlow().first() shouldBe opening }
            }
        }

        it("#369 TC-11: reads null after the opening is cleared") {
            withPendingOpeningFile { file ->
                withStore(file) {
                    it.save(PendingOpening("library-b", SwitchKind.Switch))

                    it.clear()

                    it.getFlow().first().shouldBeNull()
                }
            }
        }

        it("#369 TC-11: reads null when the saved kind is unknown") {
            withPendingOpeningFile { file ->
                writePreferences(file, mapOf("library_id" to "library-b", "kind" to "Nope"))

                withStore(file) { it.getFlow().first().shouldBeNull() }
            }
        }

        it("#369 TC-11: reads null when the library id is missing") {
            withPendingOpeningFile { file ->
                writePreferences(file, mapOf("kind" to "Switch"))

                withStore(file) { it.getFlow().first().shouldBeNull() }
            }
        }

        it("#369 TC-11: reads null from a corrupt file without throwing") {
            withPendingOpeningFile { file ->
                file.writeText("this is not a preferences file")

                withStore(file) { it.getFlow().first().shouldBeNull() }
            }
        }
    }
})
