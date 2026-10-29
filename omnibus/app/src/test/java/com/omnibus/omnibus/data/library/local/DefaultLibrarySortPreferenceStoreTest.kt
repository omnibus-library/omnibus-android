package com.omnibus.omnibus.data.library.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.LibrarySortPreference
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
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

class DefaultLibrarySortPreferenceStoreTest : DescribeSpec({

    lateinit var dataStoreDir: File
    lateinit var dataStoreScope: CoroutineScope
    lateinit var dataStore: DataStore<Preferences>
    lateinit var store: DefaultLibrarySortPreferenceStore

    fun openDataStore(scope: CoroutineScope): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { File(dataStoreDir, "library_preferences.preferences_pb") },
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
        dataStoreDir = createTempDirectory("library-sort-test").toFile()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = openDataStore(dataStoreScope)
        store = DefaultLibrarySortPreferenceStore(dataStore)
    }

    afterEach {
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
    }

    describe("preference") {

        it("TC-115: emits the defaults when nothing is stored") {
            store.preference.first() shouldBe LibrarySortPreference()
        }

        it("TC-116: a saved preference is read back, including through a second store on the same file") {
            store.save(LibrarySortPreference(LibrarySort.Author, SortDirection.Asc))

            store.preference.first() shouldBe LibrarySortPreference(LibrarySort.Author, SortDirection.Asc)

            dataStoreScope.cancel()
            val reopenedScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val reopened = DefaultLibrarySortPreferenceStore(openDataStore(reopenedScope))

                reopened.preference.first() shouldBe LibrarySortPreference(LibrarySort.Author, SortDirection.Asc)
            } finally {
                reopenedScope.cancel()
            }
        }

        it("TC-117: a save writes two separate raw string keys and no category key") {
            store.save(LibrarySortPreference(LibrarySort.RecentlyInteracted, SortDirection.Asc))

            val prefs = dataStore.data.first()

            prefs[stringPreferencesKey("library.sort")] shouldBe "recently_interacted"
            prefs[stringPreferencesKey("library.sortDirection")] shouldBe "asc"
            prefs.asMap().keys.map { it.name }.toSet() shouldBe setOf("library.sort", "library.sortDirection")
        }

        it("TC-118: an unknown sort or direction falls back independently and the other field is kept") {
            seed("library.sort" to "bogus", "library.sortDirection" to "asc")
            store.preference.first() shouldBe LibrarySortPreference(LibrarySort.NewestAdded, SortDirection.Asc)

            seed("library.sort" to "title", "library.sortDirection" to "sideways")
            store.preference.first() shouldBe LibrarySortPreference(LibrarySort.Title, SortDirection.Desc)

            seed("library.sort" to "author")
            store.preference.first() shouldBe LibrarySortPreference(LibrarySort.Author, SortDirection.Desc)
        }

        it("TC-119: a failed read emits the defaults, does not throw, and completes") {
            val failingStore = DefaultLibrarySortPreferenceStore(
                object : DataStore<Preferences> {
                    override val data = flow<Preferences> { throw IOException("boom") }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IOException("boom")
                },
            )

            failingStore.preference.toList() shouldBe listOf(LibrarySortPreference())
        }
    }

    describe("save") {

        it("TC-120: a failed write does not throw out of save") {
            val failingStore = DefaultLibrarySortPreferenceStore(
                object : DataStore<Preferences> {
                    override val data = flow<Preferences> { emit(mutablePreferencesOf()) }

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IOException("boom")
                },
            )

            failingStore.save(LibrarySortPreference(LibrarySort.Title, SortDirection.Asc))
        }
    }
})
