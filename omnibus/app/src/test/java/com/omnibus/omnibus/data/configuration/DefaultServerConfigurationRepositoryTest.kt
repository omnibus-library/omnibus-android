package com.omnibus.omnibus.data.configuration

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer

class DefaultServerConfigurationRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var repository: DefaultServerConfigurationRepository

    beforeEach {
        server = MockWebServer().apply { start() }
        repository = DefaultServerConfigurationRepository(
            serverConfigPreferences = mockk(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    afterEach {
        server.close()
    }

    describe("validateServerAddress") {

        describe("when the health check responds 200") {

            it("returns Success") {
                server.enqueue(MockResponse.Builder().code(200).build())

                repository.validateServerAddress(server.url("/").toString()) shouldBe ServerValidationResult.Success
                server.takeRequest().url.encodedPath shouldBe "/api/_health"
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
})
