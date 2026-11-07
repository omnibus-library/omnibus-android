package com.omnibus.omnibus.data.shelves

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.types.shouldBeTypeOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl

/** This shelf as the server writes it. */
private fun ShelfSummary.toJson(): String =
    """{"id":$id,"owner_user_id":$ownerUserId,"kind":"${kind.name.lowercase()}","name":"$name",""" +
        """"book_count":$bookCount,"cover_uuids":[${coverUuids.joinToString(",") { "\"$it\"" }}]}"""

/** Answers by exact path, and a 404 for any other. */
private fun routes(routes: Map<String, MockResponse>): Dispatcher = object : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse = routes[request.url.encodedPath] ?: reply(404)
}

/** The three reads behind one book's shelves, each answering an empty list. */
private fun healthyRoutes(uuid: String): Map<String, MockResponse> = mapOf(
    "/api/shelves" to reply(200, "[]"),
    "/api/shelves/containing/$uuid" to reply(200, "[]"),
    "/api/shelves/containing/$uuid/smart" to reply(200, "[]"),
)

private val add: suspend DefaultShelvesRepository.(String) -> ShelfWriteResult = { addBook(randomLong(), it) }

private val remove: suspend DefaultShelvesRepository.(String) -> ShelfWriteResult = { removeBook(randomLong(), it) }

