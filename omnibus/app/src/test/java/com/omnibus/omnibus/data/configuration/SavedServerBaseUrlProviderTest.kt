package com.omnibus.omnibus.data.configuration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class SavedServerBaseUrlProviderTest : DescribeSpec({

    lateinit var dataStoreDir: File
    lateinit var dataStoreScope: CoroutineScope
    lateinit var provider: SavedServerBaseUrlProvider
    lateinit var save: suspend (String) -> Unit

    beforeEach {
        dataStoreDir = createTempDirectory("base-url-test").toFile()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { File(dataStoreDir, "server_configuration.preferences_pb") },
        )
        provider = SavedServerBaseUrlProvider(dataStore)
        save = { address ->
            dataStore.edit { it[DefaultServerConfigurationRepository.SERVER_URL_PREFS_KEY] = address }
        }
    }

    afterEach {
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
    }

    describe("baseUrl") {

        it("is null when no server is saved") {
            provider.baseUrl().shouldBeNull()
        }

        it("is the saved server") {
            save("https://read.example.com")

            provider.baseUrl().toString() shouldBe "https://read.example.com/"
        }

        it("keeps a path prefix") {
            save("https://example.com/omnibus")

            provider.baseUrl()!!.encodedPath shouldBe "/omnibus"
        }

        it("normalizes addresses saved as typed by older builds") {
            save(" 10.0.0.2:3000/ ")

            provider.baseUrl().toString() shouldBe "http://10.0.0.2:3000/"
        }

        listOf("", "   ", "exa mple.com", "ftp://example.com").forEach { saved ->
            it("is null when the saved value \"$saved\" isn't a valid server") {
                save(saved)

                provider.baseUrl().shouldBeNull()
            }
        }
    }
})
