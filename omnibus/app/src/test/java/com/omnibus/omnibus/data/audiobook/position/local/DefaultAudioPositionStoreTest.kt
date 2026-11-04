package com.omnibus.omnibus.data.audiobook.position.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.audiobook.position.AudioPosition
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class DefaultAudioPositionStoreTest : DescribeSpec({

    lateinit var dataStoreDir: File
    lateinit var dataStoreScope: CoroutineScope
    lateinit var dataStore: DataStore<Preferences>
    lateinit var store: DefaultAudioPositionStore

    fun openDataStore(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(dataStoreDir, "audiobook_positions.preferences_pb") },
    )

    /** Replaces every stored value with [values], written as raw string keys. */
    suspend fun seed(vararg values: Pair<String, String>) {
        dataStore.updateData {
            mutablePreferencesOf().apply {
                values.forEach { (name, value) -> this[stringPreferencesKey(name)] = value }
            }
        }
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        dataStoreDir = createTempDirectory("audio-position-test").toFile()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = openDataStore(dataStoreScope)
        store = DefaultAudioPositionStore(dataStore)
    }

    afterEach {
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
    }

    describe("save") {

        it("TC-1: a save is cached and pending, coalesced per book, last call wins (I-SAVE-4)") {
            val first = AudioPosition("book-1", 10.0, 11, 1)
            val second = AudioPosition("book-1", 20.0, 11, 2)

            store.save(first)
            store.cached("book-1") shouldBe first
            store.pending("book-1") shouldBe first

            store.save(second)
            store.cached("book-1") shouldBe second
            store.pending("book-1") shouldBe second
            store.allPending() shouldBe listOf(second)

            (1..50).forEach { store.save(AudioPosition("book-1", it.toDouble(), 11, it.toLong())) }
            val last = AudioPosition("book-1", 50.0, 11, 50)
            store.cached("book-1") shouldBe last
            store.pending("book-1") shouldBe last
        }

        it("TC-2: books are isolated and allPending lists every book (I-SAVE-4)") {
            val a = AudioPosition("book-a", 5.0, 1, 10)
            val b = AudioPosition("book-b", 6.0, 2, 11)

            store.save(a)
            store.save(b)

            store.cached("book-a") shouldBe a
            store.cached("book-b") shouldBe b
            store.pending("book-a") shouldBe a
            store.pending("book-b") shouldBe b
            store.allPending() shouldContainExactlyInAnyOrder listOf(a, b)
        }
    }

    describe("storage") {

        it("TC-3: uses its own key prefixes and leaves EPUB keys alone") {
            seed("position.book-1" to "x")

            store.save(AudioPosition("book-1", 12.5, 11, 7))

            val prefs = dataStore.data.first()
            prefs.asMap().keys.map { it.name }.toSet() shouldContainExactlyInAnyOrder
                listOf("position.book-1", "audio.cached.book-1", "audio.pending.book-1")
            prefs[stringPreferencesKey("position.book-1")] shouldBe "x"
            Json.parseToJsonElement(prefs[stringPreferencesKey("audio.pending.book-1")]!!).jsonObject.keys shouldContainExactlyInAnyOrder
                listOf("positionSeconds", "bookFileId", "clientUpdatedAt")
        }

        it("TC-4: values survive a new store instance (I-SAVE-4)") {
            val position = AudioPosition("book-1", 12.5, 11, 7)
            val report = ListeningSessionReport("book-1", 988, 1000, 12, "c-1", 330, "Asia/Kolkata")
            store.save(position)
            store.queueSessions(listOf(report))

            // Two live DataStores on one file throw, so the first must be fully stopped before the second opens it.
            dataStoreScope.coroutineContext.job.cancelAndJoin()
            val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val reopened = DefaultAudioPositionStore(openDataStore(reopenedScope))

                reopened.cached("book-1") shouldBe position
                reopened.pending("book-1") shouldBe position
                reopened.sessions() shouldBe listOf(report)
            } finally {
                reopenedScope.cancel()
            }
        }
    }

    describe("failures") {

        it("TC-5: a corrupt value reads as absent for its key only") {
            val valid = """{"positionSeconds":12.5,"bookFileId":11,"clientUpdatedAt":7}"""
            val b = AudioPosition("book-b", 12.5, 11, 7)
            val report = ListeningSessionReport("book-b", 988, 1000, 12, "c-2", 0, "UTC")
            val garbageKeys = listOf("audio.cached.a", "audio.pending.a", "audio.sessions")

            garbageKeys.forEach { garbageKey ->
                withClue(garbageKey) {
                    seed(
                        garbageKey to "{not json",
                        "audio.cached.book-b" to valid,
                        "audio.pending.book-b" to valid,
                    )

                    store.cached("a").shouldBeNull()
                    store.pending("a").shouldBeNull()
                    store.allPending() shouldBe listOf(b)
                    store.cached("book-b") shouldBe b
                    store.pending("book-b") shouldBe b
                    store.sessions().shouldBeEmpty()

                    store.queueSessions(listOf(report))
                    store.sessions() shouldBe listOf(report)
                }
            }
        }

        it("TC-6: a read failure reads as absent") {
            val failingStore = DefaultAudioPositionStore(
                object : DataStore<Preferences> {
                    override val data = flow<Preferences> { throw IOException("boom") }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IOException("boom")
                },
            )

            failingStore.cached("book-1").shouldBeNull()
            failingStore.pending("book-1").shouldBeNull()
            failingStore.allPending().shouldBeEmpty()
            failingStore.sessions().shouldBeEmpty()
        }

        it("TC-7: a write failure is logged and never thrown") {
            val failingStore = DefaultAudioPositionStore(
                object : DataStore<Preferences> {
                    override val data = flowOf(emptyPreferences())

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IOException("boom")
                },
            )
            val position = AudioPosition("book-1", 12.5, 11, 7)
            val report = ListeningSessionReport("book-1", 988, 1000, 12, "c-1", 330, "Asia/Kolkata")

            shouldNotThrowAny {
                failingStore.save(position)
                failingStore.clearPendingIf(position)
                failingStore.queueSessions(listOf(report))
                failingStore.removeSessions(setOf(report.clientId))
            }
        }
    }

    describe("clearPendingIf") {

        it("TC-8: clears only a pending write equal to the one sent (I-SAVE-4)") {
            val sent = AudioPosition("book-1", 30.0, 11, 10)
            val newer = AudioPosition("book-1", 40.0, 11, 20)

            withClue("pending equals the sent position: cleared, cached kept") {
                store.save(sent)
                store.clearPendingIf(sent)
                store.pending("book-1").shouldBeNull()
                store.cached("book-1") shouldBe sent
            }

            withClue("pending is a newer write: kept") {
                seed()
                store.save(newer)
                store.clearPendingIf(sent)
                store.pending("book-1") shouldBe newer
                store.cached("book-1") shouldBe newer
            }

            withClue("nothing pending: no change, no throw") {
                seed()
                shouldNotThrowAny { store.clearPendingIf(sent) }
                store.pending("book-1").shouldBeNull()
                store.cached("book-1").shouldBeNull()
            }
        }
    }

    describe("sessions") {

        it("TC-9: reports are listed in queue order and removed by client id (I-SESS-3)") {
            val r1 = ListeningSessionReport("book-1", 100, 112, 12, "c-1", 330, "Asia/Kolkata")
            val r2 = ListeningSessionReport("book-2", 200, 205, 5, "c-2", -480, "America/Los_Angeles")
            val r3 = ListeningSessionReport("book-1", 300, 310, 10, "c-3", -300, "America/New_York")

            store.queueSessions(listOf(r1))
            store.queueSessions(listOf(r2, r3))
            store.sessions() shouldBe listOf(r1, r2, r3)

            store.removeSessions(setOf(r2.clientId))
            store.sessions() shouldBe listOf(r1, r3)
        }
    }
})
