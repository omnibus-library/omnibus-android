package com.omnibus.omnibus.data.configuration

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.randomLibrary
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
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import mockwebserver3.MockResponse

private fun ok(): MockResponse = MockResponse.Builder().code(200).build()

class DefaultServerConfigurationRepositoryTest : DescribeSpec({

    fun sut(
        librariesRepository: LibrariesRepository = TestLibrariesRepository(),
        authTokenProvider: AuthTokenProvider = TestAuthTokenProvider(),
    ): DefaultServerConfigurationRepository = DefaultServerConfigurationRepository(
        librariesRepository = librariesRepository,
        omnibusHttpClient = OmnibusHttpClient(
            // Validation targets the candidate address, never the saved one.
            baseUrlProvider = TestServerBaseUrlProvider(),
            authTokenProvider = authTokenProvider,
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        ioDispatcher = Dispatchers.Unconfined,
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("observing the saved server") {
        context("when no library is active") {
            it("TC-35: emits null") {
                sut().getSavedServerConfigFlow().first().shouldBeNull()
            }
        }

        context("when a library is active") {
            it("TC-35: emits the active library's address") {
                val address = randomServerAddress()
                val libraries = TestLibrariesRepository(
                    listOf(randomLibrary().copy(address = address, isActive = true)),
                )

                sut(libraries).getSavedServerConfigFlow().first() shouldBe address
            }
        }
    }

    describe("saving a server") {
        context("when no library exists") {
            it("TC-36: adds and activates a library for the normalized address") {
                val libraries = TestLibrariesRepository()

                sut(libraries).saveServerConfig("  example.com:3000/ ")

                libraries.getActiveLibraryFlow().first()?.address shouldBe "http://example.com:3000"
            }

            it("TC-36: removes nothing") {
                val libraries = TestLibrariesRepository()

                sut(libraries).saveServerConfig(randomServerAddress())

                libraries.removedIds shouldBe emptyList()
            }
        }

        context("when the same server was saved before") {
            it("TC-37: keeps the library") {
                val library = randomLibrary().copy(address = randomServerAddress(), isActive = true)
                val libraries = TestLibrariesRepository(listOf(library))

                sut(libraries).saveServerConfig(library.address)

                libraries.getLibrariesFlow().first() shouldBe listOf(library)
                libraries.removedIds shouldBe emptyList()
            }
        }

        context("when an older build saved the same server as typed") {
            it("TC-37: keeps the library") {
                val host = randomString(8..16).lowercase()
                val library = randomLibrary().copy(address = "$host.example.com:3000/", isActive = true)
                val libraries = TestLibrariesRepository(listOf(library))

                sut(libraries).saveServerConfig("http://$host.example.com:3000")

                libraries.getLibrariesFlow().first() shouldBe listOf(library)
                libraries.removedIds shouldBe emptyList()
            }
        }

        context("when a different server was saved before") {
            it("TC-70: replaces the library for a different address") {
                val active = randomLibrary().copy(isActive = true)
                val newAddress = randomServerAddress()
                val libraries = TestLibrariesRepository(listOf(active))

                sut(libraries).saveServerConfig(newAddress)

                libraries.removedIds shouldBe listOf(active.id)
                val stored = libraries.getLibrariesFlow().first()
                stored.count { it.isActive } shouldBe 1
                val current = libraries.getActiveLibraryFlow().first()
                current?.address shouldBe newAddress
                current?.id shouldNotBe active.id
            }
        }
    }

    describe("validating a server address") {
        context("when the health check responds 200") {
            context("when the address has no scheme") {
                it("returns Success with the normalized address") {
                    withMockServer { server ->
                        server.enqueue(ok())
                        val hostAndPort = "${server.hostName}:${server.port}"

                        sut().validateServerAddress("  $hostAndPort/ ") shouldBe
                            ServerValidationResult.Success(serverAddress = "http://$hostAndPort")
                    }
                }
            }

            context("when the address has a scheme") {
                it("returns Success with that scheme") {
                    withMockServer { server ->
                        server.enqueue(ok())
                        val address = "http://${server.hostName}:${server.port}"

                        sut().validateServerAddress("$address/") shouldBe
                            ServerValidationResult.Success(serverAddress = address)
                    }
                }
            }

            it("probes the health endpoint") {
                withMockServer { server ->
                    server.enqueue(ok())

                    sut().validateServerAddress(server.url("/").toString())

                    server.takeRequest().url.encodedPath shouldBe "/api/_health"
                }
            }

            context("when a token is stored") {
                it("TC-12: doesn't send it to the unsaved server") {
                    withMockServer { server ->
                        server.enqueue(ok())
                        val provider = TestAuthTokenProvider(token = randomString())

                        sut(authTokenProvider = provider)
                            .validateServerAddress(server.url("/").toString())

                        server.takeRequest().headers["Authorization"].shouldBeNull()
                    }
                }
            }

            it("doesn't save the address") {
                withMockServer { server ->
                    server.enqueue(ok())
                    val repository = sut()

                    repository.validateServerAddress(server.url("/").toString())

                    repository.getSavedServerConfigFlow().first().shouldBeNull()
                }
            }
        }

        context("when the health check responds with a non-2xx status") {
            it("returns ServerNotFound") {
                withMockServer { server ->
                    server.enqueue(MockResponse.Builder().code(404).build())

                    sut().validateServerAddress(server.url("/").toString()) shouldBe
                        ServerValidationResult.ServerNotFound
                }
            }
        }

        context("when the server can't be reached") {
            it("returns ServerNotFound") {
                run {
                    val closed = withMockServer { it.url("/").toString() }

                    sut().validateServerAddress(closed) shouldBe ServerValidationResult.ServerNotFound
                }
            }
        }

        context("when the address can't be parsed") {
            it("returns InvalidAddressFormat for empty, blank, wrong-scheme, hostless and spaced addresses") {
                run {
                    checkAll(Exhaustive.of("", "   ", "ftp://example.com", "http://", "exa mple.com")) { address ->
                        sut().validateServerAddress(address) shouldBe
                            ServerValidationResult.InvalidAddressFormat
                    }
                }
            }
        }
    }
})
