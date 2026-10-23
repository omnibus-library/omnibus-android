package com.omnibus.omnibus.data.network

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl

class OmnibusHttpClientTest : DescribeSpec({

    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var client: OmnibusHttpClient

    beforeEach {
        baseUrlProvider = FakeServerBaseUrlProvider()
        client = OmnibusHttpClient(
            baseUrlProvider = baseUrlProvider,
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
        )
    }

    describe("request(path)") {

        it("returns null when no server is saved") {
            client.request("api/auth/login").shouldBeNull()
        }

        it("resolves against the saved server") {
            baseUrlProvider.baseUrl = "https://read.example.com".toHttpUrl()

            client.request("api/auth/login")!!.build().url.toString() shouldBe
                "https://read.example.com/api/auth/login"
        }

        it("follows the saved server when it changes") {
            baseUrlProvider.baseUrl = "https://one.example.com".toHttpUrl()
            client.request("api/_health")
            baseUrlProvider.baseUrl = "https://two.example.com".toHttpUrl()

            client.request("api/_health")!!.build().url.host shouldBe "two.example.com"
        }
    }

    describe("request(baseUrl, path)") {

        mapOf(
            "http://10.0.0.2:3000" to "http://10.0.0.2:3000/api/_health",
            "http://10.0.0.2:3000/" to "http://10.0.0.2:3000/api/_health",
            "https://example.com/omnibus" to "https://example.com/omnibus/api/_health",
            "https://example.com/omnibus/" to "https://example.com/omnibus/api/_health",
        ).forEach { (base, expected) ->
            it("resolves beneath $base") {
                client.request(base.toHttpUrl(), "api/_health").build().url.toString() shouldBe expected
            }
        }

        it("rejects absolute paths, which would drop the server's path prefix") {
            shouldThrow<IllegalArgumentException> {
                client.request("https://example.com/omnibus".toHttpUrl(), "/api/_health")
            }
        }
    }

    describe("newCall") {

        it("sends requests through the shared interceptors") {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse.Builder().code(200).build())

                client.newCall(client.request(server.url("/"), "api/_health").build()).execute().close()

                server.takeRequest().headers["Accept"] shouldBe "application/json"
            }
        }
    }
})
