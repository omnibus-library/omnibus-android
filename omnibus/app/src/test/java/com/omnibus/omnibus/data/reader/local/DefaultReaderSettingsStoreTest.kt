package com.omnibus.omnibus.data.reader.local

import app.cash.turbine.test
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.reader.ReaderPageTurn
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.randomReaderSettings
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job

/** An in-memory DataStore, optionally holding [initial]. */
private class TestPreferencesDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** A DataStore whose every read and write fails with [error]. */
private class FailingDataStore(private val error: Exception) : DataStore<Preferences> {

    override val data: Flow<Preferences> = flow { throw error }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw error
}

class DefaultReaderSettingsStoreTest : DescribeSpec({

    fun sut(dataStore: DataStore<Preferences> = TestPreferencesDataStore()): DefaultReaderSettingsStore =
        DefaultReaderSettingsStore(dataStore)

    /** [settings] as they come back from storage, where spread is not kept. */
    fun stored(settings: ReaderSettings): ReaderSettings = settings.copy(spread = ReaderSettings().spread)

    /** Saves [settings] to a DataStore file, restarts the store on that file, and returns what it reads back. */
    suspend fun savedAndReopened(settings: ReaderSettings): ReaderSettings {
        val directory = createTempDirectory("reader-settings-test").toFile()
        fun open(scope: CoroutineScope) = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { File(directory, "reader_preferences.preferences_pb") },
        )
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            sut(open(firstScope)).save(settings)
            // Only one DataStore may be open on the file at a time. Cancelling does not wait for the first to
            // release the file, so join it before reopening.
            firstScope.coroutineContext.job.cancelAndJoin()

            return sut(open(secondScope)).settings.first()
        } finally {
            firstScope.coroutineContext.job.cancelAndJoin()
            secondScope.coroutineContext.job.cancelAndJoin()
            directory.deleteRecursively()
        }
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("observing the settings") {

        context("when nothing is stored") {

            it("emits the defaults") {
                sut().settings.first() shouldBe ReaderSettings()
            }
        }

        context("when settings are saved") {

            it("emits each saved value") {
                val store = sut()
                val first = stored(randomReaderSettings())
                val second = first.copy(justify = !first.justify)

                store.settings.test {
                    awaitItem() shouldBe ReaderSettings()

                    store.save(first)
                    awaitItem() shouldBe first

                    store.save(second)
                    awaitItem() shouldBe second

                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

        context("when the value was stored by an earlier version") {

            it("reads it from the key the stored format has always used") {
                val dataStore = TestPreferencesDataStore(
                    mutablePreferencesOf(stringPreferencesKey("reader.settings") to """{"fontSize":24}"""),
                )

                sut(dataStore).settings.first() shouldBe ReaderSettings(fontSize = 24)
            }
        }

        context("when the app restarts") {

            it("reads the value saved before the restart") {
                val settings = stored(randomReaderSettings())

                savedAndReopened(settings) shouldBe settings
            }

            it("TC-303: reads back every page turn saved before the restart") {
                checkAll(Exhaustive.enum<ReaderPageTurn>()) { mode ->
                    val settings = stored(randomReaderSettings()).copy(pageTurn = mode)

                    savedAndReopened(settings).pageTurn shouldBe mode
                }
            }
        }

        context("when the read fails with an IOException") {

            it("emits the defaults, then completes") {
                sut(FailingDataStore(IOException())).settings.test {
                    awaitItem() shouldBe ReaderSettings()
                    awaitComplete()
                }
            }
        }
    }

    describe("saving the settings") {

        context("when the write fails with an IOException") {

            it("does not throw") {
                val store = sut(FailingDataStore(IOException()))

                shouldNotThrowAny { store.save(randomReaderSettings()) }
            }
        }
    }
})
