package com.omnibus.omnibus.data.libraryconfiguration

import android.util.Log
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.OmnibusHttpClient
import com.omnibus.omnibus.data.auth.remote.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.libraryconfiguration.remote.ServerValidationRequestDto
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Interceptor

class DefaultLibraryConfigurationRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var repository: DefaultLibraryConfigurationRepository

    fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    fun serverAddress(): String = server.url("/").toString()

    beforeSpec {
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
    }

    afterSpec {
        unmockkStatic(Log::class)
    }

    beforeEach {
        server = MockWebServer().apply { start() }

        // The real interceptor reads Build.MODEL and PackageManager, which aren't available on the JVM.
        val passThroughMetadataInterceptor = mockk<AuthClientMetadataInterceptor> {
            every { intercept(any()) } answers {
                val chain = firstArg<Interceptor.Chain>()
                chain.proceed(chain.request())
            }
        }

        repository = DefaultLibraryConfigurationRepository(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrl = "https://unused.example.com/",
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = passThroughMetadataInterceptor,
            ),
        )
    }

    afterEach {
        server.close()
    }

    describe("saveServerConfiguration") {

        describe("when the address points at a healthy Omnibus server") {

            beforeEach { enqueue(200, HEALTHY_BODY) }

            it("returns ServerValidationSuccess") {
                repository.saveServerConfiguration(serverAddress()) shouldBe
                    ServerConfigSaveResult.ValidationSuccessful
            }

            it("GETs the health path on the entered server, not the configured base URL") {
                repository.saveServerConfiguration(serverAddress())

                val request = server.takeRequest()
                request.method shouldBe "GET"
                request.url.encodedPath shouldBe ServerValidationRequestDto.HEALTH_PATH
            }

            it("ignores surrounding whitespace in the address") {
                repository.saveServerConfiguration("  ${serverAddress()}  ") shouldBe
                    ServerConfigSaveResult.ValidationSuccessful
            }
        }

        describe("when the server responds with a non-2xx status") {

            listOf(401, 404, 500, 503).forEach { code ->

                it("returns ServerValidationFailure for $code") {
                    enqueue(code, HEALTHY_BODY)

                    repository.saveServerConfiguration(serverAddress()) shouldBe
                        ServerConfigSaveResult.ValidationFailed
                }
            }
        }

        describe("when the server responds 200 but isn't a healthy Omnibus server") {

            mapOf(
                "a different app" to """{"app": "jellyfin", "status": "ok"}""",
                "an unhealthy status" to """{"app": "omnibus", "status": "degraded"}""",
                "an HTML page" to "<html><body>Welcome to nginx!</body></html>",
                "an empty body" to "",
            ).forEach { (description, body) ->

                it("returns ServerValidationFailure for $description") {
                    enqueue(200, body)

                    repository.saveServerConfiguration(serverAddress()) shouldBe
                        ServerConfigSaveResult.ValidationFailed
                }
            }
        }

        describe("when the address is malformed") {

            listOf("", "   ", "read.example.com", "not a url").forEach { address ->

                it("returns ServerValidationFailure for \"$address\"") {
                    repository.saveServerConfiguration(address) shouldBe
                        ServerConfigSaveResult.ValidationFailed
                }
            }
        }

        describe("when the server can't be reached") {

            it("returns ServerValidationFailure") {
                val address = serverAddress()
                server.close()

                repository.saveServerConfiguration(address) shouldBe
                    ServerConfigSaveResult.ValidationFailed
            }
        }
    }
}) {
    private companion object {
        const val HEALTHY_BODY = """
            {
                "app": "omnibus",
                "status": "ok",
                "build_id": "1759766400123456789",
                "repo_root": "",
                "version": "0.42.0",
                "time": 1759766400
            }
        """
    }
}
