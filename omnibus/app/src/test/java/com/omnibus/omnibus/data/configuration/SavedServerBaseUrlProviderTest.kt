package com.omnibus.omnibus.data.configuration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job

/** Runs [block] with an empty preferences store on a temp file, removed afterwards. */
private suspend inline fun <T> withPreferences(block: (DataStore<Preferences>) -> T): T {
    val directory = createTempDirectory("base-url-test").toFile()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    return try {
        block(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(directory, "server_configuration.preferences_pb") },
            ),
        )
    } finally {
        scope.coroutineContext.job.cancelAndJoin()
        directory.deleteRecursively()
    }
}

private suspend fun DataStore<Preferences>.saveAddress(address: String) {
    edit { it[DefaultServerConfigurationRepository.SERVER_URL_PREFS_KEY] = address }
}

class SavedServerBaseUrlProviderTest : DescribeSpec({

    fun sut(preferences: DataStore<Preferences>): SavedServerBaseUrlProvider = SavedServerBaseUrlProvider(preferences)

    describe("reading the base URL") {
        context("when no server is saved") {
            it("is null") {
                withPreferences { preferences ->
                    sut(preferences).baseUrl().shouldBeNull()
                }
            }
        }

        context("when a server is saved") {
            it("is that server") {
                withPreferences { preferences ->
                    val address = randomServerAddress()
                    preferences.saveAddress(address)

                    sut(preferences).baseUrl().toString() shouldBe "$address/"
                }
            }
        }

        context("when an older build saved the address as typed") {
            it("normalizes it") {
                withPreferences { preferences ->
                    preferences.saveAddress(" 10.0.0.2:3000/ ")

                    sut(preferences).baseUrl().toString() shouldBe "http://10.0.0.2:3000/"
                }
            }
        }

        context("when the saved value isn't a valid server") {
            it("is null for empty, blank, spaced and wrong-scheme values") {
                withPreferences { preferences ->
                    checkAll(Exhaustive.of("", "   ", "exa mple.com", "ftp://example.com")) { saved ->
                        preferences.saveAddress(saved)

                        sut(preferences).baseUrl().shouldBeNull()
                    }
                }
            }
        }
    }
})
