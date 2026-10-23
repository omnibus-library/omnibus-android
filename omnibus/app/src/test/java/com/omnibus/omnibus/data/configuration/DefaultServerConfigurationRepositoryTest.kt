package com.omnibus.omnibus.data.configuration

import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.omnibus.omnibus.data.auth.local.FakeAuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.io.IOException
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class DefaultServerConfigurationRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var dataStoreDir: File
    lateinit var dataStoreScope: CoroutineScope
    lateinit var tokenStore: FakeAuthTokenStore
    lateinit var tokenProvider: FakeAuthTokenProvider
    lateinit var repository: DefaultServerConfigurationRepository

    beforeSpec {
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
    }

    afterSpec {
        unmockkStatic(Log::class)
    }

    beforeEach {
        server = MockWebServer().apply { start() }
        dataStoreDir = createTempDirectory("server-config-test").toFile()
        dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        tokenStore = FakeAuthTokenStore(initialToken = TOKEN)
        tokenProvider = FakeAuthTokenProvider(token = TOKEN)
        repository = DefaultServerConfigurationRepository(
            serverConfigPreferences = PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { File(dataStoreDir, "server_configuration.preferences_pb") },
            ),
            omnibusHttpClient = OmnibusHttpClient(
                // Validation targets the candidate address, never the saved one.
                baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = null),
                authTokenProvider = tokenProvider,
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
            authTokenStore = tokenStore,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    afterEach {
        server.close()
        dataStoreScope.cancel()
        dataStoreDir.deleteRecursively()
    }

    describe("fetchSavedServerConfig") {

        it("emits null when no server has been saved") {
            repository.fetchSavedServerConfig().first().shouldBeNull()
        }

        it("emits the address passed to saveServerConfig") {
            repository.saveServerConfig("http://10.0.0.2:3000")

            repository.fetchSavedServerConfig().first() shouldBe "http://10.0.0.2:3000"
        }

        it("emits the most recently saved address") {
            repository.saveServerConfig("http://10.0.0.2:3000")
            repository.saveServerConfig("https://read.example.com")

            repository.fetchSavedServerConfig().first() shouldBe "https://read.example.com"
        }
    }

    describe("saveServerConfig") {

        it("signs out when a different server is saved") {
            repository.saveServerConfig("http://10.0.0.2:3000")
            tokenStore.save(TOKEN)

            repository.saveServerConfig("https://read.example.com")

            tokenStore.token.first().shouldBeNull()
        }

        it("signs out when the first server is saved") {
            repository.saveServerConfig("http://10.0.0.2:3000")

            tokenStore.token.first().shouldBeNull()
        }

        it("stays signed in when the same server is saved again") {
            repository.saveServerConfig("http://10.0.0.2:3000")
            tokenStore.save(TOKEN)

            repository.saveServerConfig("http://10.0.0.2:3000")

            tokenStore.token.first() shouldBe TOKEN
        }

        it("treats an address saved as typed by an older build as the same server") {
            repository.saveServerConfig("10.0.0.2:3000/")
            tokenStore.save(TOKEN)

            repository.saveServerConfig("http://10.0.0.2:3000")

            tokenStore.token.first() shouldBe TOKEN
        }

        it("still saves the server when the old token can't be cleared") {
            tokenStore.clearFailure = IOException("keystore unavailable")

            repository.saveServerConfig("https://read.example.com")

            repository.fetchSavedServerConfig().first() shouldBe "https://read.example.com"
        }
    }

    describe("validateServerAddress") {

        describe("when the health check responds 200") {

            it("returns Success with the normalized address") {
                server.enqueue(MockResponse.Builder().code(200).build())
                val hostAndPort = "${server.hostName}:${server.port}"

                repository.validateServerAddress("  $hostAndPort/ ") shouldBe
                    ServerValidationResult.Success(serverAddress = "http://$hostAndPort")
                server.takeRequest().url.encodedPath shouldBe "/api/_health"
            }

            it("keeps an explicit scheme") {
                server.enqueue(MockResponse.Builder().code(200).build())
                val address = "http://${server.hostName}:${server.port}"

                repository.validateServerAddress("$address/") shouldBe
                    ServerValidationResult.Success(serverAddress = address)
            }

            it("probes beneath a path prefix") {
                server.enqueue(MockResponse.Builder().code(200).build())

                repository.validateServerAddress(server.url("/omnibus/").toString())

                server.takeRequest().url.encodedPath shouldBe "/omnibus/api/_health"
            }

            it("TC-12: doesn't send the stored bearer token to the unsaved server") {
                server.enqueue(MockResponse.Builder().code(200).build())

                repository.validateServerAddress(server.url("/").toString())

                server.takeRequest().headers["Authorization"].shouldBeNull()
                tokenProvider.invalidateCalls shouldBe 0
            }

            it("doesn't save the address") {
                server.enqueue(MockResponse.Builder().code(200).build())

                repository.validateServerAddress(server.url("/").toString())

                repository.fetchSavedServerConfig().first().shouldBeNull()
            }
        }

        describe("when the health check responds with a non-2xx status") {

            it("returns ServerNotFound") {
                server.enqueue(MockResponse.Builder().code(404).build())

                repository.validateServerAddress(server.url("/").toString()) shouldBe
                    ServerValidationResult.ServerNotFound
            }
        }

        describe("when the server can't be reached") {

            it("returns ServerNotFound") {
                val address = server.url("/").toString()
                server.close()

                repository.validateServerAddress(address) shouldBe ServerValidationResult.ServerNotFound
            }

            it("returns ServerNotFound for a host that doesn't resolve") {
                repository.validateServerAddress("rrrrrrr.invalid") shouldBe ServerValidationResult.ServerNotFound
            }
        }

        describe("when the address can't be parsed") {

            listOf("", "   ", "ftp://example.com", "http://", "exa mple.com").forEach { address ->

                it("returns InvalidAddressFormat for \"$address\"") {
                    repository.validateServerAddress(address) shouldBe ServerValidationResult.InvalidAddressFormat
                }
            }
        }
    }
}) {
    private companion object {
        const val TOKEN = "abc123"
    }
}
