package com.omnibus.omnibus.data.shelves

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomInt
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
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl

/** This shelf as the server writes it. */
private fun ShelfSummary.toJson(): String =
    """{"id":$id,"owner_user_id":$ownerUserId,"kind":"${kind.name.lowercase()}","name":"$name",""" +
        """"book_count":$bookCount,"cover_uuids":[${coverUuids.joinToString(",") { "\"$it\"" }}]}"""

/** This shelf detail as the server writes it. */
private fun ShelfDetail.toJson(): String = buildJsonObject {
    put("id", shelf.id)
    put("owner_user_id", shelf.ownerUserId)
    put("owner_username", shelf.ownerUsername)
    put("owner_has_avatar", shelf.ownerHasAvatar)
    put("kind", shelf.kind.name.lowercase())
    put("name", shelf.name)
    put("description", description)
    put("visibility", shelf.visibility.name.lowercase())
    put("accent", shelf.accent)
    put("book_count", shelf.bookCount)
    put("match_mode", rules.matchMode?.name?.lowercase())
    put(
        "rules",
        JsonArray(
            rules.rules.map { rule ->
                buildJsonObject {
                    put("field", rule.field!!.wire)
                    put("op", rule.op!!.wire)
                    put("value", rule.value)
                }
            },
        ),
    )
}.toString()

/** A book page as the server writes it, with only the uuid of each book set. */
private fun pageJson(uuids: List<String>): String = buildJsonObject {
    put(
        "books",
        JsonArray(uuids.mapIndexed { index, uuid -> buildJsonObject { put("id", index + 1L); put("unique_identifier", uuid) } }),
    )
}.toString()

private fun ShelfDetail.withId(id: Long): ShelfDetail = copy(shelf = shelf.copy(id = id))

