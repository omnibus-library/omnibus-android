package com.omnibus.omnibus.data.progress.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class DefaultReadingPositionStoreTest : DescribeSpec({

    lateinit var dataStoreDir: File
    lateinit var dataStoreScope: CoroutineScope
    lateinit var dataStore: DataStore<Preferences>
    lateinit var store: DefaultReadingPositionStore

    fun openDataStore(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(dataStoreDir, "reading_positions.preferences_pb") },
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
        dataStoreDir = createTempDirectory("reading-position-test").toFile()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = openDataStore(dataStoreScope)
        store = DefaultReadingPositionStore(dataStore)
    }

    afterEach {
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
    }

    describe("save and getPositionFlow") {

        it("TC-15: a saved position is read back, nullable fields included, and the last write wins") {
            val rows = listOf(
                ReadingPosition("book-1", "epubcfi(/6/4)", 40, 1_700_000_000),
                ReadingPosition("book-2", "epubcfi(/6/8)", null, 1_700_000_001),
                ReadingPosition("book-3", null, 57, 1_700_000_002),
            )
            rows.forEach { position ->
                store.save(position)
                store.getPositionFlow(position.bookUuid).first() shouldBe position
            }

            val second = ReadingPosition("book-1", "epubcfi(/6/12)", 60, 1_700_000_100)
            store.save(second)
            store.getPositionFlow("book-1").first() shouldBe second
        }

        it("TC-17: an unknown book reads null, and books are independent") {
            store.save(ReadingPosition("book-1", "epubcfi(/6/4)", 40, 1_700_000_000))

            store.getPositionFlow("book-2").first().shouldBeNull()

            store.save(ReadingPosition("book-2", "epubcfi(/6/8)", 10, 1_700_000_005))

            store.getPositionFlow("book-1").first() shouldBe
                ReadingPosition("book-1", "epubcfi(/6/4)", 40, 1_700_000_000)
        }

        it("TC-19: a corrupt value reads null for its book only, and unknown fields are ignored") {
            seed(
                "position.book-1" to "not json",
                "position.book-2" to """{"cfi":"epubcfi(/6/4)","percent":40,"clientUpdatedAt":1700000000,"foo":1}""",
            )

            store.getPositionFlow("book-1").first().shouldBeNull()
            store.getPositionFlow("book-2").first() shouldBe
                ReadingPosition("book-2", "epubcfi(/6/4)", 40, 1_700_000_000)
        }

        it("TC-20: the stored key and the JSON field names are stable and nulls are omitted") {
            store.save(ReadingPosition("book-1", "epubcfi(/6/4)", 40, 1_700_000_000))
            store.save(ReadingPosition("book-2", null, 57, 1_700_000_001))

            val prefs = dataStore.data.first()

            val full = prefs[stringPreferencesKey("position.book-1")]!!
            Json.parseToJsonElement(full).jsonObject.keys shouldContainExactlyInAnyOrder
                listOf("cfi", "percent", "clientUpdatedAt")

            val percentOnly = prefs[stringPreferencesKey("position.book-2")]!!
            Json.parseToJsonElement(percentOnly).jsonObject.keys shouldContainExactlyInAnyOrder
                listOf("percent", "clientUpdatedAt")

            prefs.asMap().keys.map { it.name }.toSet() shouldContainExactlyInAnyOrder
                listOf("position.book-1", "position.book-2")
        }

        it("TC-21: IO failures are swallowed: the read emits null and the write does not throw") {
            val failingStore = DefaultReadingPositionStore(
                object : DataStore<Preferences> {
                    override val data = flow<Preferences> { throw IOException("boom") }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IOException("boom")
                },
            )

            failingStore.getPositionFlow("book-1").toList() shouldBe listOf(null)

            shouldNotThrowAny {
                failingStore.save(ReadingPosition("book-1", "epubcfi(/6/4)", 40, 1_700_000_000))
            }
        }
    }
})
