package com.omnibus.omnibus.data.suggestions

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl
import okio.Buffer

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A 200 cover response carrying [bytes]. */
private fun coverReply(bytes: ByteArray): MockResponse.Builder =
    MockResponse.Builder().code(200).body(Buffer().write(bytes))

/** This suggestion as the server writes it, with an extra key the app does not read. */
private fun BookSuggestion.toJson(): String =
    """{"hardcover_id":$hardcoverId,"hardcover_url":"$hardcoverUrl","title":"$title","author":"$author",""" +
        """"cover_url":${coverPath?.let { "\"$it\"" } ?: "null"},"list_count":4}"""

/** A path the app accepts as a suggestion cover. */
private fun randomCoverPath(): String = "/api/suggestions/${randomString()}/${randomInt()}/cover"

/** A base URL with nothing listening on it. */
private fun closedServerUrl(): HttpUrl = MockWebServer().use { server ->
    server.start()
    server.url("/")
}

/** A running server, closed afterwards. */
private suspend fun withServer(test: suspend (MockWebServer) -> Unit) {
    MockWebServer().use { server ->
        server.start()
        test(server)
    }
}

class DefaultSuggestionsRepositoryTest : DescribeSpec({

    fun sut(baseUrl: HttpUrl? = null): DefaultSuggestionsRepository = DefaultSuggestionsRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading a book's suggestions") {

        context("when the server is ready") {

            it("#106 TC-1: gives the items in the server's order") {
                withServer { server ->
                    val items = listOf(randomBookSuggestion(), randomBookSuggestion())
                    val body = items.joinToString(",", """{"status":"ready","items":[""", "]}") { it.toJson() }
                    server.enqueue(reply(200, body))

                    sut(server.url("/")).getSuggestions(randomString()) shouldBe
                        SuggestionsResult.Success(SuggestionsAnswer.Ready(items))
                }
            }

            it("#106 TC-2: fills in the fields an item leaves out") {
                withServer { server ->
                    val url = randomString()
                    server.enqueue(reply(200, """{"status":"ready","items":[{"hardcover_url":"$url"}]}"""))

                    sut(server.url("/")).getSuggestions(randomString()) shouldBe SuggestionsResult.Success(
                        SuggestionsAnswer.Ready(listOf(BookSuggestion(0, url, "", "", null))),
                    )
                }
            }

            it("#106 TC-4: is an empty answer regardless of how the items are absent") {
                withServer { server ->
                    checkAll(Exhaustive.of("""{"status":"ready","items":[]}""", """{"status":"ready"}""")) { body ->
                        server.enqueue(reply(200, body))

                        sut(server.url("/")).getSuggestions(randomString()) shouldBe
                            SuggestionsResult.Success(SuggestionsAnswer.Ready(emptyList()))
                    }
                }
            }
        }

        it("#106 TC-3: reads each status the server can still answer with") {
            withServer { server ->
                checkAll(
                    Exhaustive.of(
                        "pending" to SuggestionsAnswer.Pending,
                        "unavailable" to SuggestionsAnswer.Unavailable,
                        "not_configured" to SuggestionsAnswer.NotConfigured,
                    ),
                ) { (status, answer) ->
                    server.enqueue(reply(200, """{"status":"$status"}"""))

                    sut(server.url("/")).getSuggestions(randomString()) shouldBe SuggestionsResult.Success(answer)
                }
            }
        }

        it("#106 TC-5, TC-6: asks for the book with its uuid as one encoded path segment") {
            withServer { server ->
                server.enqueue(reply(200, """{"status":"pending"}"""))

                sut(server.url("/")).getSuggestions("a/b c")

                val request = server.takeRequest()
                request.method shouldBe "GET"
                request.url.encodedPath shouldBe "/api/ebooks/a%2Fb%20c/suggestions"
            }
        }

        it("#106 TC-7, TC-8: reports UnexpectedResponse regardless of how the body is unreadable") {
            withServer { server ->
                checkAll(Exhaustive.of("""{"status":"archived"}""", """{"items":[]}""", "<html>", "[]")) { body ->
                    server.enqueue(reply(200, body))

                    sut(server.url("/")).getSuggestions(randomString()) shouldBe
                        SuggestionsResult.Failure(SuggestionsError.UnexpectedResponse)
                }
            }
        }

        it("#106 TC-9: reports each failure status as its own error") {
            withServer { server ->
                checkAll(
                    Exhaustive.of(
                        401 to SuggestionsError.SessionExpired,
                        404 to SuggestionsError.Http(404),
                        500 to SuggestionsError.Http(500),
                    ),
                ) { (code, error) ->
                    server.enqueue(reply(code))

                    sut(server.url("/")).getSuggestions(randomString()) shouldBe SuggestionsResult.Failure(error)
                }
            }
        }

        context("when the server cannot be reached") {

            it("#106 TC-10: reports Unreachable") {
                sut(closedServerUrl()).getSuggestions(randomString()) shouldBe
                    SuggestionsResult.Failure(SuggestionsError.Unreachable)
            }
        }

        context("when no server is saved") {

            it("#106 TC-11: reports NoServer") {
                sut().getSuggestions(randomString()) shouldBe SuggestionsResult.NoServer
            }
        }
    }

    describe("reading a suggestion's cover") {

        it("#106 TC-13: asks for the same path regardless of a leading slash") {
            withServer { server ->
                val path = randomCoverPath()

                checkAll(Exhaustive.of(path, path.removePrefix("/"))) { asked ->
                    server.enqueue(coverReply(ByteArray(1)).build())

                    sut(server.url("/")).getCover(asked)

                    server.takeRequest().url.encodedPath shouldBe path
                }
            }
        }

        context("when the server sits behind a path prefix") {

            it("#106 TC-14: keeps the prefix") {
                withServer { server ->
                    val path = randomCoverPath()
                    server.enqueue(coverReply(ByteArray(1)).build())

                    sut(server.url("/omnibus/")).getCover(path)

                    server.takeRequest().url.encodedPath shouldBe "/omnibus$path"
                }
            }
        }

        context("when the server has the cover") {

            it("#106 TC-15: gives the cover") {
                withServer { server ->
                    val bytes = randomString().encodeToByteArray()
                    val type = "image/${randomString()}"
                    server.enqueue(coverReply(bytes).addHeader("Content-Type", type).build())

                    val found = sut(server.url("/")).getCover(randomCoverPath()).shouldBeTypeOf<SuggestionCoverResult.Found>()

                    found.bytes.toList() shouldBe bytes.toList()
                    found.mimeType shouldBe type
                }
            }

            context("when the server names no type") {

                it("#106 TC-15: gives no type") {
                    withServer { server ->
                        server.enqueue(coverReply(ByteArray(1)).removeHeader("Content-Type").build())

                        val found = sut(server.url("/")).getCover(randomCoverPath()).shouldBeTypeOf<SuggestionCoverResult.Found>()

                        found.mimeType shouldBe null
                    }
                }
            }
        }

        context("when the server has no such cover") {

            it("#106 TC-16: reports NotFound") {
                withServer { server ->
                    server.enqueue(reply(404))

                    sut(server.url("/")).getCover(randomCoverPath()) shouldBe SuggestionCoverResult.NotFound
                }
            }
        }

        context("when the server fails") {

            it("#106 TC-17: reports Failure") {
                withServer { server ->
                    server.enqueue(reply(500))

                    sut(server.url("/")).getCover(randomCoverPath()) shouldBe SuggestionCoverResult.Failure
                }
            }
        }

        context("when the server cannot be reached") {

            it("#106 TC-17: reports Failure") {
                sut(closedServerUrl()).getCover(randomCoverPath()) shouldBe SuggestionCoverResult.Failure
            }
        }

        context("when no server is saved") {

            it("#106 TC-17: reports Failure") {
                sut().getCover(randomCoverPath()) shouldBe SuggestionCoverResult.Failure
            }
        }

        context("when the path is not a suggestion cover") {

            it("#106 TC-18: reports NotFound without asking the server, regardless of the path") {
                withServer { server ->
                    checkAll(
                        Exhaustive.of(
                            "https://evil/x",
                            "/api/../x",
                            "/api/suggestions/../1/cover",
                            "/api/suggestions/./1/cover",
                            "/api/other",
                            "/api/suggestions/u/x/cover",
                            "/api/suggestions/u/1/cover?x=1",
                            "/api/suggestions/%2E%2E/1/cover",
                            "/api/suggestions/%2e/1/cover",
                            "/api/suggestions/a\\b/1/cover",
                            "//evil.example/api/suggestions/u/1/cover",
                            "",
                        ),
                    ) { path ->
                        sut(server.url("/")).getCover(path) shouldBe SuggestionCoverResult.NotFound

                        server.requestCount shouldBe 0
                    }
                }
            }
        }
    }
})
