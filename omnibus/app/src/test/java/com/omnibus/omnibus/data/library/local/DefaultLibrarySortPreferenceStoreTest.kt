package com.omnibus.omnibus.data.library.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.library.LibrarySort
import com.omnibus.omnibus.data.library.LibrarySortPreference
import com.omnibus.omnibus.data.library.SortDirection
import com.omnibus.omnibus.data.library.randomLibrarySortPreference
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.job

private val SORT = stringPreferencesKey("library.sort")
private val DIRECTION = stringPreferencesKey("library.sortDirection")

class DefaultLibrarySortPreferenceStoreTest : DescribeSpec({

    fun sut(dataStore: DataStore<Preferences> = TestPreferencesDataStore()) =
        DefaultLibrarySortPreferenceStore(dataStore)

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading the stored preference") {

        context("when nothing is stored") {
            it("TC-115: is the defaults") {
                sut().preference.first() shouldBe LibrarySortPreference(LibrarySort.NewestAdded, SortDirection.Desc)
            }
        }

        context("when the app restarts") {
            it("TC-116: is the preference saved before") {
                val dir = createTempDirectory("library-sort-test").toFile()
                val file = File(dir, "library_preferences.preferences_pb")
                val preference = randomLibrarySortPreference()
                try {
                    val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    sut(PreferenceDataStoreFactory.create(scope = firstScope, produceFile = { file })).save(preference)
                    firstScope.coroutineContext.job.cancelAndJoin()

                    val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    val reopened = sut(PreferenceDataStoreFactory.create(scope = secondScope, produceFile = { file }))

                    reopened.preference.first() shouldBe preference
                    secondScope.coroutineContext.job.cancelAndJoin()
                } finally {
                    dir.deleteRecursively()
                }
            }
        }

        context("when values are already stored") {
            it("TC-118: reads them from the stored format") {
                val stored = preferencesOf(SORT to "title", DIRECTION to "asc")

                sut(TestPreferencesDataStore(stored)).preference.first() shouldBe
                    LibrarySortPreference(LibrarySort.Title, SortDirection.Asc)
            }
        }

        context("when the read fails") {
            it("TC-119: emits the defaults once") {
                sut(FailingPreferencesDataStore()).preference.toList() shouldBe listOf(LibrarySortPreference())
            }
        }
    }

    describe("saving the preference") {

        context("when the write fails") {
            it("TC-120: does not throw") {
                shouldNotThrowAny { sut(FailingPreferencesDataStore()).save(randomLibrarySortPreference()) }
            }
        }
    }
})
