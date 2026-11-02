package com.omnibus.omnibus.data.authors

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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class DefaultAuthorsRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var client: OmnibusHttpClient
    lateinit var repository: DefaultAuthorsRepository
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
        repository = DefaultAuthorsRepository(ioDispatcher = Dispatchers.Unconfined, omnibusHttpClient = client)
    }

    afterEach { server.close() }

    describe("getAuthor") {

        it("#105 TC-1: reads the author and their books from api/authors/{id} with the bearer token") {
            respond = { json(200, AUTHOR_JSON) }

            val result = repository.getAuthor(7)

            val author = result.shouldBeTypeOf<AuthorResult.Success>().author
            author.id shouldBe 7
            author.name shouldBe "Ursula K. Le Guin"
            author.books.map { it.id } shouldContainExactly listOf(1L, 2L)
            author.books.map { it.title } shouldContainExactly listOf("The Dispossessed", "Lavinia")
            author.books[0].publishedDate shouldBe "1974-05-01"
            val request = requests.single()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/authors/7"
            request.headers["Authorization"] shouldBe "Bearer secret"
        }

        it("#105 TC-2: an author with no books decodes to an empty list") {
            respond = { json(200, """{"id": 7, "name": "A", "book_count": 0, "books": []}""") }

            repository.getAuthor(7).shouldBeTypeOf<AuthorResult.Success>().author.books shouldBe emptyList()
        }

        listOf(
            Triple("HTTP 401", { json(401, "") }, AuthorsError.SessionExpired),
            Triple("HTTP 404", { json(404, "") }, AuthorsError.NotFound),
            Triple("HTTP 500", { json(500, "boom") }, AuthorsError.Http(500)),
            Triple("a body that is not JSON", { json(200, "<html>") }, AuthorsError.UnexpectedResponse),
            Triple("a body without an id", { json(200, """{"name": "A"}""") }, AuthorsError.UnexpectedResponse),
        ).forEach { (name, response, error) ->
            it("#105 TC-3: $name gives $error") {
                respond = response

                repository.getAuthor(7) shouldBe AuthorResult.Failure(error)
            }
        }

        it("#105 TC-4: a dropped connection gives Unreachable") {
            // A socket dropped by a dispatched response is not reliable, so the server is closed instead.
            server.close()

            repository.getAuthor(7) shouldBe AuthorResult.Failure(AuthorsError.Unreachable)
        }

        it("#105 TC-5: with no saved server it gives NoServer and sends nothing") {
            baseUrlProvider.baseUrl = null

            repository.getAuthor(7) shouldBe AuthorResult.NoServer
            requests.size shouldBe 0
        }

        it("#105 TC-6: cancelling an in-flight read throws CancellationException instead of a result") {
            respond = { json(200, AUTHOR_JSON).newBuilder().headersDelay(500, TimeUnit.MILLISECONDS).build() }
            val dispatched = DefaultAuthorsRepository(ioDispatcher = Dispatchers.IO, omnibusHttpClient = client)

            coroutineScope {
                val pending = async(Dispatchers.IO) { dispatched.getAuthor(7) }
                delay(100)
                pending.cancel()
                shouldThrow<CancellationException> { pending.await() }
            }
        }
    }
})

private const val AUTHOR_JSON = """
{
  "id": 7,
  "name": "Ursula K. Le Guin",
  "sort": "Le Guin, Ursula K.",
  "book_count": 2,
  "has_photo": true,
  "books": [
    {"id": 1, "unique_identifier": "uuid-1", "filename": "a.epub", "title": "The Dispossessed", "published": "1974-05-01",
     "creators": [{"name": "Ursula K. Le Guin", "id": 7}], "formats": ["epub"]},
    {"id": 2, "unique_identifier": "uuid-2", "filename": "b.epub", "title": "Lavinia",
     "creators": [{"name": "Ursula K. Le Guin", "id": 7}], "formats": ["epub"], "unknown_future_key": 1}
  ],
  "reading": []
}
"""
