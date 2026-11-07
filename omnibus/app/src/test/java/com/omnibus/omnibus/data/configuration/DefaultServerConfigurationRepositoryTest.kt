package com.omnibus.omnibus.data.configuration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.data.auth.local.AuthTokenStore
import com.omnibus.omnibus.data.auth.local.TestAuthTokenStore
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.AuthTokenProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.io.File
import java.io.IOException
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

/** Runs [block] with an empty preferences store on a temp file, removed afterwards. */
private suspend inline fun <T> withPreferences(block: (DataStore<Preferences>) -> T): T {
    val directory = createTempDirectory("server-config-test").toFile()
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

private suspend inline fun <T> withServerAndPreferences(block: (MockWebServer, DataStore<Preferences>) -> T): T =
    withMockServer { server -> withPreferences { preferences -> block(server, preferences) } }

private fun ok(): MockResponse = MockResponse.Builder().code(200).build()

class DefaultServerConfigurationRepositoryTest : DescribeSpec({

    fun sut(
        preferences: DataStore<Preferences>,
        authTokenStore: AuthTokenStore = TestAuthTokenStore(),
        authTokenProvider: AuthTokenProvider = TestAuthTokenProvider(),
    ): DefaultServerConfigurationRepository = DefaultServerConfigurationRepository(
        serverConfigPreferences = preferences,
        omnibusHttpClient = OmnibusHttpClient(
            // Validation targets the candidate address, never the saved one.
            baseUrlProvider = TestServerBaseUrlProvider(),
            authTokenProvider = authTokenProvider,
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        authTokenStore = authTokenStore,
        ioDispatcher = Dispatchers.Unconfined,
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("observing the saved server") {
        context("when no server has been saved") {
            it("emits null") {
                withPreferences { preferences ->
                    sut(preferences).getSavedServerConfigFlow().first().shouldBeNull()
                }
            }
        }

        context("when a server has been saved") {
            it("emits its address") {
                withPreferences { preferences ->
                    val address = randomServerAddress()
                    val repository = sut(preferences)

                    repository.saveServerConfig(address)

                    repository.getSavedServerConfigFlow().first() shouldBe address
                }
            }
        }
    }

    describe("saving a server") {
        context("when no server was saved before") {
            it("signs out") {
                withPreferences { preferences ->
                    val tokenStore = TestAuthTokenStore(initialToken = randomString())

                    sut(preferences, tokenStore).saveServerConfig(randomServerAddress())

                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        context("when a different server was saved before") {
            it("signs out") {
                withPreferences { preferences ->
                    val tokenStore = TestAuthTokenStore()
                    val repository = sut(preferences, tokenStore)
                    repository.saveServerConfig(randomServerAddress())
                    tokenStore.save(randomString())

                    repository.saveServerConfig(randomServerAddress())

                    tokenStore.token.first().shouldBeNull()
                }
            }
        }

        context("when the same server was saved before") {
            it("stays signed in") {
                withPreferences { preferences ->
                    val address = randomServerAddress()
                    val token = randomString()
                    val tokenStore = TestAuthTokenStore()
                    val repository = sut(preferences, tokenStore)
                    repository.saveServerConfig(address)
                    tokenStore.save(token)

                    repository.saveServerConfig(address)

                    tokenStore.token.first() shouldBe token
                }
            }
        }

        context("when an older build saved the same server as typed") {
            it("stays signed in") {
                withPreferences { preferences ->
                    val host = randomString(8..16).lowercase()
                    val token = randomString()
                    val tokenStore = TestAuthTokenStore()
                    val repository = sut(preferences, tokenStore)
                    repository.saveServerConfig("$host.example.com:3000/")
                    tokenStore.save(token)

                    repository.saveServerConfig("http://$host.example.com:3000")

                    tokenStore.token.first() shouldBe token
                }
            }
        }

        context("when the old token can't be cleared") {
            it("still saves the server") {
                withPreferences { preferences ->
                    val address = randomServerAddress()
                    val tokenStore = TestAuthTokenStore().apply { clearFailure = IOException("keystore unavailable") }
                    val repository = sut(preferences, tokenStore)

                    repository.saveServerConfig(address)

                    repository.getSavedServerConfigFlow().first() shouldBe address
                }
            }
        }
    }

    describe("validating a server address") {
        context("when the health check responds 200") {
            context("when the address has no scheme") {
                it("returns Success with the normalized address") {
                    withServerAndPreferences { server, preferences ->
                        server.enqueue(ok())
                        val hostAndPort = "${server.hostName}:${server.port}"

                        sut(preferences).validateServerAddress("  $hostAndPort/ ") shouldBe
                            ServerValidationResult.Success(serverAddress = "http://$hostAndPort")
                    }
                }
            }

            context("when the address has a scheme") {
                it("returns Success with that scheme") {
                    withServerAndPreferences { server, preferences ->
                        server.enqueue(ok())
                        val address = "http://${server.hostName}:${server.port}"

                        sut(preferences).validateServerAddress("$address/") shouldBe
                            ServerValidationResult.Success(serverAddress = address)
                    }
                }
            }

            it("probes the health endpoint") {
                withServerAndPreferences { server, preferences ->
                    server.enqueue(ok())

                    sut(preferences).validateServerAddress(server.url("/").toString())

                    server.takeRequest().url.encodedPath shouldBe "/api/_health"
                }
            }

            context("when a token is stored") {
                it("TC-12: doesn't send it to the unsaved server") {
                    withServerAndPreferences { server, preferences ->
                        server.enqueue(ok())
                        val provider = TestAuthTokenProvider(token = randomString())

                        sut(preferences, authTokenProvider = provider)
                            .validateServerAddress(server.url("/").toString())

                        server.takeRequest().headers["Authorization"].shouldBeNull()
                    }
                }
            }

            it("doesn't save the address") {
                withServerAndPreferences { server, preferences ->
                    server.enqueue(ok())
                    val repository = sut(preferences)

                    repository.validateServerAddress(server.url("/").toString())

                    repository.getSavedServerConfigFlow().first().shouldBeNull()
                }
            }
        }

        context("when the health check responds with a non-2xx status") {
            it("returns ServerNotFound") {
                withServerAndPreferences { server, preferences ->
                    server.enqueue(MockResponse.Builder().code(404).build())

                    sut(preferences).validateServerAddress(server.url("/").toString()) shouldBe
                        ServerValidationResult.ServerNotFound
                }
            }
        }

        context("when the server can't be reached") {
            it("returns ServerNotFound") {
                withPreferences { preferences ->
                    val closed = withMockServer { it.url("/").toString() }

                    sut(preferences).validateServerAddress(closed) shouldBe ServerValidationResult.ServerNotFound
                }
            }
        }

        context("when the address can't be parsed") {
            it("returns InvalidAddressFormat for empty, blank, wrong-scheme, hostless and spaced addresses") {
                withPreferences { preferences ->
                    checkAll(Exhaustive.of("", "   ", "ftp://example.com", "http://", "exa mple.com")) { address ->
                        sut(preferences).validateServerAddress(address) shouldBe
                            ServerValidationResult.InvalidAddressFormat
                    }
                }
            }
        }
    }
})
