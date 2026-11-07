package com.omnibus.omnibus.data.network

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private val SERVER = "https://read.example.com".toHttpUrl()

class OmnibusHttpClientTest : DescribeSpec({

    fun sut(
        baseUrlProvider: ServerBaseUrlProvider = TestServerBaseUrlProvider(),
        authTokenProvider: AuthTokenProvider = TestAuthTokenProvider(),
    ) = OmnibusHttpClient(
        baseUrlProvider = baseUrlProvider,
        authTokenProvider = authTokenProvider,
        omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
        authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
    )

    describe("building a request") {
        context("with no base URL given") {
            it("resolves against the saved server") {
                sut(TestServerBaseUrlProvider(SERVER)).request("api/auth/login")!!.build().url.toString() shouldBe
                    "https://read.example.com/api/auth/login"
            }

            context("when no server is saved") {
                it("gives no request") {
                    sut().request(randomString()).shouldBeNull()
                }
            }

            context("when the saved server changes") {
                it("follows it") {
                    val baseUrl = TestServerBaseUrlProvider("https://one.example.com".toHttpUrl())
                    val client = sut(baseUrl)
                    client.request("api/_health")

                    baseUrl.baseUrl = "https://two.example.com".toHttpUrl()

                    client.request("api/_health")!!.build().url.host shouldBe "two.example.com"
                }
            }

            context("when a token is stored") {
                it("TC-10: adds one bearer header") {
                    val token = randomString()

                    sut(TestServerBaseUrlProvider(SERVER), TestAuthTokenProvider(token)).request("api/auth/me")!!.build()
                        .headers("Authorization") shouldBe listOf("Bearer $token")
                }
            }

            context("when the stored token changes") {
                it("TC-10: uses the current one") {
                    val tokens = TestAuthTokenProvider(randomString())
                    val client = sut(TestServerBaseUrlProvider(SERVER), tokens)
                    client.request("api/auth/me")
                    val token = randomString()

                    tokens.token = token

                    client.request("api/auth/me")!!.build().header("Authorization") shouldBe "Bearer $token"
                }
            }

            context("when no usable token is stored") {
                it("TC-11: adds no bearer header") {
                    checkAll(Exhaustive.of(null, "", "   ")) { token ->
                        sut(TestServerBaseUrlProvider(SERVER), TestAuthTokenProvider(token)).request("api/auth/me")!!.build()
                            .header("Authorization").shouldBeNull()
                    }
                }
            }
        }

        context("with a base URL given") {
            it("resolves beneath the base URL, with or without a trailing slash") {
                checkAll(
                    Exhaustive.of(
                        "http://10.0.0.2:3000" to "http://10.0.0.2:3000/api/_health",
                        "http://10.0.0.2:3000/" to "http://10.0.0.2:3000/api/_health",
                        "https://example.com/omnibus" to "https://example.com/omnibus/api/_health",
                        "https://example.com/omnibus/" to "https://example.com/omnibus/api/_health",
                    ),
                ) { (base, expected) ->
                    sut().request(base.toHttpUrl(), "api/_health").build().url.toString() shouldBe expected
                }
            }

            it("TC-12: never adds a bearer header, even with a stored token") {
                sut(authTokenProvider = TestAuthTokenProvider(randomString()))
                    .request(SERVER, "api/_health").build()
                    .header("Authorization").shouldBeNull()
            }

            context("when the path is absolute") {
                it("rejects it, as it would drop the server's path prefix") {
                    shouldThrow<IllegalArgumentException> {
                        sut().request("https://example.com/omnibus".toHttpUrl(), "/api/_health")
                    }
                }
            }
        }
    }

    describe("sending a request") {

        it("TC-183: goes through the shared interceptors") {
            withMockServer { server ->
                server.enqueue(reply(200))
                val client = sut()

                client.newCall(client.request(server.url("/"), "api/_health").build()).execute().close()

                server.nextRequest().headers["Accept"] shouldBe "application/json"
            }
        }
    }

    describe("executing a request") {

        context("when an authenticated request gets a 401") {
            it("TC-13: invalidates the token") {
                withMockServer { server ->
                    server.enqueue(reply(401))
                    val tokens = TestAuthTokenProvider(randomString())
                    val client = sut(TestServerBaseUrlProvider(server.url("/")), tokens)

                    client.execute(client.request("api/books")!!.build()).close()

                    tokens.invalidateCalls shouldBe 1
                }
            }

            it("TC-13: still returns the 401") {
                withMockServer { server ->
                    server.enqueue(reply(401))
                    val client = sut(TestServerBaseUrlProvider(server.url("/")), TestAuthTokenProvider(randomString()))

                    client.execute(client.request("api/auth/me")!!.build()).use { it.code } shouldBe 401
                }
            }
        }

        context("when a sign-in request gets a 401") {
            it("TC-14: keeps the token, for each sign-in path, with or without a server path prefix") {
                val emptyJson = "{}".toRequestBody("application/json".toMediaType())

                checkAll(
                    Exhaustive.of("api/auth/login", "api/auth/register"),
                    Exhaustive.of("/", "/omnibus"),
                ) { path, prefix ->
                    withMockServer { server ->
                        server.enqueue(reply(401))
                        val tokens = TestAuthTokenProvider(randomString())
                        val client = sut(TestServerBaseUrlProvider(server.url(prefix)), tokens)

                        client.execute(client.request(path)!!.post(emptyJson).build()).close()

                        tokens.invalidateCalls shouldBe 0
                    }
                }
            }
        }

        context("when a request with no bearer header gets a 401") {
            it("TC-15: invalidates nothing") {
                withMockServer { server ->
                    server.enqueue(reply(401))
                    val tokens = TestAuthTokenProvider(token = null)
                    val client = sut(TestServerBaseUrlProvider(server.url("/")), tokens)

                    client.execute(client.request("api/auth/me")!!.build()).close()

                    tokens.invalidateCalls shouldBe 0
                }
            }
        }

        context("when the answer is not a 401") {
            it("TC-15: keeps the token, for each other status") {
                checkAll(Exhaustive.of(200, 403, 500)) { code ->
                    withMockServer { server ->
                        server.enqueue(reply(code))
                        val tokens = TestAuthTokenProvider(randomString())
                        val client = sut(TestServerBaseUrlProvider(server.url("/")), tokens)

                        client.execute(client.request("api/auth/me")!!.build()).close()

                        tokens.invalidateCalls shouldBe 0
                    }
                }
            }
        }

        context("when the server can't be reached") {
            it("TC-15: throws IOException") {
                withMockServer { server ->
                    val client = sut(TestServerBaseUrlProvider(server.url("/")))
                    val request = client.request("api/auth/me")!!.build()
                    server.close()

                    shouldThrow<IOException> { client.execute(request) }
                }
            }
        }
    }
})
