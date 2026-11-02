package com.omnibus.omnibus.data.reader.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

class DefaultReaderSettingsStoreTest : DescribeSpec({

    lateinit var dataStoreDir: File
    lateinit var dataStoreScope: CoroutineScope
    lateinit var dataStore: DataStore<Preferences>
    lateinit var store: DefaultReaderSettingsStore

    fun openDataStore(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(dataStoreDir, "reader_preferences.preferences_pb") },
    )

    /** Replaces the stored value of `reader.settings` with the raw [blob]. */
    suspend fun seed(blob: String) {
        dataStore.updateData {
            mutablePreferencesOf().apply { this[DefaultReaderSettingsStore.SETTINGS_KEY] = blob }
        }
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        dataStoreDir = createTempDirectory("reader-settings-test").toFile()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = openDataStore(dataStoreScope)
        store = DefaultReaderSettingsStore(dataStore)
    }

    afterEach {
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
    }

    describe("settings") {

        it("emits the defaults when nothing is stored, then the saved value, then the latest one") {
            store.settings.first() shouldBe ReaderSettings()

            val first = ReaderSettings(theme = ReaderTheme.Sepia, fontSize = 24)
            store.save(first)
            store.settings.first() shouldBe first

            val second = ReaderSettings(typeface = ReaderTypeface.Mono, margins = ReaderMargins.Wide)
            store.save(second)
            store.settings.first() shouldBe second
        }

        it("reads a stored blob with a missing key and an extra key tolerantly") {
            seed("""{"fontSize":24,"theme":"sepia","extra":{"a":1}}""")

            store.settings.first() shouldBe ReaderSettings(theme = ReaderTheme.Sepia, fontSize = 24)
        }

        it("keeps one global value under one key, with no book in it") {
            val settings = ReaderSettings(theme = ReaderTheme.Light)
            store.save(settings)

            // Only one DataStore may be open on the file at a time, so the first is closed before reopening.
            dataStoreScope.cancel()
            val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val reopened = openDataStore(reopenedScope)

                DefaultReaderSettingsStore(reopened).settings.first() shouldBe settings
                reopened.data.first().asMap().keys.map { it.name } shouldContainExactly listOf("reader.settings")
            } finally {
                reopenedScope.cancel()
            }
        }

        it("emits the defaults and completes when the read throws an IOException") {
            val failing = DefaultReaderSettingsStore(
                object : DataStore<Preferences> {
                    override val data = flow<Preferences> { throw IOException("boom") }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IOException("boom")
                },
            )

            failing.settings.toList() shouldBe listOf(ReaderSettings())
        }

        it("still propagates a read failure that is not an IOException") {
            val failing = DefaultReaderSettingsStore(
                object : DataStore<Preferences> {
                    override val data = flow<Preferences> { throw IllegalStateException("bug") }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IllegalStateException("bug")
                },
            )

            shouldThrow<IllegalStateException> { failing.settings.toList() }
        }
    }

    describe("save") {

        it("does not throw when the write fails with an IOException") {
            val failing = DefaultReaderSettingsStore(
                object : DataStore<Preferences> {
                    override val data = flow<Preferences> { emit(mutablePreferencesOf()) }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IOException("boom")
                },
            )

            shouldNotThrowAny { failing.save(ReaderSettings(fontSize = 22)) }
        }
    }
})