class DefaultShelvesRepositoryTest : DescribeSpec({

    fun sut(baseUrl: HttpUrl? = null): DefaultShelvesRepository = DefaultShelvesRepository(
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

    describe("reading a book's shelves") {

        it("#116 TC-18: combines the three reads") {
            withMockServer { server ->
                val uuid = randomString()
                val shelves = listOf(randomShelfSummary(), randomShelfSummary())
                val member = randomLong()
                val smart = randomLong()
                server.dispatcher = routes(
                    mapOf(
                        "/api/shelves" to reply(200, shelves.joinToString(",", "[", "]") { it.toJson() }),
                        "/api/shelves/containing/$uuid" to reply(200, "[$member]"),
                        "/api/shelves/containing/$uuid/smart" to reply(200, "[$smart]"),
                    ),
                )

                sut(server.url("/")).getBookShelves(uuid) shouldBe
                    BookShelvesResult.Success(BookShelves(shelves, setOf(member), setOf(smart)))
            }
        }

        it("#116 TC-19: leaves out a shelf of a kind it does not know") {
            withMockServer { server ->
                val uuid = randomString()
                val shelf = randomShelfSummary()
                val unknownKind = randomShelfSummary().toJson().replace(Regex(""""kind":"\w+""""), """"kind":"series"""")
                server.dispatcher = routes(healthyRoutes(uuid) + ("/api/shelves" to reply(200, "[$unknownKind,${shelf.toJson()}]")))

                val result = sut(server.url("/")).getBookShelves(uuid).shouldBeTypeOf<BookShelvesResult.Success>()

                result.shelves.shelves shouldBe listOf(shelf)
            }
        }

        it("#116 TC-19: tolerates a missing cover list along with unknown keys") {
            withMockServer { server ->
                val uuid = randomString()
                val shelf = randomShelfSummary().copy(coverUuids = emptyList())
                val sparse = """{"id":${shelf.id},"owner_user_id":${shelf.ownerUserId},"kind":"${shelf.kind.name.lowercase()}",""" +
                    """"name":"${shelf.name}","book_count":${shelf.bookCount},"future_key":{"nested":true}}"""
                server.dispatcher = routes(healthyRoutes(uuid) + ("/api/shelves" to reply(200, "[$sparse]")))

                val result = sut(server.url("/")).getBookShelves(uuid).shouldBeTypeOf<BookShelvesResult.Success>()

                result.shelves.shelves shouldBe listOf(shelf)
            }
        }

        it("#116 TC-20: fails regardless of which required read goes wrong, or how") {
            withMockServer { server ->
                val uuid = randomString()

                checkAll(
                    Exhaustive.of("/api/shelves", "/api/shelves/containing/$uuid"),
                    Exhaustive.of(
                        reply(401) to ShelvesError.SessionExpired,
                        reply(500, "boom") to ShelvesError.Http(500, "boom"),
                        reply(200, "not json") to ShelvesError.UnexpectedResponse,
                    ),
                ) { path, (response, error) ->
                    server.dispatcher = routes(healthyRoutes(uuid) + (path to response))

                    sut(server.url("/")).getBookShelves(uuid) shouldBe BookShelvesResult.Failure(error)
                }
            }
        }

        context("when the server cannot be reached") {

            it("#116 TC-20: reports Unreachable") {
                sut(unreachableUrl()).getBookShelves(randomString()) shouldBe
                    BookShelvesResult.Failure(ShelvesError.Unreachable)
            }
        }

        it("#116 TC-21: degrades to no smart shelves regardless of how the smart read goes wrong") {
            withMockServer { server ->
                val uuid = randomString()
                val member = randomLong()

                checkAll(Exhaustive.of(reply(500, "boom"), reply(404), reply(200, "not json"))) { smart ->
                    server.dispatcher = routes(
                        healthyRoutes(uuid) + mapOf(
                            "/api/shelves/containing/$uuid" to reply(200, "[$member]"),
                            "/api/shelves/containing/$uuid/smart" to smart,
                        ),
                    )

                    sut(server.url("/")).getBookShelves(uuid) shouldBe
                        BookShelvesResult.Success(BookShelves(emptyList(), setOf(member), emptySet()))
                }
            }
        }

        context("when the uuid is blank") {

            it("#116 TC-22: reports NotFound without asking the server, regardless of the blank") {
                withMockServer { server ->
                    checkAll(Exhaustive.of("", "   ")) { uuid ->
                        sut(server.url("/")).getBookShelves(uuid) shouldBe BookShelvesResult.Failure(ShelvesError.NotFound)

                        server.requestCount shouldBe 0
                    }
                }
            }
        }

        it("#116 TC-22: encodes an unsafe uuid as one path segment") {
            withMockServer { server ->
                val uuid = "a/../b c"
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse = reply(200, "[]")
                }

                sut(server.url("/")).getBookShelves(uuid)

                val paths = List(3) { server.takeRequest().url.encodedPath }
                paths.toSet() shouldBe setOf(
                    "/api/shelves",
                    "/api/shelves/containing/a%2F..%2Fb%20c",
                    "/api/shelves/containing/a%2F..%2Fb%20c/smart",
                )
            }
        }

        context("when no server is saved") {

            it("#116 TC-22: reports NoServer") {
                sut().getBookShelves(randomString()) shouldBe BookShelvesResult.NoServer
            }
        }
    }

    describe("reading a shelf's rules") {

        it("#116 TC-23: keeps a rule with a token it does not know, leaving that token empty") {
            withMockServer { server ->
                val shelf = randomLong()
                server.enqueue(
                    reply(
                        200,
                        """
                        {"match_mode":"any","unexpected":true,
                         "rules":[{"field":"year","op":"gte","value":"1901"},
                                  {"field":"mood","op":"is","value":"x"},
                                  {"field":"tag","op":"blink","value":"y"}]}
                        """.trimIndent(),
                    ),
                )

                sut(server.url("/")).getShelfRules(shelf) shouldBe ShelfRulesResult.Success(
                    ShelfRules(
                        matchMode = ShelfMatchMode.Any,
                        rules = listOf(
                            ShelfRule(ShelfRuleField.Year, ShelfRuleOp.Gte, "1901"),
                            ShelfRule(null, ShelfRuleOp.Is, "x"),
                            ShelfRule(ShelfRuleField.Tag, null, "y"),
                        ),
                    ),
                )
                server.takeRequest().url.encodedPath shouldBe "/api/shelves/$shelf"
            }
        }

        it("#116 TC-23: reads the match mode, defaulting to none") {
            withMockServer { server ->
                checkAll(
                    Exhaustive.of(
                        """{"match_mode":"all"}""" to ShelfMatchMode.All,
                        """{"match_mode":"any"}""" to ShelfMatchMode.Any,
                        "{}" to null,
                    ),
                ) { (body, mode) ->
                    server.enqueue(reply(200, body))

                    sut(server.url("/")).getShelfRules(randomLong()) shouldBe
                        ShelfRulesResult.Success(ShelfRules(mode, emptyList()))
                }
            }
        }

        it("#116 TC-23: reports each failure as its own error") {
            withMockServer { server ->
                checkAll(
                    Exhaustive.of(
                        reply(404) to ShelvesError.NotFound,
                        reply(401) to ShelvesError.SessionExpired,
                        reply(200, "not json") to ShelvesError.UnexpectedResponse,
                    ),
                ) { (response, error) ->
                    server.enqueue(response)

                    sut(server.url("/")).getShelfRules(randomLong()) shouldBe ShelfRulesResult.Failure(error)
                }
            }
        }

        context("when no server is saved") {

            it("#116 TC-22: reports NoServer") {
                sut().getShelfRules(randomLong()) shouldBe ShelfRulesResult.NoServer
            }
        }
    }

    describe("adding a book to a shelf") {

        it("#116 TC-24: posts the book's uuid to the shelf") {
            withMockServer { server ->
                val shelf = randomLong()
                val uuid = randomString()
                server.enqueue(reply(204))

                sut(server.url("/")).addBook(shelf, uuid)

                val request = server.takeRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/shelves/$shelf/books"
                request.headers["Content-Type"] shouldBe "application/json; charset=utf-8"
                request.body?.utf8() shouldBe """{"book_uuids":["$uuid"]}"""
            }
        }

        it("#116 TC-24: succeeds regardless of which 2xx the server answers") {
            withMockServer { server ->
                checkAll(Exhaustive.of(200, 204)) { code ->
                    server.enqueue(reply(code))

                    sut(server.url("/")).addBook(randomLong(), randomString()) shouldBe ShelfWriteResult.Success
                }
            }
        }
    }

    describe("removing a book from a shelf") {

        it("#116 TC-25: deletes the book from the shelf") {
            withMockServer { server ->
                val shelf = randomLong()
                val uuid = randomString()
                server.enqueue(reply(204))

                sut(server.url("/")).removeBook(shelf, uuid) shouldBe ShelfWriteResult.Success

                val request = server.takeRequest()
                request.method shouldBe "DELETE"
                request.url.encodedPath shouldBe "/api/shelves/$shelf/books/$uuid"
            }
        }
    }

    describe("writing to a shelf") {

        it("#116 TC-26: reports each failure status as its own error, regardless of the write") {
            withMockServer { server ->
                checkAll(
                    Exhaustive.of(add, remove),
                    Exhaustive.of(
                        reply(401) to ShelvesError.SessionExpired,
                        reply(403) to ShelvesError.Forbidden,
                        reply(404) to ShelvesError.NotFound,
                        reply(422, """{"error":"bad shelf"}""") to ShelvesError.Http(422, "bad shelf"),
                        reply(500) to ShelvesError.Http(500, null),
                    ),
                ) { write, (response, error) ->
                    server.enqueue(response)

                    sut(server.url("/")).write(randomString()) shouldBe ShelfWriteResult.Failure(error)
                }
            }
        }

        it("#116 TC-26: reads the server's message from whichever key or plain text it used") {
            withMockServer { server ->
                val message = randomString()

                checkAll(
                    Exhaustive.of(
                        """{"message":"$message"}""",
                        """{"detail":"$message"}""",
                        "  $message  ",
                    ),
                ) { body ->
                    server.enqueue(reply(500, body))

                    sut(server.url("/")).addBook(randomLong(), randomString()) shouldBe
                        ShelfWriteResult.Failure(ShelvesError.Http(500, message))
                }
            }
        }

        it("#116 TC-26: caps a long plain-text message") {
            withMockServer { server ->
                server.enqueue(reply(500, "x".repeat(1_000)))

                val result = sut(server.url("/")).addBook(randomLong(), randomString())
                    .shouldBeTypeOf<ShelfWriteResult.Failure>()

                result.error.shouldBeTypeOf<ShelvesError.Http>().serverMessage!! shouldHaveLength 300
            }
        }

        context("when no server is saved") {

            it("#116 TC-22, TC-26: reports NoServer regardless of the write") {
                checkAll(Exhaustive.of(add, remove)) { write ->
                    sut().write(randomString()) shouldBe ShelfWriteResult.NoServer
                }
            }
        }

        context("when the server cannot be reached") {

            it("#116 TC-26: reports Unreachable regardless of the write") {
                val closed = unreachableUrl()

                checkAll(Exhaustive.of(add, remove)) { write ->
                    sut(closed).write(randomString()) shouldBe ShelfWriteResult.Failure(ShelvesError.Unreachable)
                }
            }
        }

        context("when the uuid is blank") {

            it("#116 TC-26: reports NotFound without asking the server, regardless of the write") {
                withMockServer { server ->
                    checkAll(Exhaustive.of(add, remove)) { write ->
                        sut(server.url("/")).write(" ") shouldBe ShelfWriteResult.Failure(ShelvesError.NotFound)

                        server.requestCount shouldBe 0
                    }
                }
            }
        }
    }
})
