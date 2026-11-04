package com.omnibus.omnibus.data.audiobook.crossformat.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job

class DefaultCrossFormatDismissStoreTest : DescribeSpec({

    lateinit var dataStoreDir: File
    lateinit var dataStoreScope: CoroutineScope
    lateinit var dataStore: DataStore<Preferences>
    lateinit var store: DefaultCrossFormatDismissStore

    fun openDataStore(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(dataStoreDir, "audiobook_resume_offers.preferences_pb") },
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        dataStoreDir = createTempDirectory("resume-offers-test").toFile()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = openDataStore(dataStoreScope)
        store = DefaultCrossFormatDismissStore(dataStore)
    }

    afterEach {
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
    }

    describe("dismissedClock and store") {

        // R8, P-SYNC-AC6: nothing is dismissed until the listener dismisses it
        it("#166 TC-19: reads null before any store, then the stored clock (P-SYNC-AC6)") {
            store.dismissedClock("book-1").shouldBeNull()

            store.store("book-1", 500L)

            store.dismissedClock("book-1") shouldBe 500L
        }

        // R8: a later dismissal replaces the earlier clock
        it("#166 TC-20: the later store wins for one book (P-SYNC-AC6)") {
            store.store("book-1", 500L)
            store.store("book-1", 700L)

            store.dismissedClock("book-1") shouldBe 700L
        }

        // R8: the memory is per book
        it("#166 TC-21: each book keeps its own clock (P-SYNC-AC6)") {
            store.store("book-a", 500L)

            store.dismissedClock("book-a") shouldBe 500L
            store.dismissedClock("book-b").shouldBeNull()
        }

        // R8: the memory survives an app restart
        it("#166 TC-22: the clock survives reopening the store (P-SYNC-AC6)") {
            store.store("book-1", 900L)

            dataStoreScope.coroutineContext.job.cancelAndJoin()
            val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val reopened = DefaultCrossFormatDismissStore(openDataStore(reopenedScope))

                reopened.dismissedClock("book-1") shouldBe 900L
            } finally {
                reopenedScope.cancel()
            }
        }

        // R8: an unreadable memory reads as absent, so the offer shows again
        it("#166 TC-23: a read that throws IOException reads as null and does not throw (P-SYNC-AC6)") {
            val failingRead = object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow { throw IOException("disk gone") }

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                    throw IOException("disk gone")
            }
            val failing = DefaultCrossFormatDismissStore(failingRead)

            shouldNotThrowAny {
                failing.dismissedClock("book-1").shouldBeNull()
            }
        }

        // R8: a failed write is logged, not thrown
        it("#166 TC-24: a write that throws IOException is logged and does not throw (I-SAVE-3)") {
            val failingWrite = object : DataStore<Preferences> {
                override val data: Flow<Preferences> = emptyFlow()

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                    throw IOException("disk full")
            }
            val failing = DefaultCrossFormatDismissStore(failingWrite)

            shouldNotThrowAny {
                failing.store("book-1", 500L)
            }
        }
    }
})
