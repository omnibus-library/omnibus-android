package com.omnibus.omnibus.data.wishlist

import app.cash.turbine.test
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

/** This entry as the server writes it, with its time in seconds. */
private fun WishlistEntry.toBody(): String = listOfNotNull(
    "\"id\":$id",
    addedAtMillis?.let { "\"added_at\":${it / 1_000L}" },
    source?.let { "\"source\":\"${it.name.lowercase()}\"" },
).joinToString(prefix = "{", postfix = "}", separator = ",")

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

private fun reply(entry: WishlistEntry): MockResponse = reply(200, entry.toBody())

/** A successful removal's answer. */
private fun removed(): MockResponse = reply(200, """{"book_deleted":${randomBoolean()}}""")

/** A connection dropped before any response, which the client reports as an IOException. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/**
 * Answers requests from a script, per HTTP method, in order. A step can hold its response until the test releases
 * [CountDownLatch]. A request with no step gets a 500, so an unexpected call fails the test instead of hanging.
 */
private class ScriptedDispatcher : Dispatcher() {
    private class Step(val response: MockResponse, val gate: CountDownLatch?)

    private val steps = mutableMapOf<String, ArrayDeque<Step>>()

    fun answer(method: String, response: MockResponse, gate: CountDownLatch? = null) {
        synchronized(steps) { steps.getOrPut(method) { ArrayDeque() }.addLast(Step(response, gate)) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val method = request.method.orEmpty()
        val step = synchronized(steps) { steps[method]?.removeFirstOrNull() }
            ?: return reply(500, "unscripted $method")
        step.gate?.await(5, TimeUnit.SECONDS)
        return step.response
    }
}

/** A running server that answers from [script], and [scope], a place for work that outlives one call. */
private class TestServer(val server: MockWebServer, val script: ScriptedDispatcher, val scope: CoroutineScope) {

    val url: HttpUrl get() = server.url("/")

    /** Waits for the next request the server received, in arrival order. */
    fun nextRequest(): RecordedRequest =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }
}

/** Runs [test] against a new [TestServer], and closes it afterwards. */
private suspend fun withServer(test: suspend TestServer.() -> Unit) {
    val script = ScriptedDispatcher()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    MockWebServer().use { server ->
        server.dispatcher = script
        server.start()
        try {
            TestServer(server, script, scope).test()
        } finally {
            scope.cancel()
        }
    }
}

