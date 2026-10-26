package com.omnibus.omnibus.data.network

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.IOException
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

class OmnibusHttpClientTest : DescribeSpec({

    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var tokenProvider: FakeAuthTokenProvider
    lateinit var client: OmnibusHttpClient

    beforeEach {
        baseUrlProvider = FakeServerBaseUrlProvider()
        tokenProvider = FakeAuthTokenProvider()
        client = OmnibusHttpClient(
            baseUrlProvider = baseUrlProvider,
            authTokenProvider = tokenProvider,
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

        describe("bearer token") {

            beforeEach { baseUrlProvider.baseUrl = "https://read.example.com".toHttpUrl() }

            it("TC-10: adds a single Authorization: Bearer header when a token is stored") {
                tokenProvider.token = "abc123"

                val request = client.request("api/auth/me")!!.build()

                request.headers("Authorization") shouldBe listOf("Bearer abc123")
            }

            it("TC-10: uses the current token for each request") {
                tokenProvider.token = "first"
                client.request("api/auth/me")!!.build().header("Authorization") shouldBe "Bearer first"

                tokenProvider.token = "second"
                client.request("api/auth/me")!!.build().header("Authorization") shouldBe "Bearer second"
            }

            it("TC-10: sends the header on the wire") {
                MockWebServer().use { server ->
                    server.start()
                    server.enqueue(MockResponse.Builder().code(200).build())
                    baseUrlProvider.baseUrl = server.url("/")
                    tokenProvider.token = "abc123"

                    client.newCall(client.request("api/auth/me")!!.build()).execute().close()

                    server.takeRequest().headers["Authorization"] shouldBe "Bearer abc123"
                }
            }

            listOf(null, "", "   ").forEach { token ->
                it("TC-11: adds no Authorization header when the token is ${token?.let { "\"$it\"" } ?: "null"}") {
                    tokenProvider.token = token

                    client.request("api/auth/me")!!.build().header("Authorization").shouldBeNull()
                }
            }
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

        it("TC-12: never adds an Authorization header, even when a token is stored") {
            tokenProvider.token = "abc123"

            client.request("http://10.0.0.2:3000".toHttpUrl(), "api/_health")
                .build()
                .header("Authorization")
                .shouldBeNull()
        }
    }

    describe("newCall") {

        it("TC-183: sends requests through the shared interceptors") {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse.Builder().code(200).build())

                client.newCall(client.request(server.url("/"), "api/_health").build()).execute().close()

                server.takeRequest().headers["Accept"] shouldBe "application/json"
            }
        }

        it("TC-184: keeps an Accept header the request already set") {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse.Builder().code(200).build())
                val request = client.request(server.url("/"), "api/thumbs/abc/md")
                    .header("Accept", "image/webp")
                    .build()

                client.newCall(request).execute().close()

                server.takeRequest().headers["Accept"] shouldBe "image/webp"
            }
        }
    }

    describe("execute") {

        lateinit var server: MockWebServer

        fun enqueue(code: Int) {
            server.enqueue(MockResponse.Builder().code(code).build())
        }

        beforeEach {
            server = MockWebServer().apply { start() }
            baseUrlProvider.baseUrl = server.url("/")
            tokenProvider.token = TOKEN
        }

        afterEach { server.close() }

        it("returns the response from the server") {
            enqueue(200)

            client.execute(client.request("api/auth/me")!!.build()).use { it.code } shouldBe 200
        }

        it("TC-13: invalidates the token on a 401 to an authenticated request, and still returns the 401") {
            enqueue(401)

            val code = client.execute(client.request("api/auth/me")!!.build()).use { it.code }

            code shouldBe 401
            tokenProvider.invalidateCalls shouldBe 1
        }

        it("TC-13: invalidates on a 401 to any non-sign-in path, not only api/auth/me") {
            enqueue(401)

            client.execute(client.request("api/books")!!.build()).close()

            tokenProvider.invalidateCalls shouldBe 1
        }

        it("TC-13: invalidates on a 401 beneath a server path prefix") {
            baseUrlProvider.baseUrl = server.url("/omnibus")
            enqueue(401)

            client.execute(client.request("api/auth/me")!!.build()).close()

            tokenProvider.invalidateCalls shouldBe 1
        }

        listOf("api/auth/login", "api/auth/register").forEach { path ->

            it("TC-14: does not invalidate on a 401 from $path, even with a bearer token") {
                enqueue(401)
                val request = client.request(path)!!.post(EMPTY_JSON).build()
                request.header("Authorization") shouldBe "Bearer $TOKEN"

                client.execute(request).use { it.code } shouldBe 401

                tokenProvider.invalidateCalls shouldBe 0
                tokenProvider.token shouldBe TOKEN
            }

            it("TC-14: does not invalidate on a 401 from $path beneath a server path prefix") {
                baseUrlProvider.baseUrl = server.url("/omnibus")
                enqueue(401)

                client.execute(client.request(path)!!.post(EMPTY_JSON).build()).close()

                tokenProvider.invalidateCalls shouldBe 0
            }
        }

        it("TC-15: does not invalidate on a 401 when the request carried no Authorization header") {
            tokenProvider.token = null
            enqueue(401)

            client.execute(client.request("api/auth/me")!!.build()).use { it.code } shouldBe 401

            tokenProvider.invalidateCalls shouldBe 0
        }

        listOf(200, 204, 403, 404, 500, 503).forEach { code ->
            it("TC-15: does not invalidate on a $code") {
                enqueue(code)

                client.execute(client.request("api/auth/me")!!.build()).use { it.code } shouldBe code

                tokenProvider.invalidateCalls shouldBe 0
                tokenProvider.token shouldBe TOKEN
            }
        }

        it("TC-15: throws IOException without invalidating when the server can't be reached") {
            val request = client.request("api/auth/me")!!.build()
            server.close()

            shouldThrow<IOException> { client.execute(request) }

            tokenProvider.invalidateCalls shouldBe 0
        }

        it("TC-183: sends requests through the shared interceptors") {
            enqueue(200)

            client.execute(client.request("api/auth/me")!!.build()).close()

            server.takeRequest().headers["Accept"] shouldBe "application/json"
        }
    }
}) {
    private companion object {
        const val TOKEN = "abc123"
        val EMPTY_JSON = "{}".toRequestBody("application/json".toMediaType())
    }
}
