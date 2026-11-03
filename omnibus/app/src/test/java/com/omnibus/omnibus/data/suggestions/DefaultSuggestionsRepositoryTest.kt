package com.omnibus.omnibus.data.suggestions

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okio.Buffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class DefaultSuggestionsRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var client: OmnibusHttpClient
    lateinit var repository: DefaultSuggestionsRepository
    val requests = ConcurrentLinkedQueue<RecordedRequest>()
    var respond: () -> MockResponse = { MockResponse.Builder().code(404).build() }

    fun json(code: Int, body: String): MockResponse = MockResponse.Builder().code(code).body(body).build()

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        server = MockWebServer().apply { start() }
        requests.clear()
        respond = { MockResponse.Builder().code(404).build() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return respond()
            }
        }
        baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/"))
        client = OmnibusHttpClient(
            baseUrlProvider = baseUrlProvider,
            authTokenProvider = FakeAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
        )
        repository = DefaultSuggestionsRepository(ioDispatcher = Dispatchers.Unconfined, omnibusHttpClient = client)
    }

    afterEach { server.close() }

    describe("getSuggestions") {

        it("#106 TC-1: a ready body decodes its items in order, mapping cover_url to coverPath") {
            respond = { json(200, READY_JSON) }

            val answer = repository.getSuggestions("uuid-1").shouldBeTypeOf<SuggestionsResult.Success>().answer

            answer shouldBe SuggestionsAnswer.Ready(
                listOf(
                    BookSuggestion(
                        hardcoverId = 11,
                        hardcoverUrl = "https://hardcover.app/books/first",
                        title = "The Dispossessed",
                        author = "Ursula K. Le Guin",
                        coverPath = "/api/suggestions/uuid-1/1/cover",
                    ),
                    BookSuggestion(
                        hardcoverId = 12,
                        hardcoverUrl = "https://hardcover.app/books/second",
                        title = "Piranesi",
                        author = "Susanna Clarke",
                        coverPath = null,
                    ),
                ),
            )
        }

        it("#106 TC-2: a null or missing cover_url and missing fields decode to defaults") {
            respond = {
                json(
                    200,
                    """
                    {"status": "ready", "items": [
                      {"hardcover_id": 5, "hardcover_url": "https://hardcover.app/books/a", "title": "A", "author": "B", "cover_url": null},
                      {"hardcover_url": "https://hardcover.app/books/c"}
                    ]}
                    """,
                )
            }

            val answer = repository.getSuggestions("uuid-1").shouldBeTypeOf<SuggestionsResult.Success>().answer

            val items = answer.shouldBeTypeOf<SuggestionsAnswer.Ready>().items
            items[0].coverPath shouldBe null
            items[1] shouldBe BookSuggestion(
                hardcoverId = 0,
                hardcoverUrl = "https://hardcover.app/books/c",
                title = "",
                author = "",
                coverPath = null,
            )
        }

        listOf(
            "pending" to SuggestionsAnswer.Pending,
            "unavailable" to SuggestionsAnswer.Unavailable,
            "not_configured" to SuggestionsAnswer.NotConfigured,
        ).forEach { (status, answer) ->
            it("#106 TC-3: status $status decodes to $answer") {
                respond = { json(200, """{"status": "$status"}""") }

                repository.getSuggestions("uuid-1") shouldBe SuggestionsResult.Success(answer)
            }
        }

        listOf(
            "an empty items array" to """{"status": "ready", "items": []}""",
            "no items key" to """{"status": "ready"}""",
        ).forEach { (name, body) ->
            it("#106 TC-4: a ready body with $name gives an empty Ready") {
                respond = { json(200, body) }

                repository.getSuggestions("uuid-1") shouldBe
                    SuggestionsResult.Success(SuggestionsAnswer.Ready(emptyList()))
            }
        }

        it("#106 TC-5: reads api/ebooks/{uuid}/suggestions with a GET and the bearer token") {
            respond = { json(200, """{"status": "pending"}""") }

            repository.getSuggestions("uuid-1")

            val request = requests.single()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/ebooks/uuid-1/suggestions"
            request.headers["Authorization"] shouldBe "Bearer secret"
        }

        it("#106 TC-6: the book uuid is encoded as a single path segment") {
            respond = { json(200, """{"status": "pending"}""") }

            repository.getSuggestions("a/b c")

            requests.single().url.encodedPath shouldBe "/api/ebooks/a%2Fb%20c/suggestions"
        }

        it("#106 TC-7: an unknown or missing status gives UnexpectedResponse") {
            respond = { json(200, """{"status": "archived"}""") }
            repository.getSuggestions("uuid-1") shouldBe
                SuggestionsResult.Failure(SuggestionsError.UnexpectedResponse)

            respond = { json(200, """{"items": []}""") }
            repository.getSuggestions("uuid-1") shouldBe
                SuggestionsResult.Failure(SuggestionsError.UnexpectedResponse)
        }

        listOf(
            "malformed JSON" to "<html>",
            "a non-object body" to "[]",
        ).forEach { (name, body) ->
            it("#106 TC-8: a body that does not decode gives UnexpectedResponse, for $name") {
                respond = { json(200, body) }

                repository.getSuggestions("uuid-1") shouldBe
                    SuggestionsResult.Failure(SuggestionsError.UnexpectedResponse)
            }
        }

        listOf(
            401 to SuggestionsError.SessionExpired,
            500 to SuggestionsError.Http(500),
            404 to SuggestionsError.Http(404),
        ).forEach { (code, error) ->
            it("#106 TC-9: HTTP $code gives $error") {
                respond = { json(code, "") }

                repository.getSuggestions("uuid-1") shouldBe SuggestionsResult.Failure(error)
            }
        }

        it("#106 TC-10: an unreachable server gives Unreachable") {
            // A socket dropped by a dispatched response is not reliable, so the server is closed instead.
            server.close()

            repository.getSuggestions("uuid-1") shouldBe SuggestionsResult.Failure(SuggestionsError.Unreachable)
        }

        it("#106 TC-11: with no saved server getSuggestions gives NoServer and sends nothing") {
            baseUrlProvider.baseUrl = null

            repository.getSuggestions("uuid-1") shouldBe SuggestionsResult.NoServer
            requests.size shouldBe 0
        }

        it("#106 TC-12: cancelling an in-flight read throws CancellationException") {
            respond = { json(200, """{"status": "pending"}""").newBuilder().headersDelay(500, TimeUnit.MILLISECONDS).build() }
            val dispatched = DefaultSuggestionsRepository(ioDispatcher = Dispatchers.IO, omnibusHttpClient = client)

            coroutineScope {
                val inFlight = async(Dispatchers.IO) { dispatched.getSuggestions("uuid-1") }
                delay(100)
                inFlight.cancel()
                shouldThrow<CancellationException> { inFlight.await() }
            }
        }
    }

    describe("getCover") {

        it("#106 TC-13: getCover strips the leading slash and sends the bearer token") {
            respond = { MockResponse.Builder().code(200).body(Buffer().write(byteArrayOf(1, 2, 3))).build() }

            repository.getCover("/api/suggestions/u1/3/cover")

            val request = requests.single()
            request.url.encodedPath shouldBe "/api/suggestions/u1/3/cover"
            request.headers["Authorization"] shouldBe "Bearer secret"
            repository.getCover("api/suggestions/u1/3/cover")
            requests.last().url.encodedPath shouldBe "/api/suggestions/u1/3/cover"
            requests.none { it.url.encodedPath.contains("//") } shouldBe true
        }

        it("#106 TC-14: getCover keeps the base-URL prefix of a proxied server") {
            baseUrlProvider.baseUrl = server.url("/omnibus/")
            respond = { MockResponse.Builder().code(200).body(Buffer().write(byteArrayOf(1))).build() }

            repository.getCover("/api/suggestions/u/1/cover")

            requests.single().url.encodedPath shouldBe "/omnibus/api/suggestions/u/1/cover"
        }

        it("#106 TC-15: a 200 cover gives the bytes and the mime type") {
            respond = {
                MockResponse.Builder()
                    .code(200)
                    .body(Buffer().write(byteArrayOf(1, 2, 3)))
                    .addHeader("Content-Type", "image/jpeg")
                    .build()
            }

            val found = repository.getCover("/api/suggestions/u/1/cover")
                .shouldBeTypeOf<SuggestionCoverResult.Found>()

            found.bytes.toList() shouldBe listOf<Byte>(1, 2, 3)
            found.mimeType shouldBe "image/jpeg"
        }

        it("#106 TC-15: a 200 cover with no Content-Type gives a null mime type") {
            respond = {
                MockResponse.Builder()
                    .code(200)
                    .body(Buffer().write(byteArrayOf(1, 2, 3)))
                    .removeHeader("Content-Type")
                    .build()
            }

            val found = repository.getCover("/api/suggestions/u/1/cover")
                .shouldBeTypeOf<SuggestionCoverResult.Found>()

            found.mimeType shouldBe null
        }

        it("#106 TC-16: a 404 cover gives NotFound") {
            respond = { json(404, "") }

            repository.getCover("/api/suggestions/u/1/cover") shouldBe SuggestionCoverResult.NotFound
        }

        it("#106 TC-17: a 500, an unreachable server or no saved server gives a cover Failure") {
            respond = { json(500, "boom") }
            repository.getCover("/api/suggestions/u/1/cover") shouldBe SuggestionCoverResult.Failure

            server.close()
            repository.getCover("/api/suggestions/u/1/cover") shouldBe SuggestionCoverResult.Failure

            baseUrlProvider.baseUrl = null
            requests.clear()
            repository.getCover("/api/suggestions/u/1/cover") shouldBe SuggestionCoverResult.Failure
            requests.size shouldBe 0
        }

        listOf(
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
        ).forEach { path ->
            it("#106 TC-18: the invalid cover path $path gives NotFound without a request") {
                repository.getCover(path) shouldBe SuggestionCoverResult.NotFound

                requests.size shouldBe 0
            }
        }

        it("#106 TC-19: cancelling an in-flight cover read throws CancellationException") {
            respond = {
                MockResponse.Builder()
                    .code(200)
                    .body(Buffer().write(byteArrayOf(1)))
                    .headersDelay(500, TimeUnit.MILLISECONDS)
                    .build()
            }
            val dispatched = DefaultSuggestionsRepository(ioDispatcher = Dispatchers.IO, omnibusHttpClient = client)

            coroutineScope {
                val inFlight = async(Dispatchers.IO) { dispatched.getCover("/api/suggestions/u/1/cover") }
                delay(100)
                inFlight.cancel()
                shouldThrow<CancellationException> { inFlight.await() }
            }
        }
    }
})

private const val READY_JSON = """
{
  "status": "ready",
  "items": [
    {"hardcover_id": 11, "hardcover_url": "https://hardcover.app/books/first", "title": "The Dispossessed",
     "author": "Ursula K. Le Guin", "cover_url": "/api/suggestions/uuid-1/1/cover", "list_count": 4},
    {"hardcover_id": 12, "hardcover_url": "https://hardcover.app/books/second", "title": "Piranesi",
     "author": "Susanna Clarke"}
  ]
}
"""
