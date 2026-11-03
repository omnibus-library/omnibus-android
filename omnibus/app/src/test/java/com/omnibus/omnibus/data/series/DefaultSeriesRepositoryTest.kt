package com.omnibus.omnibus.data.series

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
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.coroutines.CoroutineContext
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

class DefaultSeriesRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    lateinit var client: OmnibusHttpClient
    lateinit var repository: DefaultSeriesRepository

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
        DefaultSeriesRepository(ioDispatcher = dispatcher, omnibusHttpClient = client)

    /** One book object of the series response. Null values are sent as JSON null. */
    fun bookJson(
        id: Long = 1,
        uniqueIdentifier: String? = "u$id",
        filename: String = "book$id.epub",
        title: String? = "Book $id",
        seriesIndex: String? = null,
        coverUrl: String? = null,
        accent: String? = null,
    ): String = """
        {"id":$id,"unique_identifier":${quoted(uniqueIdentifier)},"filename":${quoted(filename)},
         "title":${quoted(title)},"series_index":${quoted(seriesIndex)},"cover_url":${quoted(coverUrl)},
         "accent":${quoted(accent)}}
    """.trimIndent()

    /** A series response holding [books] in the given order. */
    fun seriesJson(vararg books: String): String =
        """{"id":4,"name":"Dune","book_count":${books.size},"books":[${books.joinToString(",")}]}"""

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

    describe("getSeries") {

        it("#104 TC-1: a 200 maps every book field") {
            route(
                mapOf(
                    SERIES_4 to {
                        json(
                            200,
                            seriesJson(
                                bookJson(
                                    id = 7,
                                    uniqueIdentifier = "u-7",
                                    filename = "dune.epub",
                                    title = "Dune",
                                    seriesIndex = "1",
                                    coverUrl = "/covers/u-7.jpg",
                                    accent = "#c8a04a",
                                ),
                            ),
                        )
                    },
                ),
            )

            repository.getSeries(4) shouldBe SeriesResult.Success(
                listOf(SeriesMember("u-7", "Dune", "1", "/covers/u-7.jpg", "#c8a04a", hasCover = true)),
            )
        }

        it("#104 TC-2: uuid falls back to the id when unique_identifier is null, and a present identifier wins") {
            route(
                mapOf(
                    SERIES_4 to {
                        json(
                            200,
                            seriesJson(
                                bookJson(id = 42, uniqueIdentifier = null),
                                bookJson(id = 1, uniqueIdentifier = "u-1"),
                            ),
                        )
                    },
                ),
            )

            val members = repository.getSeries(4).shouldBeTypeOf<SeriesResult.Success>().members
            members.map { it.uuid } shouldContainExactly listOf("42", "u-1")
        }

        it("#104 TC-3: a blank or missing title falls back to the filename, and a real title is kept") {
            route(
                mapOf(
                    SERIES_4 to {
                        json(
                            200,
                            seriesJson(
                                bookJson(id = 1, title = "", filename = "dune.epub"),
                                bookJson(id = 2, title = "   ", filename = "dune.epub"),
                                bookJson(id = 3, title = null, filename = "dune.epub"),
                                bookJson(id = 4, title = "Dune", filename = "dune.epub"),
                            ),
                        )
                    },
                ),
            )

            val members = repository.getSeries(4).shouldBeTypeOf<SeriesResult.Success>().members
            members.map { it.title } shouldContainExactly listOf("dune.epub", "dune.epub", "dune.epub", "Dune")
        }

        it("#104 TC-4: hasCover follows cover_url") {
            route(
                mapOf(
                    SERIES_4 to {
                        json(
                            200,
                            seriesJson(
                                bookJson(id = 1, coverUrl = null, accent = null),
                                bookJson(id = 2, coverUrl = "/c/b.jpg", accent = "#112233"),
                            ),
                        )
                    },
                ),
            )

            val members = repository.getSeries(4).shouldBeTypeOf<SeriesResult.Success>().members
            members[0].hasCover shouldBe false
            members[0].coverUrl shouldBe null
            members[0].accent shouldBe null
            members[1].hasCover shouldBe true
            members[1].coverUrl shouldBe "/c/b.jpg"
            members[1].accent shouldBe "#112233"
        }

        it("#104 TC-5: members are sorted by numeric series index") {
            route(
                mapOf(
                    SERIES_4 to {
                        json(
                            200,
                            seriesJson(
                                bookJson(id = 10, seriesIndex = "10"),
                                bookJson(id = 2, seriesIndex = "2"),
                                bookJson(id = 15, seriesIndex = "1.5"),
                                bookJson(id = 1, seriesIndex = "1"),
                            ),
                        )
                    },
                ),
            )

            val members = repository.getSeries(4).shouldBeTypeOf<SeriesResult.Success>().members
            members.map { it.seriesIndex } shouldContainExactly listOf("1", "1.5", "2", "10")
        }

        it("#104 TC-6: null and non-numeric indexes sort last in server order") {
            route(
                mapOf(
                    SERIES_4 to {
                        json(
                            200,
                            seriesJson(
                                bookJson(id = 1, seriesIndex = "II"),
                                bookJson(id = 2, seriesIndex = null),
                                bookJson(id = 3, seriesIndex = "2"),
                                bookJson(id = 4, seriesIndex = ""),
                                bookJson(id = 5, seriesIndex = "1"),
                                bookJson(id = 6, seriesIndex = "Omnibus"),
                            ),
                        )
                    },
                ),
            )

            val members = repository.getSeries(4).shouldBeTypeOf<SeriesResult.Success>().members
            members.map { it.uuid } shouldContainExactly listOf("u5", "u3", "u1", "u2", "u4", "u6")
        }

        it("#104 TC-7: ties keep the server order, and an ordered response is unchanged") {
            route(
                mapOf(
                    SERIES_4 to {
                        json(
                            200,
                            seriesJson(
                                bookJson(id = 1, seriesIndex = "2"),
                                bookJson(id = 2, seriesIndex = null),
                                bookJson(id = 3, seriesIndex = "2"),
                                bookJson(id = 4, seriesIndex = "2"),
                                bookJson(id = 5, seriesIndex = null),
                            ),
                        )
                    },
                ),
            )

            val members = repository.getSeries(4).shouldBeTypeOf<SeriesResult.Success>().members
            members.map { it.uuid } shouldContainExactly listOf("u1", "u3", "u4", "u2", "u5")

            val ordered = listOf(member("a", "1"), member("b", "1.5"), member("c", "2"), member("d", null))
            ordered.inSeriesOrder() shouldBe ordered
            listOf(member("x", "2"), member("y", null), member("z", "2")).inSeriesOrder().map { it.uuid } shouldContainExactly
                listOf("x", "z", "y")
        }

        it("#104 TC-8: an empty or sparse body is a success") {
            route(mapOf(SERIES_4 to { json(200, """{"books":[]}""") }))
            repository.getSeries(4) shouldBe SeriesResult.Success(emptyList())

            route(mapOf(SERIES_4 to { json(200, "{}") }))
            repository.getSeries(4) shouldBe SeriesResult.Success(emptyList())

            route(
                mapOf(
                    SERIES_4 to {
                        json(
                            200,
                            """
                            {"id":4,"name":"Dune","sort":"dune","reading":{"x":1},"hidden_from_home":false,
                             "books":[{"id":1,"unique_identifier":"u1","filename":"f.epub","title":"T",
                               "accent":"#unknown-token","future":[1,2]}]}
                            """.trimIndent(),
                        )
                    },
                ),
            )
            repository.getSeries(4) shouldBe SeriesResult.Success(
                listOf(SeriesMember("u1", "T", null, null, "#unknown-token", hasCover = false)),
            )
        }

        it("#104 TC-9: it requests GET api/series/{id} with one encoded path segment") {
            route(
                mapOf(
                    SERIES_4 to { json(200, "{}") },
                    SERIES_BIG to { json(200, "{}") },
                ),
            )

            repository.getSeries(4)
            repository.getSeries(123456789012)

            requests.map { it.method }.toSet() shouldBe setOf("GET")
            requests.map { it.url.encodedPath } shouldContainExactly listOf(SERIES_4, SERIES_BIG)
            requests.forEach {
                it.url.query shouldBe null
                it.headers["Authorization"] shouldBe "Bearer secret"
            }
        }

        it("#104 TC-10: 401 is SessionExpired") {
            route(mapOf(SERIES_4 to { json(401, "") }))

            repository.getSeries(4) shouldBe SeriesResult.Failure(SeriesError.SessionExpired)
        }

        it("#104 TC-11: 404 is NotFound") {
            route(mapOf(SERIES_4 to { json(404, "") }))

            repository.getSeries(4) shouldBe SeriesResult.Failure(SeriesError.NotFound)
        }

        listOf(500, 503, 403).forEach { code ->
            it("#104 TC-12: HTTP $code is Http($code)") {
                route(mapOf(SERIES_4 to { json(code, "boom") }))

                repository.getSeries(4) shouldBe SeriesResult.Failure(SeriesError.Http(code))
            }
        }

        it("#104 TC-13: an IO error is Unreachable") {
            server.close()

            repository.getSeries(4) shouldBe SeriesResult.Failure(SeriesError.Unreachable)
        }

        listOf(
            "invalid JSON" to "not json",
            "a top-level array" to "[]",
            "an empty body" to "",
            "books of the wrong type" to """{"books":"nope"}""",
        ).forEach { (case, body) ->
            it("#104 TC-14: a 200 with $case is UnexpectedResponse") {
                route(mapOf(SERIES_4 to { json(200, body) }))

                repository.getSeries(4) shouldBe SeriesResult.Failure(SeriesError.UnexpectedResponse)
            }
        }

        it("#104 TC-15: with no saved server it gives NoServer and sends nothing") {
            baseUrlProvider.baseUrl = null

            repository.getSeries(4) shouldBe SeriesResult.NoServer

            server.requestCount shouldBe 0
        }

        it("#104 TC-16: cancelling an in-flight read throws CancellationException instead of a result") {
            respond = { MockResponse.Builder().code(200).headersDelay(500, TimeUnit.MILLISECONDS).body("{}").build() }
            val dispatched = newRepository(Dispatchers.IO)

            coroutineScope {
                val pending = async(Dispatchers.IO) { dispatched.getSeries(4) }
                delay(100)
                pending.cancel()
                shouldThrow<CancellationException> { pending.await() }
            }
        }

        it("#104 TC-17: the request and decode run on the injected IO dispatcher") {
            route(mapOf(SERIES_4 to { json(200, seriesJson(bookJson(id = 1, seriesIndex = "1"))) }))
            val recording = RecordingDispatcher()

            val result = newRepository(recording).getSeries(4)

            result.shouldBeTypeOf<SeriesResult.Success>()
            recording.dispatches shouldBeGreaterThanOrEqual 1
        }
    }
}) {
    private companion object {
        const val SERIES_4 = "/api/series/4"
        const val SERIES_BIG = "/api/series/123456789012"

        fun quoted(value: String?): String = value?.let { "\"$it\"" } ?: "null"

        fun member(uuid: String, index: String?) = SeriesMember(uuid, "Title $uuid", index, null, null, false)
    }
}

/** Counts the dispatches it receives and runs each block on [Dispatchers.IO]. */
private class RecordingDispatcher : CoroutineDispatcher() {
    var dispatches = 0

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatches++
        Dispatchers.IO.dispatch(context, block)
    }
}
