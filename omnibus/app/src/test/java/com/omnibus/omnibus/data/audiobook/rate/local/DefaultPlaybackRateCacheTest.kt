package com.omnibus.omnibus.data.audiobook.rate.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.omnibus.omnibus.data.audiobook.di.PLAYBACK_RATE_DATASTORE_FILE
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.maps.shouldBeEmpty as shouldBeEmptyMap
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job

class DefaultPlaybackRateCacheTest : DescribeSpec({

    lateinit var dataStoreDir: File
    lateinit var dataStoreScope: CoroutineScope
    lateinit var cache: DefaultPlaybackRateCache

    fun openDataStore(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(dataStoreDir, PLAYBACK_RATE_DATASTORE_FILE) },
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        dataStoreDir = createTempDirectory("playback-rates-test").toFile()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        cache = DefaultPlaybackRateCache(openDataStore(dataStoreScope))
    }

    afterEach {
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
    }

    describe("saveLocal and reads") {

        // R14, I-SPD-1: one write sets both the cached and the pending rate
        it("#168 TC-25: saveLocal writes the cached and pending rate together (I-SPD-1)") {
            cache.saveLocal("a", 1.25)

            cache.cached("a") shouldBe 1.25
            cache.pending("a") shouldBe 1.25
            cache.allPending() shouldBe mapOf("a" to 1.25)
            cache.cached("unknown").shouldBeNull()
            cache.pending("unknown").shouldBeNull()
        }

        // R14, I-SPD-3: one pending rate per book, the newest wins; a server-only book is not pending
        it("#168 TC-26: allPending holds the newest pending rate per book only (I-SPD-3)") {
            cache.saveLocal("a", 1.25)
            cache.saveLocal("b", 1.5)
            cache.storeServer("c", 2.0)
            cache.saveLocal("a", 1.75)

            cache.allPending() shouldBe mapOf("a" to 1.75, "b" to 1.5)
        }

        // R14: the server's rate sets or removes the cached rate and never touches pending
        it("#168 TC-27: storeServer sets or removes the cached rate only (R14)") {
            cache.storeServer("a", 2.0)
            cache.cached("a") shouldBe 2.0
            cache.storeServer("a", null)
            cache.cached("a").shouldBeNull()

            cache.saveLocal("a", 1.25)
            cache.storeServer("a", 2.0)
            cache.pending("a") shouldBe 1.25
            cache.cached("a") shouldBe 2.0

            cache.storeServer("b", 3.0)
            cache.storeServer("a", null)
            cache.cached("b") shouldBe 3.0
        }

        // I-SPD-3: a pending rate is cleared only when it still equals the one that was sent
        it("#168 TC-28: clearPendingIf clears only while the pending rate still matches (I-SPD-3)") {
            cache.saveLocal("a", 1.25)
            cache.clearPendingIf("a", 1.25)
            cache.pending("a").shouldBeNull()
            cache.cached("a") shouldBe 1.25

            cache.saveLocal("a", 1.5)
            cache.clearPendingIf("a", 1.25)
            cache.pending("a") shouldBe 1.5
            cache.cached("a") shouldBe 1.5
        }

        // R14: the values survive reopening the file, as after an app restart
        it("#168 TC-29: cached and pending rates survive a new cache over the same file (R14)") {
            cache.saveLocal("a", 1.25)
            cache.storeServer("b", 2.0)
            PLAYBACK_RATE_DATASTORE_FILE.endsWith(".preferences_pb") shouldBe true
            PLAYBACK_RATE_DATASTORE_FILE shouldBe "audiobook_playback_rates.preferences_pb"

            dataStoreScope.coroutineContext.job.cancelAndJoin()
            val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val reopened = DefaultPlaybackRateCache(openDataStore(reopenedScope))

                reopened.cached("a") shouldBe 1.25
                reopened.pending("a") shouldBe 1.25
                reopened.cached("b") shouldBe 2.0
            } finally {
                reopenedScope.cancel()
            }
        }

        // R14, I-SPD-1: an unreadable file reads as absent and an unwritable one drops the write; nothing throws
        it("#168 TC-30: IO failures are swallowed: reads are absent and writes are dropped (R14, R16)") {
            val failingRead = object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flow { throw IOException("disk gone") }

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                    throw IOException("disk gone")
            }
            val failingWrite = object : DataStore<Preferences> {
                override val data: Flow<Preferences> = flowOf(emptyPreferences())

                override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                    throw IOException("disk full")
            }

            shouldNotThrowAny {
                val unreadable = DefaultPlaybackRateCache(failingRead)
                unreadable.cached("a").shouldBeNull()
                unreadable.pending("a").shouldBeNull()
                unreadable.allPending().shouldBeEmptyMap()
                unreadable.saveLocal("a", 1.25)
                unreadable.storeServer("a", 2.0)
                unreadable.clearPendingIf("a", 1.25)

                val unwritable = DefaultPlaybackRateCache(failingWrite)
                unwritable.saveLocal("a", 1.25)
                unwritable.storeServer("a", 2.0)
                unwritable.clearPendingIf("a", 1.25)
                unwritable.cached("a").shouldBeNull()
                unwritable.allPending().shouldBeEmptyMap()
            }
        }
    }
})