private val genreFilter = listOf(FilterClause(FilterField.Genre, FilterMode.Include, listOf("Fantasy")))

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

    fun sut(
        baseUrl: HttpUrl? = null,
        baseUrlProvider: TestServerBaseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
        authTokenProvider: TestAuthTokenProvider = TestAuthTokenProvider(),
    ): DefaultShelvesRepository = DefaultShelvesRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = baseUrlProvider,
            authTokenProvider = authTokenProvider,
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        baseUrlProvider = baseUrlProvider,
        authTokenProvider = authTokenProvider,
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
    describe("reading a shelf") {

        it("#232 TC-1: maps the shelf's identity, description, match mode and rules") {
            withMockServer { server ->
                val detail = randomShelfDetail()
                server.enqueue(reply(200, detail.toJson()))

                sut(server.url("/")).getShelfFlow(detail.shelf.id).toList() shouldBe
                    listOf(ShelfDetailResult.Success(detail))
                server.takeRequest().url.encodedPath shouldBe "/api/shelves/${detail.shelf.id}"
            }
        }

        it("#232 TC-1: reports Gone for a 404") {
            withMockServer { server ->
                server.enqueue(reply(404))

                sut(server.url("/")).getShelfFlow(randomLong()).toList() shouldBe listOf(ShelfDetailResult.Gone)
            }
        }

        it("#232 TC-1: reports each other failure as its own error") {
            withMockServer { server ->
                checkAll(
                    Exhaustive.of(
                        reply(401) to ShelvesError.SessionExpired,
                        reply(403) to ShelvesError.Forbidden,
                        reply(500, "boom") to ShelvesError.Http(500, "boom"),
                        reply(200, "not json") to ShelvesError.UnexpectedResponse,
                    ),
                ) { (response, error) ->
                    server.enqueue(response)

                    sut(server.url("/")).getShelfFlow(randomLong()).toList() shouldBe
                        listOf(ShelfDetailResult.Failure(error))
                }
            }
        }

        context("when the kind is one this app does not know") {

            it("#232 TC-1: reports UnexpectedResponse") {
                withMockServer { server ->
                    server.enqueue(reply(200, """{"id":1,"kind":"series","name":"Shelf"}"""))

                    sut(server.url("/")).getShelfFlow(1).toList() shouldBe
                        listOf(ShelfDetailResult.Failure(ShelvesError.UnexpectedResponse))
                }
            }
        }

        context("when the server cannot be reached") {

            it("#232 TC-1: reports Unreachable") {
                sut(unreachableUrl()).getShelfFlow(randomLong()).toList() shouldBe
                    listOf(ShelfDetailResult.Failure(ShelvesError.Unreachable))
            }
        }

        context("when no server is saved") {

            it("#232 TC-1: reports NoServer") {
                sut().getShelfFlow(randomLong()).toList() shouldBe listOf(ShelfDetailResult.NoServer)
            }
        }
    }

    describe("reading a shelf's books") {

        it("#232 TC-1: maps the books of the page") {
            withMockServer { server ->
                val uuids = List(randomInt(1..4)) { randomString() }.distinct()
                server.enqueue(reply(200, pageJson(uuids)))

                val result = sut(server.url("/")).getShelfBooksFlow(randomLong(), emptyList()).toList().single()

                result.shouldBeTypeOf<ShelfBooksResult.Success>().books.map { it.uniqueIdentifier } shouldBe uuids
            }
        }

        it("#232 TC-1: reports Gone for a 404") {
            withMockServer { server ->
                server.enqueue(reply(404))

                sut(server.url("/")).getShelfBooksFlow(randomLong(), emptyList()).toList() shouldBe
                    listOf(ShelfBooksResult.Gone)
            }
        }

        it("#232 TC-1: reports each other failure as its own error") {
            withMockServer { server ->
                checkAll(
                    Exhaustive.of(
                        reply(401) to ShelvesError.SessionExpired,
                        reply(500, "boom") to ShelvesError.Http(500, "boom"),
                        reply(200, "not json") to ShelvesError.UnexpectedResponse,
                    ),
                ) { (response, error) ->
                    server.enqueue(response)

                    sut(server.url("/")).getShelfBooksFlow(randomLong(), emptyList()).toList() shouldBe
                        listOf(ShelfBooksResult.Failure(error))
                }
            }
        }

        context("when the server cannot be reached") {

            it("#232 TC-1: reports Unreachable") {
                sut(unreachableUrl()).getShelfBooksFlow(randomLong(), emptyList()).toList() shouldBe
                    listOf(ShelfBooksResult.Failure(ShelvesError.Unreachable))
            }
        }

        context("when no server is saved") {

            it("#232 TC-1: reports NoServer") {
                sut().getShelfBooksFlow(randomLong(), emptyList()).toList() shouldBe listOf(ShelfBooksResult.NoServer)
            }
        }
    }

    describe("caching a shelf") {

        it("#232 TC-2: emits the cached detail first, then the fresh one") {
            withMockServer { server ->
                val shelf = randomLong()
                val first = randomShelfDetail().withId(shelf)
                val second = randomShelfDetail().withId(shelf)
                server.enqueue(reply(200, first.toJson()))
                server.enqueue(reply(200, second.toJson()))
                val repository = sut(server.url("/"), authTokenProvider = TestAuthTokenProvider(randomString()))

                repository.getShelfFlow(shelf).toList() shouldBe listOf(ShelfDetailResult.Success(first))
                repository.getShelfFlow(shelf).toList() shouldBe
                    listOf(ShelfDetailResult.Success(first), ShelfDetailResult.Success(second))
            }
        }

        it("#232 TC-2: does not emit a shelf cached for another token") {
            withMockServer { server ->
                val shelf = randomLong()
                val first = randomShelfDetail().withId(shelf)
                val second = randomShelfDetail().withId(shelf)
                server.enqueue(reply(200, first.toJson()))
                server.enqueue(reply(200, second.toJson()))
                val tokens = TestAuthTokenProvider(randomString())
                val repository = sut(server.url("/"), authTokenProvider = tokens)
                repository.getShelfFlow(shelf).toList()

                tokens.token = randomString()

                repository.getShelfFlow(shelf).toList() shouldBe listOf(ShelfDetailResult.Success(second))
            }
        }

        it("#232 TC-2: does not emit a shelf cached for another server") {
            withMockServer { first ->
                withMockServer { second ->
                    val shelf = randomLong()
                    val firstDetail = randomShelfDetail().withId(shelf)
                    val secondDetail = randomShelfDetail().withId(shelf)
                    first.enqueue(reply(200, firstDetail.toJson()))
                    second.enqueue(reply(200, secondDetail.toJson()))
                    val servers = TestServerBaseUrlProvider(first.url("/"))
                    val repository = sut(
                        baseUrlProvider = servers,
                        authTokenProvider = TestAuthTokenProvider(randomString()),
                    )
                    repository.getShelfFlow(shelf).toList()

                    servers.baseUrl = second.url("/")

                    repository.getShelfFlow(shelf).toList() shouldBe listOf(ShelfDetailResult.Success(secondDetail))
                }
            }
        }
    }

    describe("caching a shelf's books") {

        it("#232 TC-2: emits the cached unfiltered books first, then the fresh ones") {
            withMockServer { server ->
                val shelf = randomLong()
                val first = List(2) { randomString() }.distinct()
                val second = List(3) { randomString() }.distinct()
                server.enqueue(reply(200, pageJson(first)))
                server.enqueue(reply(200, pageJson(second)))
                val repository = sut(server.url("/"), authTokenProvider = TestAuthTokenProvider(randomString()))

                repository.getShelfBooksFlow(shelf, emptyList()).toList().map { it.bookUuids() } shouldBe listOf(first)
                repository.getShelfBooksFlow(shelf, emptyList()).toList().map { it.bookUuids() } shouldBe
                    listOf(first, second)
            }
        }

        it("#232 TC-2: emits nothing cached for a filtered read") {
            withMockServer { server ->
                val shelf = randomLong()
                val unfiltered = List(2) { randomString() }.distinct()
                val filtered = List(1) { randomString() }
                server.enqueue(reply(200, pageJson(unfiltered)))
                server.enqueue(reply(200, pageJson(filtered)))
                val repository = sut(server.url("/"), authTokenProvider = TestAuthTokenProvider(randomString()))
                repository.getShelfBooksFlow(shelf, emptyList()).toList()

                repository.getShelfBooksFlow(shelf, genreFilter).toList().map { it.bookUuids() } shouldBe listOf(filtered)
            }
        }

        it("#232 TC-2: a filtered read does not overwrite the cached unfiltered books") {
            withMockServer { server ->
                val shelf = randomLong()
                val unfiltered = List(2) { randomString() }.distinct()
                val filtered = List(1) { randomString() }
                val fresh = List(3) { randomString() }.distinct()
                server.enqueue(reply(200, pageJson(unfiltered)))
                server.enqueue(reply(200, pageJson(filtered)))
                server.enqueue(reply(200, pageJson(fresh)))
                val repository = sut(server.url("/"), authTokenProvider = TestAuthTokenProvider(randomString()))
                repository.getShelfBooksFlow(shelf, emptyList()).toList()
                repository.getShelfBooksFlow(shelf, genreFilter).toList()

                repository.getShelfBooksFlow(shelf, emptyList()).toList().map { it.bookUuids() } shouldBe
                    listOf(unfiltered, fresh)
            }
        }

        it("#232 TC-2: does not emit books cached for another token") {
            withMockServer { server ->
                val shelf = randomLong()
                val first = List(2) { randomString() }.distinct()
                val second = List(3) { randomString() }.distinct()
                server.enqueue(reply(200, pageJson(first)))
                server.enqueue(reply(200, pageJson(second)))
                val tokens = TestAuthTokenProvider(randomString())
                val repository = sut(server.url("/"), authTokenProvider = tokens)
                repository.getShelfBooksFlow(shelf, emptyList()).toList()

                tokens.token = randomString()

                repository.getShelfBooksFlow(shelf, emptyList()).toList().map { it.bookUuids() } shouldBe listOf(second)
            }
        }
    }

    describe("requesting a shelf's books") {

        it("#232 TC-3: sends no filter, sort or direction without clauses") {
            withMockServer { server ->
                val shelf = randomLong()
                server.enqueue(reply(200, "{}"))

                sut(server.url("/")).getShelfBooksFlow(shelf, emptyList()).toList()

                val url = server.takeRequest().url
                url.encodedPath shouldBe "/api/shelves/$shelf/page"
                url.queryParameter("filter") shouldBe null
                url.queryParameter("sort") shouldBe null
                url.queryParameter("dir") shouldBe null
            }
        }

        it("#232 TC-3: sends the clauses as the filter") {
            withMockServer { server ->
                server.enqueue(reply(200, "{}"))

                sut(server.url("/")).getShelfBooksFlow(randomLong(), genreFilter).toList()

                server.takeRequest().url.queryParameter("filter") shouldBe
                    """[{"field":"genre","mode":"include","values":["Fantasy"]}]"""
            }
        }

        it("#232 TC-3: keeps the server's book order") {
            withMockServer { server ->
                val uuids = List(randomInt(2..6)) { "${randomString()}-$it" }
                server.enqueue(reply(200, pageJson(uuids.reversed())))

                val result = sut(server.url("/")).getShelfBooksFlow(randomLong(), emptyList()).toList().single()

                result.bookUuids() shouldBe uuids.reversed()
            }
        }
    }

    describe("updating a shelf") {

        it("#232 TC-4: sends only the fields that are set") {
            withMockServer { server ->
                val shelf = randomLong()
                server.enqueue(reply(204))

                sut(server.url("/")).updateShelf(shelf, ShelfUpdate(name = "Weekend")) shouldBe ShelfWriteResult.Success

                val request = server.takeRequest()
                request.method shouldBe "PATCH"
                request.url.encodedPath shouldBe "/api/shelves/$shelf"
                request.body?.utf8() shouldBe """{"name":"Weekend"}"""
            }
        }

        it("#232 TC-4: sends the visibility as its wire token") {
            withMockServer { server ->
                checkAll(Exhaustive.enum<ShelfVisibility>()) { visibility ->
                    server.enqueue(reply(204))

                    sut(server.url("/")).updateShelf(randomLong(), ShelfUpdate(visibility = visibility))

                    server.takeRequest().body?.utf8() shouldBe
                        """{"visibility":"${visibility.name.lowercase()}"}"""
                }
            }
        }

        it("#232 TC-4: omits the rules when no match mode is sent") {
            withMockServer { server ->
                server.enqueue(reply(204))

                sut(server.url("/")).updateShelf(randomLong(), ShelfUpdate(rules = listOf(randomShelfRule())))

                server.takeRequest().body?.utf8() shouldBe "{}"
            }
        }

        it("#232 TC-4: sends the match mode with the rules") {
            withMockServer { server ->
                server.enqueue(reply(204))
                val rule = ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "x")

                sut(server.url("/")).updateShelf(randomLong(), ShelfUpdate(matchMode = ShelfMatchMode.Any, rules = listOf(rule)))

                server.takeRequest().body?.utf8() shouldBe
                    """{"match_mode":"any","rules":[{"field":"tag","op":"is","value":"x"}]}"""
            }
        }

        it("#232 TC-4: reports each failure status as its own error") {
            withMockServer { server ->
                checkAll(
                    Exhaustive.of(
                        reply(403) to ShelvesError.Forbidden,
                        reply(404) to ShelvesError.NotFound,
                        reply(409, """{"error":"taken"}""") to ShelvesError.Http(409, "taken"),
                        reply(422, """{"error":"bad"}""") to ShelvesError.Http(422, "bad"),
                    ),
                ) { (response, error) ->
                    server.enqueue(response)

                    sut(server.url("/")).updateShelf(randomLong(), ShelfUpdate(name = "x")) shouldBe
                        ShelfWriteResult.Failure(error)
                }
            }
        }

        it("#232 TC-4: drops the cached detail of that shelf") {
            withMockServer { server ->
                val shelf = randomLong()
                val cached = randomShelfDetail().withId(shelf)
                val fresh = randomShelfDetail().withId(shelf)
                server.enqueue(reply(200, cached.toJson()))
                server.enqueue(reply(204))
                server.enqueue(reply(200, fresh.toJson()))
                val repository = sut(server.url("/"), authTokenProvider = TestAuthTokenProvider(randomString()))
                repository.getShelfFlow(shelf).toList()
                repository.updateShelf(shelf, ShelfUpdate(name = "x"))

                repository.getShelfFlow(shelf).toList() shouldBe listOf(ShelfDetailResult.Success(fresh))
            }
        }

        it("#232 TC-4: drops the cached books of that shelf") {
            withMockServer { server ->
                val shelf = randomLong()
                val cached = List(2) { randomString() }.distinct()
                val fresh = List(3) { randomString() }.distinct()
                server.enqueue(reply(200, pageJson(cached)))
                server.enqueue(reply(204))
                server.enqueue(reply(200, pageJson(fresh)))
                val repository = sut(server.url("/"), authTokenProvider = TestAuthTokenProvider(randomString()))
                repository.getShelfBooksFlow(shelf, emptyList()).toList()
                repository.updateShelf(shelf, ShelfUpdate(name = "x"))

                repository.getShelfBooksFlow(shelf, emptyList()).toList().map { it.bookUuids() } shouldBe listOf(fresh)
            }
        }
    }

    describe("deleting a shelf") {

        it("#232 TC-4: sends a DELETE for the shelf") {
            withMockServer { server ->
                val shelf = randomLong()
                server.enqueue(reply(204))

                sut(server.url("/")).deleteShelf(shelf) shouldBe ShelfWriteResult.Success

                val request = server.takeRequest()
                request.method shouldBe "DELETE"
                request.url.encodedPath shouldBe "/api/shelves/$shelf"
            }
        }

        it("#232 TC-4: reports each failure status as its own error") {
            withMockServer { server ->
                checkAll(
                    Exhaustive.of(
                        reply(403) to ShelvesError.Forbidden,
                        reply(404) to ShelvesError.NotFound,
                        reply(409, """{"error":"taken"}""") to ShelvesError.Http(409, "taken"),
                        reply(422, """{"error":"bad"}""") to ShelvesError.Http(422, "bad"),
                    ),
                ) { (response, error) ->
                    server.enqueue(response)

                    sut(server.url("/")).deleteShelf(randomLong()) shouldBe ShelfWriteResult.Failure(error)
                }
            }
        }

        it("#232 TC-4: drops the cached detail of that shelf") {
            withMockServer { server ->
                val shelf = randomLong()
                val cached = randomShelfDetail().withId(shelf)
                val fresh = randomShelfDetail().withId(shelf)
                server.enqueue(reply(200, cached.toJson()))
                server.enqueue(reply(204))
                server.enqueue(reply(404))
                val repository = sut(server.url("/"), authTokenProvider = TestAuthTokenProvider(randomString()))
                repository.getShelfFlow(shelf).toList()
                repository.deleteShelf(shelf)

                repository.getShelfFlow(shelf).toList() shouldBe listOf(ShelfDetailResult.Gone)
            }
        }
    }

    describe("previewing rules") {

        it("#232 TC-4: posts the match mode and rules and reads the counts") {
            withMockServer { server ->
                server.enqueue(reply(200, """{"matched":3,"total":10}"""))
                val rule = ShelfRule(ShelfRuleField.Tag, ShelfRuleOp.Is, "x")

                sut(server.url("/")).previewRules(ShelfMatchMode.Any, listOf(rule)) shouldBe
                    RulePreviewResult.Success(RuleMatchCount(matched = 3, total = 10))

                val request = server.takeRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/shelves/preview"
                request.body?.utf8() shouldBe """{"match_mode":"any","rules":[{"field":"tag","op":"is","value":"x"}]}"""
            }
        }

        it("#232 TC-4: reports a 422 with the server's message") {
            withMockServer { server ->
                server.enqueue(reply(422, """{"error":"bad rule"}"""))

                sut(server.url("/")).previewRules(ShelfMatchMode.All, emptyList()) shouldBe
                    RulePreviewResult.Failure(ShelvesError.Http(422, "bad rule"))
            }
        }

        context("when the server cannot be reached") {

            it("#232 TC-4: reports Unreachable") {
                sut(unreachableUrl()).previewRules(ShelfMatchMode.All, emptyList()) shouldBe
                    RulePreviewResult.Failure(ShelvesError.Unreachable)
            }
        }

        context("when no server is saved") {

            it("#232 TC-4: reports NoServer") {
                sut().previewRules(ShelfMatchMode.All, emptyList()) shouldBe RulePreviewResult.NoServer
            }
        }
    }
})

/** The uuids of the books in a read that succeeded. */
private fun ShelfBooksResult.bookUuids(): List<String?> =
    shouldBeTypeOf<ShelfBooksResult.Success>().books.map { it.uniqueIdentifier }
