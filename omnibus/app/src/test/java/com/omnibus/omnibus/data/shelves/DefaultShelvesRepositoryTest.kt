package com.omnibus.omnibus.data.shelves

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

class DefaultShelvesRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var client: OmnibusHttpClient
    lateinit var repository: DefaultShelvesRepository

    /** Every request the server receives, in arrival order. */
    val requests = ConcurrentLinkedQueue<RecordedRequest>()

    /** Answers the next request. Tests replace it. */
    var respond: (RecordedRequest) -> MockResponse = { MockResponse.Builder().code(404).build() }

    /** Answers by exact path. A path not in [routes] gets a 404. */
    fun route(routes: Map<String, () -> MockResponse>) {
        respond = { request -> routes[request.url.encodedPath]?.invoke() ?: MockResponse.Builder().code(404).build() }
    }

    fun json(code: Int, body: String): MockResponse = MockResponse.Builder().code(code).body(body).build()

    fun newRepository(dispatcher: CoroutineDispatcher) =
        DefaultShelvesRepository(ioDispatcher = dispatcher, omnibusHttpClient = client)

    /** Every endpoint answering normally: the shelves, no membership, and a matching smart shelf. */
    val healthy: Map<String, () -> MockResponse> = mapOf(
        SHELVES to { json(200, SHELVES_JSON) },
        MEMBER to { json(200, "[2]") },
        SMART to { json(200, "[5]") },
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        server = MockWebServer().apply { start() }
        requests.clear()
        respond = { MockResponse.Builder().code(404).build() }
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

    describe("getBookShelves") {

        it("#116 TC-18: decodes shelves, membership and matching smart shelves") {
            route(healthy)

            repository.getBookShelves(BOOK) shouldBe BookShelvesResult.Success(
                BookShelves(
                    shelves = listOf(
                        ShelfSummary(1, 7, ShelfKind.Smart, "Old books", 14, listOf("c1", "c2")),
                        ShelfSummary(2, 7, ShelfKind.Manual, "Favourites", 9, listOf("a1", "b2", "c3", "d4")),
                        ShelfSummary(3, 8, ShelfKind.Manual, "Ann's", 2, emptyList()),
                        ShelfSummary(4, 7, ShelfKind.Wishlist, "Wish", 3, emptyList()),
                        ShelfSummary(5, 7, ShelfKind.Smart, "Recent", 6, listOf("e5")),
                    ),
                    memberShelfIds = setOf(2L),
                    matchingSmartShelfIds = setOf(5),
                ),
            )

            requests.size shouldBe 3
            requests.map { it.method }.toSet() shouldBe setOf("GET")
            requests.map { it.url.encodedPath }.toSet() shouldBe setOf(SHELVES, MEMBER, SMART)
            requests.forEach { it.headers["Authorization"] shouldBe "Bearer secret" }
        }

        it("#116 TC-19: tolerates missing cover_uuids, unknown kinds and extra keys") {
            route(
                healthy + (
                    SHELVES to {
                        json(
                            200,
                            """
                            [
                              {"id":1,"owner_user_id":7,"kind":"manual","name":"No covers","book_count":0,
                               "owner_username":"hunter","visibility":"public","accent":"#c8a04a"},
                              {"id":2,"owner_user_id":7,"kind":"series","name":"Unknown kind","book_count":4,"cover_uuids":["x"]},
                              {"id":3,"owner_user_id":7,"kind":"manual","name":"Kept","book_count":1,
                               "cover_uuids":["y"],"future_key":{"nested":true}}
                            ]
                            """.trimIndent(),
                        )
                    }
                    ),
            )

            val shelves = repository.getBookShelves(BOOK).shouldBeTypeOf<BookShelvesResult.Success>().shelves
            shelves.shelves shouldBe listOf(
                ShelfSummary(1, 7, ShelfKind.Manual, "No covers", 0, emptyList()),
                ShelfSummary(3, 7, ShelfKind.Manual, "Kept", 1, listOf("y")),
            )
        }

        it("#116 TC-19: an empty list is a success with no shelves") {
            route(healthy + (SHELVES to { json(200, "[]") }))

            val shelves = repository.getBookShelves(BOOK).shouldBeTypeOf<BookShelvesResult.Success>().shelves
            shelves.shelves shouldBe emptyList()
            shelves.memberShelfIds shouldBe setOf(2L)
        }

        val failures: List<Triple<String, () -> MockResponse, ShelvesError>> = listOf(
            Triple("HTTP 401", { json(401, "") }, ShelvesError.SessionExpired),
            Triple("HTTP 500 with a plain body", { json(500, "boom") }, ShelvesError.Http(500, "boom")),
            Triple("a body that is not JSON", { json(200, "not json") }, ShelvesError.UnexpectedResponse),
        )

        for (path in listOf(SHELVES, MEMBER)) {
            failures.forEach { (case, response, error) ->
                it("#116 TC-20: $case on $path gives $error") {
                    route(healthy + (path to response))

                    repository.getBookShelves(BOOK) shouldBe BookShelvesResult.Failure(error)
                }
            }

            // A socket dropped by a dispatched response is not reliable, so the server is closed instead.
            it("#116 TC-20: a dropped connection on $path gives Unreachable") {
                server.close()

                repository.getBookShelves(BOOK) shouldBe BookShelvesResult.Failure(ShelvesError.Unreachable)
            }
        }

        listOf(
            "HTTP 500" to { json(500, "boom") },
            "HTTP 404" to { json(404, "") },
            "a body that is not JSON" to { json(200, "not json") },
        ).forEach { (case, response) ->
            it("#116 TC-21: $case on the smart read degrades to no smart shelves") {
                route(healthy + (SMART to response))

                val result = repository.getBookShelves(BOOK).shouldBeTypeOf<BookShelvesResult.Success>().shelves
                result.matchingSmartShelfIds shouldBe emptySet()
                result.memberShelfIds shouldBe setOf(2L)
                result.shelves.size shouldBe 5
            }
        }

        it("#116 TC-22: a blank uuid is NotFound and sends nothing") {
            listOf("", "   ").forEach { blank ->
                repository.getBookShelves(blank) shouldBe BookShelvesResult.Failure(ShelvesError.NotFound)
            }

            server.requestCount shouldBe 0
        }

        it("#116 TC-22: a uuid with a slash, dots and a space is one encoded path segment") {
            respond = { json(200, "[]") }

            repository.getBookShelves(PATH_UNSAFE_UUID)

            val member = requests.single { it.url.encodedPathSegments.size == 4 }
            member.url.encodedPathSegments.last() shouldBe "a%2F..%2Fb%20c"
            member.url.pathSegments.last() shouldBe PATH_UNSAFE_UUID
        }
    }

    describe("no server") {

        it("#116 TC-22: with no saved server every call gives NoServer and sends nothing") {
            baseUrlProvider.baseUrl = null

            repository.getBookShelves(BOOK) shouldBe BookShelvesResult.NoServer
            repository.getShelfRules(2) shouldBe ShelfRulesResult.NoServer
            repository.addBook(2, BOOK) shouldBe ShelfWriteResult.NoServer
            repository.removeBook(2, BOOK) shouldBe ShelfWriteResult.NoServer

            server.requestCount shouldBe 0
        }
    }

    describe("getShelfRules") {

        it("#116 TC-23: decodes rules and tolerates unknown tokens") {
            route(
                mapOf(
                    "/api/shelves/5" to {
                        json(
                            200,
                            """
                            {"id":5,"name":"Old","kind":"smart","description":"x","match_mode":"any",
                             "rules":[{"field":"year","op":"gte","value":"1901"},
                                      {"field":"mood","op":"is","value":"x"},
                                      {"field":"tag","op":"blink","value":"y"}],
                             "unexpected":true}
                            """.trimIndent(),
                        )
                    },
                ),
            )

            repository.getShelfRules(5) shouldBe ShelfRulesResult.Success(
                ShelfRules(
                    matchMode = ShelfMatchMode.Any,
                    rules = listOf(
                        ShelfRule(ShelfRuleField.Year, ShelfRuleOp.Gte, "1901"),
                        ShelfRule(null, ShelfRuleOp.Is, "x"),
                        ShelfRule(ShelfRuleField.Tag, null, "y"),
                    ),
                ),
            )
            requests.single().url.encodedPath shouldBe "/api/shelves/5"
        }

        it("#116 TC-23: match_mode all maps to All and a missing one to null") {
            respond = { request ->
                json(200, if (request.url.encodedPath.endsWith("/5")) """{"match_mode":"all","rules":[]}""" else """{"rules":[]}""")
            }

            repository.getShelfRules(5) shouldBe ShelfRulesResult.Success(ShelfRules(ShelfMatchMode.All, emptyList()))
            repository.getShelfRules(6) shouldBe ShelfRulesResult.Success(ShelfRules(null, emptyList()))
        }

        listOf(
            "HTTP 404" to ({ json(404, "") } to ShelvesError.NotFound),
            "HTTP 401" to ({ json(401, "") } to ShelvesError.SessionExpired),
            "a body that is not JSON" to ({ json(200, "not json") } to ShelvesError.UnexpectedResponse),
        ).forEach { (case, pair) ->
            val (response, error) = pair
            it("#116 TC-23: $case gives $error") {
                route(mapOf("/api/shelves/5" to response))

                repository.getShelfRules(5) shouldBe ShelfRulesResult.Failure(error)
            }
        }
    }

    describe("addBook") {

        it("#116 TC-24: posts book_uuids and accepts 204") {
            respond = { MockResponse.Builder().code(204).build() }

            repository.addBook(2, BOOK) shouldBe ShelfWriteResult.Success

            val request = requests.single()
            request.method shouldBe "POST"
            request.url.encodedPath shouldBe "/api/shelves/2/books"
            request.body?.utf8() shouldBe """{"book_uuids":["a1b2c3"]}"""
            request.headers["Content-Type"] shouldBe "application/json; charset=utf-8"
        }

        it("#116 TC-24: a 200 reply is a success too") {
            respond = { json(200, "") }

            repository.addBook(2, BOOK) shouldBe ShelfWriteResult.Success
        }
    }

    describe("removeBook") {

        it("#116 TC-25: deletes the book path and accepts 204") {
            respond = { MockResponse.Builder().code(204).build() }

            repository.removeBook(2, BOOK) shouldBe ShelfWriteResult.Success

            val request = requests.single()
            request.method shouldBe "DELETE"
            request.url.encodedPath shouldBe "/api/shelves/2/books/a1b2c3"
            (request.body?.utf8() ?: "") shouldBe ""
        }
    }

    describe("writes") {

        val writeFailures: List<Triple<String, () -> MockResponse, ShelvesError>> = listOf(
            Triple("HTTP 401", { json(401, "") }, ShelvesError.SessionExpired),
            Triple("HTTP 403", { json(403, "") }, ShelvesError.Forbidden),
            Triple("HTTP 404", { json(404, "") }, ShelvesError.NotFound),
            Triple("HTTP 422 with a message", { json(422, """{"error":"bad shelf"}""") }, ShelvesError.Http(422, "bad shelf")),
            Triple("HTTP 500 without a message", { json(500, "") }, ShelvesError.Http(500, null)),
        )

        for (call in listOf("add", "remove")) {
            suspend fun write(): ShelfWriteResult =
                if (call == "add") repository.addBook(2, BOOK) else repository.removeBook(2, BOOK)

            writeFailures.forEach { (case, response, error) ->
                it("#116 TC-26: $call with $case gives $error") {
                    respond = { response() }

                    write() shouldBe ShelfWriteResult.Failure(error)
                }
            }

            it("#116 TC-26: $call with no saved server gives NoServer") {
                baseUrlProvider.baseUrl = null

                write() shouldBe ShelfWriteResult.NoServer
            }

            it("#116 TC-26: $call with a dropped connection gives Unreachable") {
                server.close()

                write() shouldBe ShelfWriteResult.Failure(ShelvesError.Unreachable)
            }

            it("#116 TC-26: $call with a blank uuid gives NotFound and sends nothing") {
                val result = if (call == "add") repository.addBook(2, " ") else repository.removeBook(2, " ")

                result shouldBe ShelfWriteResult.Failure(ShelvesError.NotFound)
                server.requestCount shouldBe 0
            }
        }

        it("#116 TC-26: cancelling an in-flight write throws CancellationException instead of a result") {
            respond = { MockResponse.Builder().code(204).headersDelay(500, TimeUnit.MILLISECONDS).build() }
            val dispatched = newRepository(Dispatchers.IO)

            coroutineScope {
                val pending = async(Dispatchers.IO) { dispatched.addBook(2, BOOK) }
                delay(100)
                pending.cancel()
                shouldThrow<CancellationException> { pending.await() }
            }
        }
    }
}) {
    private companion object {
        const val BOOK = "a1b2c3"
        const val PATH_UNSAFE_UUID = "a/../b c"
        const val SHELVES = "/api/shelves"
        const val MEMBER = "/api/shelves/containing/a1b2c3"
        const val SMART = "/api/shelves/containing/a1b2c3/smart"

        const val SHELVES_JSON = """
            [
                {"id":1,"owner_user_id":7,"owner_username":"hunter","kind":"smart","name":"Old books","visibility":"private","accent":null,"book_count":14,"cover_uuids":["c1","c2"]},
                {"id":2,"owner_user_id":7,"owner_username":"hunter","kind":"manual","name":"Favourites","visibility":"public","accent":"#c8a04a","book_count":9,"cover_uuids":["a1","b2","c3","d4"]},
                {"id":3,"owner_user_id":8,"owner_username":"ann","kind":"manual","name":"Ann's","visibility":"private","book_count":2,"cover_uuids":[]},
                {"id":4,"owner_user_id":7,"owner_username":"hunter","kind":"wishlist","name":"Wish","visibility":"private","book_count":3},
                {"id":5,"owner_user_id":7,"owner_username":"hunter","kind":"smart","name":"Recent","visibility":"private","book_count":6,"cover_uuids":["e5"]}
            ]
        """
    }
}