class DefaultWishlistRepositoryTest : DescribeSpec({

    // By default everything runs unconfined on the calling thread, so the state has settled when a call returns.
    fun sut(
        baseUrl: HttpUrl? = null,
        applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): DefaultWishlistRepository = DefaultWishlistRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        applicationScope = applicationScope,
    )

    /** A [sut] that runs HTTP on real threads, which lets a test hold a request open. */
    fun TestServer.threadedSut(): DefaultWishlistRepository = sut(url, scope, Dispatchers.IO)

    /** Reads [entry] for [uuid], so the book is on the wishlist, and takes that request from the server's queue. */
    suspend fun TestServer.load(repository: WishlistRepository, uuid: String, entry: WishlistEntry) {
        script.answer("GET", reply(entry))
        repository.refresh(uuid)
        nextRequest()
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading the wishlist entry") {

        context("when nothing is known") {

            it("#119 TC-1: emits Unknown, then the server's answer") {
                withServer {
                    val entry = randomWishlistEntry()
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(entry), gate)

                    threadedSut().getWishlistFlow(randomString()).test {
                        awaitItem() shouldBe WishlistState.Unknown
                        gate.countDown()
                        awaitItem() shouldBe WishlistState.Present(entry)
                    }
                }
            }
        }

        context("when the server's answer is null") {

            it("#119 TC-2: is Absent") {
                withServer {
                    script.answer("GET", reply(200, "null"))

                    sut(url).getWishlistFlow(randomString()).first() shouldBe WishlistState.Absent
                }
            }
        }

        it("#119 TC-3: reads a source token regardless of its case, leaving any other as no source") {
            withServer {
                val entry = randomWishlistEntry()

                checkAll(
                    Exhaustive.of(
                        "\"detail\"" to WishlistSource.Detail,
                        "\"manual\"" to WishlistSource.Manual,
                        "\"search\"" to WishlistSource.Search,
                        "\"scan\"" to WishlistSource.Scan,
                        "\"SCAN\"" to WishlistSource.Scan,
                        "\"hardcover\"" to null,
                    ),
                ) { (token, source) ->
                    val body = entry.copy(source = null).toBody().replaceFirst("{", """{"source":$token,""")
                    script.answer("GET", reply(200, body))

                    sut(url).getWishlistFlow(randomString()).first() shouldBe
                        WishlistState.Present(entry.copy(source = source))
                }
            }
        }

        it("#119 TC-3: maps the time from seconds to milliseconds, leaving a missing one as none") {
            withServer {
                checkAll(
                    Exhaustive.of(
                        randomWishlistEntry().copy(addedAtMillis = randomLong(0L..2_000_000_000L) * 1_000L),
                        randomWishlistEntry().copy(addedAtMillis = null),
                    ),
                ) { entry ->
                    script.answer("GET", reply(entry))

                    sut(url).getWishlistFlow(randomString()).first() shouldBe WishlistState.Present(entry)
                }
            }
        }

        it("#119 TC-4: is Failed regardless of how the read goes wrong") {
            withServer {
                checkAll(
                    Exhaustive.of(
                        reply(401),
                        reply(404),
                        reply(500),
                        dropped(),
                        reply(200, "not json"),
                        reply(200, "[1,2]"),
                    ),
                ) { failure ->
                    script.answer("GET", failure)

                    sut(url).getWishlistFlow(randomString()).first() shouldBe WishlistState.Failed
                }
            }
        }

        context("when no server is saved") {

            it("#119 TC-4: is Failed") {
                sut().getWishlistFlow(randomString()).first() shouldBe WishlistState.Failed
            }
        }

        context("when the uuid is blank") {

            it("#119 TC-5: is Failed without asking the server, regardless of the blank") {
                withServer {
                    checkAll(Exhaustive.of("", "   ")) { uuid ->
                        sut(url).getWishlistFlow(uuid).first() shouldBe WishlistState.Failed

                        server.requestCount shouldBe 0
                    }
                }
            }
        }

        it("#119 TC-6: asks for the book with its uuid as one encoded path segment") {
            withServer {
                script.answer("GET", reply(randomWishlistEntry()))

                sut(url).refresh("book one")

                val request = nextRequest()
                request.method shouldBe "GET"
                request.url.encodedPath shouldBe "/api/physical/book%20one/wishlist"
            }
        }

        context("when a read is in flight") {

            it("#119 TC-7, TC-9: shares it with another caller") {
                withServer {
                    val uuid = randomString()
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(randomWishlistEntry()), gate)
                    val repository = threadedSut()

                    val first = scope.async(start = CoroutineStart.UNDISPATCHED) { repository.refresh(uuid) }
                    nextRequest()
                    val second = scope.async(start = CoroutineStart.UNDISPATCHED) { repository.refresh(uuid) }
                    gate.countDown()
                    listOf(first, second).awaitAll()

                    server.requestCount shouldBe 1
                }
            }
        }

        context("when the entry is known") {

            it("#119 TC-7: reads nothing more from the server for a new collector") {
                withServer {
                    val uuid = randomString()
                    val repository = sut(url)
                    load(repository, uuid, randomWishlistEntry())

                    repository.getWishlistFlow(uuid).first()

                    server.requestCount shouldBe 1
                }
            }

            it("#119 TC-9: takes the new answer on a refresh") {
                withServer {
                    val uuid = randomString()
                    val repository = sut(url)
                    load(repository, uuid, randomWishlistEntry())
                    script.answer("GET", reply(200, "null"))

                    repository.refresh(uuid)

                    repository.getWishlistFlow(uuid).first() shouldBe WishlistState.Absent
                }
            }

            context("when a refresh fails") {

                it("#119 TC-9: keeps it") {
                    withServer {
                        val uuid = randomString()
                        val entry = randomWishlistEntry()
                        val repository = sut(url)
                        load(repository, uuid, entry)
                        script.answer("GET", reply(500))

                        repository.refresh(uuid)

                        repository.getWishlistFlow(uuid).first() shouldBe WishlistState.Present(entry)
                    }
                }
            }
        }

        context("when the last read failed") {

            it("#119 TC-8: reads again for a new collector") {
                withServer {
                    val uuid = randomString()
                    val entry = randomWishlistEntry()
                    val repository = sut(url)
                    script.answer("GET", reply(500))
                    repository.refresh(uuid)
                    script.answer("GET", reply(entry))

                    repository.getWishlistFlow(uuid).first() shouldBe WishlistState.Present(entry)
                }
            }
        }

        context("when the collector is cancelled") {

            it("#119 TC-15: lets the read finish") {
                withServer {
                    val uuid = randomString()
                    val entry = randomWishlistEntry()
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(entry), gate)
                    val repository = threadedSut()

                    val collector = scope.launch { repository.getWishlistFlow(uuid).collect {} }
                    nextRequest()
                    collector.cancelAndJoin()
                    gate.countDown()

                    withTimeout(5_000) { repository.getWishlistFlow(uuid).first { it == WishlistState.Present(entry) } }
                    server.requestCount shouldBe 1
                }
            }
        }
    }

    describe("the state held right now") {

        context("when nothing has been read") {

            it("is Unknown without asking the server") {
                withServer {
                    sut(url).cachedState(randomString()) shouldBe WishlistState.Unknown

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when an entry has been read") {

            it("is that entry") {
                withServer {
                    val uuid = randomString()
                    val entry = randomWishlistEntry()
                    val repository = sut(url)
                    load(repository, uuid, entry)

                    repository.cachedState(uuid) shouldBe WishlistState.Present(entry)
                }
            }
        }
    }

    describe("removing from the wishlist") {

        it("#119 TC-6: sends a DELETE with the uuid as one encoded path segment") {
            withServer {
                script.answer("DELETE", removed())

                sut(url).removeFromWishlist("book one")

                val request = nextRequest()
                request.method shouldBe "DELETE"
                request.url.encodedPath shouldBe "/api/physical/book%20one/wishlist"
            }
        }

        it("#119 TC-10, TC-11: reads whether the book was deleted, defaulting to not") {
            withServer {
                checkAll(
                    Exhaustive.of(
                        """{"book_deleted":true}""" to true,
                        """{"book_deleted":false}""" to false,
                        "{}" to false,
                    ),
                ) { (body, bookDeleted) ->
                    script.answer("DELETE", reply(200, body))

                    sut(url).removeFromWishlist(randomString()) shouldBe WishlistRemovalResult.Removed(bookDeleted)
                }
            }
        }

        context("when the removal succeeds") {

            it("#119 TC-10: moves the state to Absent") {
                withServer {
                    val uuid = randomString()
                    val repository = sut(url)
                    load(repository, uuid, randomWishlistEntry())
                    script.answer("DELETE", removed())

                    repository.removeFromWishlist(uuid)

                    repository.getWishlistFlow(uuid).first() shouldBe WishlistState.Absent
                }
            }
        }

        context("when the removal fails") {

            it("#119 TC-12: reports each failure as its own error") {
                withServer {
                    checkAll(
                        Exhaustive.of(
                            reply(401) to WishlistError.SessionExpired,
                            reply(404) to WishlistError.BookNotFound,
                            reply(500) to WishlistError.Http(500),
                            dropped() to WishlistError.Unreachable,
                            reply(200, "not json") to WishlistError.UnexpectedResponse,
                        ),
                    ) { (failure, error) ->
                        script.answer("DELETE", failure)

                        sut(url).removeFromWishlist(randomString()) shouldBe WishlistRemovalResult.Failed(error)
                    }
                }
            }

            it("#119 TC-12: leaves the entry in place") {
                withServer {
                    val uuid = randomString()
                    val entry = randomWishlistEntry()
                    val repository = sut(url)
                    load(repository, uuid, entry)
                    script.answer("DELETE", reply(500))

                    repository.removeFromWishlist(uuid)

                    repository.getWishlistFlow(uuid).first() shouldBe WishlistState.Present(entry)
                }
            }
        }

        context("when no server is saved") {

            it("#119 TC-12: fails with NoServer") {
                sut().removeFromWishlist(randomString()) shouldBe WishlistRemovalResult.Failed(WishlistError.NoServer)
            }
        }

        context("when the uuid is blank") {

            it("fails with BookNotFound without asking the server") {
                withServer {
                    sut(url).removeFromWishlist(" ") shouldBe WishlistRemovalResult.Failed(WishlistError.BookNotFound)

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when a read started before the removal") {

            it("#119 TC-13: does not bring the entry back") {
                withServer {
                    val uuid = randomString()
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(randomWishlistEntry()), gate)
                    script.answer("DELETE", removed())
                    val repository = threadedSut()

                    val read = scope.async { repository.refresh(uuid) }
                    nextRequest()
                    repository.removeFromWishlist(uuid)
                    gate.countDown()
                    read.await()

                    repository.getWishlistFlow(uuid).first() shouldBe WishlistState.Absent
                }
            }
        }

        context("when the caller is cancelled") {

            it("#119 TC-14: lets the removal finish") {
                withServer {
                    val uuid = randomString()
                    val gate = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, randomWishlistEntry())
                    script.answer("DELETE", removed(), gate)

                    val caller = scope.launch { repository.removeFromWishlist(uuid) }
                    nextRequest()
                    caller.cancelAndJoin()
                    gate.countDown()

                    withTimeout(5_000) { repository.getWishlistFlow(uuid).first { it == WishlistState.Absent } }
                }
            }
        }
    }
})
