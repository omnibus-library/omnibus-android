package com.omnibus.omnibus.data.merge

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
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit

class DefaultMergeRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var client: OmnibusHttpClient
    lateinit var repository: DefaultMergeRepository

    /** Every request the server receives, in arrival order. */
    val requests = ConcurrentLinkedQueue<RecordedRequest>()

    /** Answers the next request. Tests replace it. */
    var respond: (RecordedRequest) -> MockResponse = { MockResponse.Builder().code(404).build() }

    fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

    fun newRepository(dispatcher: CoroutineDispatcher) =
        DefaultMergeRepository(ioDispatcher = dispatcher, omnibusHttpClient = client)

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        server = MockWebServer().apply { start() }
        requests.clear()
        respond = { reply(404) }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return respond(request)
            }
        }
        baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/"))
        client = OmnibusHttpClient(
            baseUrlProvider = baseUrlProvider,
            authTokenProvider = FakeAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
        )
        repository = newRepository(Dispatchers.Unconfined)
    }

    afterEach { server.close() }

    describe("searchCandidates") {

        it("#120 TC-13: decodes ebook-shaped rows into books and sends one GET with q") {
            respond = {
                reply(
                    200,
                    """
                    [
                      {"id":1,"unique_identifier":"u-1","title":"Dune","creators":[{"name":"Frank Herbert"}],
                       "formats":["epub","m4b"],"series_id":5,"series":"Dune","published":"1965-08-01",
                       "cover_url":null,"accent":null,"future_key":{"nested":true}},
                      {"id":2,"unique_identifier":"u-2","title":"Dune Messiah","creators":[],"formats":["pdf"],
                       "published":null}
                    ]
                    """.trimIndent(),
                )
            }

            val books = repository.searchCandidates("dune").shouldBeTypeOf<MergeCandidatesResult.Success>().books

            books.map { it.displayTitle } shouldBe listOf("Dune", "Dune Messiah")
            books[0].formats shouldBe listOf("epub", "m4b")
            books[0].seriesId shouldBe 5L
            books[0].year shouldBe 1965
            books[1].year shouldBe null

            requests.size shouldBe 1
            val request = requests.single()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe "/api/books/merge/candidates"
            request.url.queryParameter("q") shouldBe "dune"
            request.headers["Authorization"] shouldBe "Bearer secret"
        }

        it("#120 TC-14: a query with spaces, &, = and non-ASCII is one q parameter whose value is the input") {
            respond = { reply(200, "[]") }
            val query = "a b&c=d é/#"

            repository.searchCandidates(query)

            requests.single().url.queryParameterNames shouldBe setOf("q")
            requests.single().url.queryParameterValues("q") shouldBe listOf(query)
        }

        it("#120 TC-15: a blank query answers an empty list without a request") {
            for (query in listOf("", "   ")) {
                repository.searchCandidates(query) shouldBe MergeCandidatesResult.Success(emptyList())
            }

            server.requestCount shouldBe 0
        }

        it("#120 TC-15: an empty array is an empty success") {
            respond = { reply(200, "[]") }

            repository.searchCandidates("dune") shouldBe MergeCandidatesResult.Success(emptyList())
        }

        it("#120 TC-15: rows without a usable uuid are dropped") {
            respond = {
                reply(
                    200,
                    """
                    [
                      {"id":1,"title":"No uuid"},
                      {"id":2,"unique_identifier":"","title":"Blank uuid"},
                      {"id":3,"unique_identifier":"u-3","title":"Valid"}
                    ]
                    """.trimIndent(),
                )
            }

            val books = repository.searchCandidates("uuid").shouldBeTypeOf<MergeCandidatesResult.Success>().books

            books.map { it.uniqueIdentifier } shouldBe listOf("u-3")
        }

        val failures = listOf(
            Triple("400 plain text", reply(400, "query too long"), MergeError.Http(400, "query too long")),
            Triple("401", reply(401, "nope"), MergeError.SessionExpired),
            Triple("403", reply(403, "nope"), MergeError.Forbidden),
            Triple("404", reply(404, "nope"), MergeError.NotFound),
            Triple("409 json", reply(409, """{"error":"x"}"""), MergeError.Conflict("x")),
            Triple("422 json", reply(422, """{"error":"bad"}"""), MergeError.Http(422, "bad")),
            Triple("500 plain text", reply(500, "boom"), MergeError.Http(500, "boom")),
            Triple("200 not json", reply(200, "<html>"), MergeError.UnexpectedResponse),
        )

        for ((label, response, expected) in failures) {
            it("#120 TC-16: search on $label maps to $expected") {
                respond = { response }

                repository.searchCandidates("dune") shouldBe MergeCandidatesResult.Failure(expected)
            }
        }

        it("#120 TC-16: search with a dropped connection gives Unreachable") {
            server.close()

            repository.searchCandidates("dune") shouldBe MergeCandidatesResult.Failure(MergeError.Unreachable)
        }
    }

    describe("mergeBooks") {

        it("#120 TC-17: posts both uuids as JSON and returns the receipt") {
            respond = { reply(200, """{"merge_log_id":42,"target_uuid":"tgt"}""") }

            repository.mergeBooks("src", "tgt") shouldBe MergeBooksResult.Success(MergeReceipt(42, "tgt"))

            val request = requests.single()
            request.method shouldBe "POST"
            request.url.encodedPath shouldBe "/api/books/merge"
            Json.parseToJsonElement(request.body!!.utf8()) shouldBe
                Json.parseToJsonElement("""{"source_uuid":"src","target_uuid":"tgt"}""")
            request.headers["Content-Type"]!! shouldStartWith "application/json"
            request.headers["Authorization"] shouldBe "Bearer secret"
        }

        val failures = listOf(
            Triple("401", reply(401, "nope"), MergeError.SessionExpired),
            Triple("403", reply(403, "nope"), MergeError.Forbidden),
            Triple("404", reply(404, "nope"), MergeError.NotFound),
            Triple("409", reply(409, """{"error":"x"}"""), MergeError.Conflict("x")),
            Triple("422", reply(422, """{"error":"same"}"""), MergeError.Http(422, "same")),
            Triple("500", reply(500, "boom"), MergeError.Http(500, "boom")),
            Triple("200 missing merge_log_id", reply(200, """{"target_uuid":"tgt"}"""), MergeError.UnexpectedResponse),
        )

        for ((label, response, expected) in failures) {
            it("#120 TC-18: merge on $label maps to $expected") {
                respond = { response }

                repository.mergeBooks("src", "tgt") shouldBe MergeBooksResult.Failure(expected)
            }
        }

        it("#120 TC-18: merge with a dropped connection gives Unreachable") {
            server.close()

            repository.mergeBooks("src", "tgt") shouldBe MergeBooksResult.Failure(MergeError.Unreachable)
        }

        it("#120 TC-18: a blank uuid gives NotFound and sends nothing") {
            repository.mergeBooks("", "tgt") shouldBe MergeBooksResult.Failure(MergeError.NotFound)
            repository.mergeBooks("src", " ") shouldBe MergeBooksResult.Failure(MergeError.NotFound)

            server.requestCount shouldBe 0
        }
    }

    describe("undoMerge") {

        it("#120 TC-19: posts the log id as a JSON number and returns the restored uuid") {
            respond = { reply(200, """{"restored_uuid":"src"}""") }

            repository.undoMerge(42) shouldBe UndoMergeResult.Success("src")

            val request = requests.single()
            request.method shouldBe "POST"
            request.url.encodedPath shouldBe "/api/books/merge/undo"
            Json.parseToJsonElement(request.body!!.utf8()) shouldBe Json.parseToJsonElement("""{"merge_log_id":42}""")
        }

        val failures = listOf(
            Triple("401", reply(401, "nope"), MergeError.SessionExpired),
            Triple("403", reply(403, "nope"), MergeError.Forbidden),
            Triple("404", reply(404, "nope"), MergeError.NotFound),
            Triple("409", reply(409, """{"error":"later merge"}"""), MergeError.Conflict("later merge")),
            Triple("500", reply(500, "boom"), MergeError.Http(500, "boom")),
            Triple("200 bad body", reply(200, """{"nope":true}"""), MergeError.UnexpectedResponse),
        )

        for ((label, response, expected) in failures) {
            it("#120 TC-19: undo on $label maps to $expected") {
                respond = { response }

                repository.undoMerge(42) shouldBe UndoMergeResult.Failure(expected)
            }
        }

        it("#120 TC-19: undo with a dropped connection gives Unreachable") {
            server.close()

            repository.undoMerge(42) shouldBe UndoMergeResult.Failure(MergeError.Unreachable)
        }

        it("#120 TC-19: a log id below 1 gives NotFound and sends nothing") {
            repository.undoMerge(0) shouldBe UndoMergeResult.Failure(MergeError.NotFound)
            repository.undoMerge(-1) shouldBe UndoMergeResult.Failure(MergeError.NotFound)

            server.requestCount shouldBe 0
        }
    }

    describe("without a server, and cancellation") {

        it("#120 TC-20: with no saved server each call answers NoServer and sends nothing") {
            baseUrlProvider.baseUrl = null

            repository.searchCandidates("dune") shouldBe MergeCandidatesResult.NoServer
            repository.mergeBooks("src", "tgt") shouldBe MergeBooksResult.NoServer
            repository.undoMerge(42) shouldBe UndoMergeResult.NoServer

            server.requestCount shouldBe 0
            requests.shouldBeEmpty()
        }

        it("#120 TC-20: cancelling a search in flight throws CancellationException instead of a result") {
            respond = { MockResponse.Builder().code(200).body("[]").headersDelay(500, TimeUnit.MILLISECONDS).build() }
            val dispatched = newRepository(Dispatchers.IO)

            coroutineScope {
                val pending = async(Dispatchers.IO) { dispatched.searchCandidates("dune") }
                delay(100)
                pending.cancel()
                shouldThrow<CancellationException> { pending.await() }
            }
        }
    }
})
