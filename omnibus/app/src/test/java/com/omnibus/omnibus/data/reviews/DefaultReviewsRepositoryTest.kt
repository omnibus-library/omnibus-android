package com.omnibus.omnibus.data.reviews

import app.cash.turbine.test
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A connection dropped before any response, which the client reports as an IOException. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** A JSON object from pairs, where null is JSON null and a Kotlin value is wrapped as a primitive. */
private fun obj(vararg fields: Pair<String, Any?>): JsonObject = JsonObject(
    fields.associate { (key, value) ->
        key to when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> error("Unsupported JSON value: $value")
        }
    },
)

/** A random review of the signed-in reader, who is not attributed, with a rating and a known change time. */
private fun randomMine(): Review = randomReview().copy(
    userId = 0L,
    displayName = "",
    hasAvatar = false,
    rating = randomRating(),
    updatedAtMillis = randomLong(0L..2_000_000_000L) * 1_000L,
)

/** A rating that differs from [rating]. */
private fun otherRating(rating: Float?): Float = randomOf((1..10).map { it / 2f }.filter { it != rating })

/** Two different ratings, neither of them [rating], for two writes that each change something. */
private fun twoOtherRatings(rating: Float?): Pair<Float, Float> {
    val older = otherRating(rating)
    return older to randomOf((1..10).map { it / 2f }.filter { it != rating && it != older })
}

/** This review of the signed-in reader as the server writes it. */
private fun Review.toJson(): JsonObject = obj(
    "stars" to rating,
    "body_md" to text,
    "notes_md" to privateNotes,
    "spoiler" to isSpoiler,
    "updated_at" to updatedAtMillis?.div(1_000L),
)

/** The body of `GET api/reviews/{uuid}` with the given parts. */
private fun reviewsBody(mine: Review? = null, others: List<JsonObject> = emptyList(), summary: JsonObject? = null): String =
    obj("mine" to mine?.toJson(), "others" to JsonArray(others), "summary" to summary).toString()

private fun loaded(mine: Review? = null, others: List<Review> = emptyList(), summary: ReviewSummary? = null) =
    ReviewsState.Loaded(BookReviews(mine, others, summary))

/** The state's reviews, or a failed assertion when it is not Loaded. */
private fun reviewsOf(state: ReviewsState): BookReviews = (state as ReviewsState.Loaded).reviews

private fun ratingPatch(rating: Float?): ReviewPatch = ReviewPatch(rating = Patch.Set(rating))

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

/** Waits until the shared state for [uuid] is [expected]. */
private suspend fun ReviewsRepository.awaitState(uuid: String, expected: ReviewsState) {
    withTimeout(5_000) { getReviewsFlow(uuid).first { it == expected } }
}

class DefaultReviewsRepositoryTest : DescribeSpec({

    // By default everything runs unconfined on the calling thread, so the state has settled when a call returns.
    fun sut(
        baseUrl: HttpUrl? = null,
        monitor: ConnectivityMonitor = TestConnectivityMonitor(),
        applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        baseUrlProvider: TestServerBaseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
    ): DefaultReviewsRepository = DefaultReviewsRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = baseUrlProvider,
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        connectivityMonitor = monitor,
        applicationScope = applicationScope,
    )

    /** A [sut] that runs HTTP on real threads, which lets a test hold a request open. */
    fun TestServer.threadedSut(monitor: ConnectivityMonitor = TestConnectivityMonitor()): DefaultReviewsRepository =
        sut(url, monitor, scope, Dispatchers.IO)

    /** Loads [body] for [uuid], and takes that request from the server's queue. */
    suspend fun TestServer.load(repository: ReviewsRepository, uuid: String, body: String) {
        script.answer("GET", reply(200, body))
        repository.refresh(uuid)
        nextRequest()
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading a book's reviews") {

        context("when nothing has loaded") {

            it("TC-1: emits Loading, then the server's reviews") {
                withServer {
                    val mine = randomMine()
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, reviewsBody(mine)), gate)

                    threadedSut().getReviewsFlow(randomString()).test {
                        awaitItem() shouldBe ReviewsState.Loading
                        gate.countDown()
                        awaitItem() shouldBe loaded(mine)
                    }
                }
            }
        }

        context("when the reviews have loaded") {

            it("TC-1: reads nothing more from the server for a new collector") {
                withServer {
                    val uuid = randomString()
                    val repository = sut(url)
                    load(repository, uuid, reviewsBody())

                    repository.getReviewsFlow(uuid).first()

                    server.requestCount shouldBe 1
                }
            }

            context("when a refresh fails") {

                it("TC-4: keeps them, regardless of how it fails") {
                    withServer {
                        val uuid = randomString()
                        val mine = randomMine()
                        val repository = sut(url, TestConnectivityMonitor(online = false))
                        load(repository, uuid, reviewsBody(mine))

                        checkAll(Exhaustive.of(reply(500), dropped())) { failure ->
                            script.answer("GET", failure)

                            repository.refresh(uuid)

                            repository.getReviewsFlow(uuid).first() shouldBe loaded(mine)
                        }
                    }
                }
            }
        }

        context("when a read is in flight") {

            it("TC-2: shares it with another caller") {
                withServer {
                    val uuid = randomString()
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(200, reviewsBody()), gate)
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

        it("TC-3: asks for the book with its uuid as one path segment") {
            withServer {
                val uuid = "a b/c"
                script.answer("GET", reply(200, reviewsBody()))

                sut(url).refresh(uuid)

                nextRequest().url.pathSegments shouldContainExactly listOf("api", "reviews", uuid)
            }
        }

        it("TC-5: keeps others in the server's order, without rows that have no user id") {
            withServer {
                val uuid = randomString()
                val repository = sut(url)
                val rows = listOf(
                    obj("user_id" to 9L, "unknown_key" to obj("nested" to true)),
                    obj("user_id" to null),
                    obj("user_id" to 2L),
                    obj("user_id" to 5L),
                )
                load(repository, uuid, reviewsBody(others = rows))

                reviewsOf(repository.getReviewsFlow(uuid).first()).others.map { it.userId } shouldBe listOf(9L, 2L, 5L)
            }
        }

        it("TC-6: maps my review with no attribution") {
            withServer {
                val uuid = randomString()
                val mine = randomMine()
                val repository = sut(url)
                load(repository, uuid, reviewsBody(mine))

                reviewsOf(repository.getReviewsFlow(uuid).first()).mine shouldBe mine
            }
        }

        it("TC-6: maps another reader's review without private notes, trimming the name") {
            withServer {
                val uuid = randomString()
                val name = randomString()
                val repository = sut(url)
                val row = obj("user_id" to randomLong(), "username" to "  $name ", "notes_md" to randomString())
                load(repository, uuid, reviewsBody(others = listOf(row)))

                val other = reviewsOf(repository.getReviewsFlow(uuid).first()).others.single()
                other.displayName shouldBe name
                other.privateNotes.shouldBeNull()
            }
        }

        it("TC-7: keeps a rating only inside 0.5 to 5") {
            withServer {
                val uuid = randomString()
                val repository = sut(url)
                val rows = listOf(0.5, 5, 0, 5.5).map { obj("user_id" to randomLong(), "stars" to it) } + obj("user_id" to randomLong())
                load(repository, uuid, reviewsBody(others = rows))

                reviewsOf(repository.getReviewsFlow(uuid).first()).others.map { it.rating } shouldBe
                    listOf(0.5f, 5f, null, null, null)
            }
        }

        it("TC-7: reads blank text as none, regardless of the blank") {
            withServer {
                val uuid = randomString()
                val text = randomString()
                val repository = sut(url)
                val rows = listOf("", "  ", text).map { obj("user_id" to randomLong(), "body_md" to it) }
                load(repository, uuid, reviewsBody(others = rows))

                reviewsOf(repository.getReviewsFlow(uuid).first()).others.map { it.text } shouldBe listOf(null, null, text)
            }
        }

        it("TC-8: prefers the seconds for the change time, then the ISO string, then none") {
            withServer {
                val uuid = randomString()
                val seconds = randomLong(0L..2_000_000_000L)
                val iso = "2025-10-29T21:24:05Z"
                val repository = sut(url)
                val rows = listOf(
                    obj("user_id" to randomLong(), "updated_at" to seconds, "updated_at_iso" to iso),
                    obj("user_id" to randomLong(), "updated_at_iso" to iso),
                    obj("user_id" to randomLong()),
                )
                load(repository, uuid, reviewsBody(others = rows))

                reviewsOf(repository.getReviewsFlow(uuid).first()).others.map { it.updatedAtMillis } shouldBe
                    listOf(seconds * 1_000L, BookDetailFormat.parseServerTimestamp(iso).shouldNotBeNull(), null)
            }
        }

        it("TC-9: has a summary only with at least one rating") {
            withServer {
                checkAll(
                    Exhaustive.of(
                        obj("average_stars" to 4.5, "ratings" to 3) to ReviewSummary(4.5f, 3),
                        obj("average_stars" to 4.5, "ratings" to 1) to ReviewSummary(4.5f, 1),
                        obj("average_stars" to 4.5, "ratings" to 0) to null,
                        null to null,
                    ),
                ) { (summary, expected) ->
                    val uuid = randomString()
                    val repository = sut(url)
                    load(repository, uuid, reviewsBody(summary = summary))

                    reviewsOf(repository.getReviewsFlow(uuid).first()).summary shouldBe expected
                }
            }
        }

        context("when the connection drops") {

            it("TC-10: is Offline or Failed according to whether the device is online") {
                withServer {
                    checkAll(Exhaustive.of(false to ReviewsState.Offline, true to ReviewsState.Failed)) { (online, state) ->
                        script.answer("GET", dropped())

                        sut(url, TestConnectivityMonitor(online = online)).getReviewsFlow(randomString()).first() shouldBe state
                    }
                }
            }
        }

        it("TC-11: is Failed regardless of what else goes wrong") {
            withServer {
                checkAll(Exhaustive.of(reply(401), reply(404), reply(500), reply(200, "not json"))) { failure ->
                    script.answer("GET", failure)

                    sut(url).getReviewsFlow(randomString()).first() shouldBe ReviewsState.Failed
                }
            }
        }

        context("when no server is saved") {

            it("TC-11: is Failed") {
                sut().getReviewsFlow(randomString()).first() shouldBe ReviewsState.Failed
            }
        }

        context("when the uuid is blank") {

            it("TC-11: fails without asking the server") {
                withServer {
                    sut(url).getReviewsFlow("  ").first() shouldBe ReviewsState.Failed

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when the last read failed") {

            it("TC-12: shows Loading again during the next read") {
                withServer {
                    val uuid = randomString()
                    val gate = CountDownLatch(1)
                    val repository = threadedSut()
                    script.answer("GET", reply(500))
                    repository.refresh(uuid)
                    script.answer("GET", reply(200, reviewsBody()), gate)

                    repository.getReviewsFlow(uuid).test {
                        // The collection itself starts the read, so Loading may replace Failed before the first item.
                        awaitItem() shouldBe ReviewsState.Loading
                        gate.countDown()
                        awaitItem() shouldBe loaded()
                    }
                }
            }
        }
    }

    describe("coming back online") {

        context("when the state is Offline") {

            it("TC-13: reads again") {
                withServer {
                    val uuid = randomString()
                    val mine = randomMine()
                    val monitor = TestConnectivityMonitor(online = false)
                    val repository = sut(url, monitor)
                    script.answer("GET", dropped())

                    repository.getReviewsFlow(uuid).test {
                        awaitItem() shouldBe ReviewsState.Offline

                        script.answer("GET", reply(200, reviewsBody(mine)))
                        monitor.isOnline.value = true

                        expectMostRecentItem() shouldBe loaded(mine)
                    }
                }
            }

            context("when nothing is collecting the state") {

                it("TC-15: reads nothing") {
                    withServer {
                        val uuid = randomString()
                        val monitor = TestConnectivityMonitor(online = false)
                        val repository = sut(url, monitor)
                        script.answer("GET", dropped())
                        repository.getReviewsFlow(uuid).first()
                        val requests = server.requestCount

                        monitor.isOnline.value = true

                        server.requestCount shouldBe requests
                    }
                }
            }
        }

        it("TC-13, TC-15: reads nothing regardless of whether the state is Failed or Loaded") {
            withServer {
                checkAll(
                    Exhaustive.of(
                        reply(500) to ReviewsState.Failed,
                        reply(200, reviewsBody()) to loaded(),
                    ),
                ) { (answer, state) ->
                    val uuid = randomString()
                    val monitor = TestConnectivityMonitor(online = false)
                    val repository = sut(url, monitor)
                    script.answer("GET", answer)

                    repository.getReviewsFlow(uuid).test {
                        awaitItem() shouldBe state
                        val requests = server.requestCount

                        monitor.isOnline.value = true

                        server.requestCount shouldBe requests
                    }
                }
            }
        }
    }

    describe("writing my review") {

        it("TC-14: sends the patch to the book's path") {
            withServer {
                val uuid = randomString()
                val rating = randomRating()
                val repository = sut(url)
                load(repository, uuid, reviewsBody())
                script.answer("PATCH", reply(200, randomMine().toJson().toString()))

                repository.updateMyReview(uuid, ratingPatch(rating))

                val request = nextRequest()
                request.method shouldBe "PATCH"
                request.url.pathSegments shouldContainExactly listOf("api", "reviews", uuid)
                request.body!!.utf8() shouldBe """{"stars":${JsonPrimitive(rating)}}"""
            }
        }

        context("when the patch changes nothing") {

            it("TC-17: answers Unchanged without asking the server") {
                withServer {
                    val uuid = randomString()
                    val mine = randomMine()
                    val repository = sut(url)
                    load(repository, uuid, reviewsBody(mine))

                    repository.updateMyReview(uuid, ratingPatch(mine.rating)) shouldBe ReviewWriteResult.Unchanged

                    server.requestCount shouldBe 1
                }
            }
        }

        context("when the server is still answering") {

            it("TC-18: shows the patched review, then the server's") {
                withServer {
                    val uuid = randomString()
                    val mine = randomMine()
                    val rating = otherRating(mine.rating)
                    val saved = randomMine()
                    val gate = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, reviewsBody(mine))
                    script.answer("PATCH", reply(200, saved.toJson().toString()), gate)
                    script.answer("GET", reply(200, reviewsBody(saved)))

                    val write = scope.async { repository.updateMyReview(uuid, ratingPatch(rating)) }

                    repository.awaitState(uuid, loaded(mine.copy(rating = rating)))
                    gate.countDown()
                    write.await() shouldBe ReviewWriteResult.Saved(saved)
                    repository.awaitState(uuid, loaded(saved))
                }
            }
        }

        context("when the patch leaves nothing") {

            it("TC-19: reads the server's JSON null as no review") {
                withServer {
                    val uuid = randomString()
                    val repository = sut(url)
                    load(repository, uuid, reviewsBody(randomMine().copy(text = null, privateNotes = null)))
                    script.answer("PATCH", reply(200, "null"))

                    repository.updateMyReview(uuid, ratingPatch(null)) shouldBe ReviewWriteResult.Saved(null)
                }
            }
        }

        context("when a save succeeds") {

            it("TC-21: reads the household's part again, keeping my saved review") {
                withServer {
                    val uuid = randomString()
                    val mine = randomMine()
                    val saved = randomMine()
                    val other = obj("user_id" to 2L, "username" to "Amy")
                    val repository = sut(url)
                    load(repository, uuid, reviewsBody(mine))
                    script.answer("PATCH", reply(200, saved.toJson().toString()))
                    script.answer("GET", reply(200, reviewsBody(saved, listOf(other), obj("average_stars" to 3.5, "ratings" to 2))))

                    repository.updateMyReview(uuid, ratingPatch(otherRating(mine.rating)))

                    val state = reviewsOf(repository.getReviewsFlow(uuid).first())
                    state.mine shouldBe saved
                    state.others.map { it.userId } shouldBe listOf(2L)
                    state.summary shouldBe ReviewSummary(3.5f, 2)
                }
            }

            context("when a read started after the write") {

                it("TC-21: joins that read instead of starting another") {
                    withServer {
                        val uuid = randomString()
                        val saved = randomMine()
                        val patchGate = CountDownLatch(1)
                        val getGate = CountDownLatch(1)
                        val repository = threadedSut()
                        load(repository, uuid, reviewsBody())
                        script.answer("PATCH", reply(200, saved.toJson().toString()), patchGate)
                        script.answer("GET", reply(200, reviewsBody(saved)), getGate)

                        val write = scope.async { repository.updateMyReview(uuid, ratingPatch(randomRating())) }
                        nextRequest().method shouldBe "PATCH"
                        val refresh = scope.async { repository.refresh(uuid) }
                        nextRequest().method shouldBe "GET"
                        patchGate.countDown()
                        write.await()
                        getGate.countDown()
                        refresh.await()

                        server.requestCount shouldBe 3
                    }
                }
            }

            context("when the read that follows it fails") {

                it("TC-22: keeps my saved review, regardless of how the read fails") {
                    withServer {
                        checkAll(Exhaustive.of(reply(500), dropped())) { failure ->
                            val uuid = randomString()
                            val saved = randomMine()
                            val repository = sut(url)
                            load(repository, uuid, reviewsBody())
                            script.answer("PATCH", reply(200, saved.toJson().toString()))
                            script.answer("GET", failure)

                            repository.updateMyReview(uuid, ratingPatch(randomRating()))

                            reviewsOf(repository.getReviewsFlow(uuid).first()).mine shouldBe saved
                        }
                    }
                }
            }
        }

        context("when the write fails") {

            it("TC-23: reports each failure as its own error") {
                withServer {
                    checkAll(
                        Exhaustive.of(
                            dropped() to ReviewError.Unreachable,
                            reply(400) to ReviewError.Http(400),
                            reply(401) to ReviewError.SessionExpired,
                            reply(404) to ReviewError.BookNotFound,
                            reply(500) to ReviewError.Http(500),
                            reply(200, "garbage") to ReviewError.UnexpectedResponse,
                        ),
                    ) { (failure, error) ->
                        val uuid = randomString()
                        val repository = sut(url)
                        load(repository, uuid, reviewsBody())
                        script.answer("PATCH", failure)

                        repository.updateMyReview(uuid, ratingPatch(randomRating())) shouldBe ReviewWriteResult.Failed(error)
                    }
                }
            }

            it("TC-24: shows the patched review, then restores the exact previous state") {
                withServer {
                    val uuid = randomString()
                    val mine = randomMine()
                    val rating = otherRating(mine.rating)
                    val others = listOf(obj("user_id" to 2L))
                    val summary = obj("average_stars" to 3.5, "ratings" to 2)
                    val repository = sut(url)
                    load(repository, uuid, reviewsBody(mine, others, summary))
                    script.answer("PATCH", reply(500))

                    repository.getReviewsFlow(uuid).test {
                        val previous = reviewsOf(awaitItem())

                        repository.updateMyReview(uuid, ratingPatch(rating))

                        awaitItem() shouldBe ReviewsState.Loaded(previous.copy(mine = mine.copy(rating = rating)))
                        awaitItem() shouldBe ReviewsState.Loaded(previous)
                    }
                }
            }
        }

        context("when the saved server has been forgotten") {

            it("TC-23: fails with NoServer regardless of the write") {
                withServer {
                    val uuid = randomString()
                    val provider = TestServerBaseUrlProvider(baseUrl = url)
                    val repository = sut(baseUrlProvider = provider)
                    load(repository, uuid, reviewsBody())
                    provider.baseUrl = null

                    repository.updateMyReview(uuid, ratingPatch(randomRating())) shouldBe
                        ReviewWriteResult.Failed(ReviewError.NoServer)
                    repository.deleteMyReview(uuid) shouldBe ReviewWriteResult.Failed(ReviewError.NoServer)
                }
            }
        }

        context("when the uuid is blank") {

            it("TC-23: fails with BookNotFound regardless of the write") {
                sut().updateMyReview(" ", ratingPatch(randomRating())) shouldBe ReviewWriteResult.Failed(ReviewError.BookNotFound)
                sut().deleteMyReview("") shouldBe ReviewWriteResult.Failed(ReviewError.BookNotFound)
            }
        }

        context("when the reviews have not loaded") {

            it("TC-29: fails with NotLoaded regardless of the reason or the write") {
                withServer {
                    checkAll(
                        Exhaustive.of(
                            null to true,
                            reply(500) to true,
                            dropped() to false,
                        ),
                    ) { (firstRead, online) ->
                        val uuid = randomString()
                        val repository = sut(url, TestConnectivityMonitor(online = online))
                        if (firstRead != null) {
                            script.answer("GET", firstRead)
                            repository.refresh(uuid)
                        }
                        val requests = server.requestCount

                        repository.updateMyReview(uuid, ratingPatch(randomRating())) shouldBe
                            ReviewWriteResult.Failed(ReviewError.NotLoaded)
                        repository.deleteMyReview(uuid) shouldBe ReviewWriteResult.Failed(ReviewError.NotLoaded)
                        server.requestCount shouldBe requests
                    }
                }
            }
        }

        context("when a newer write is pending") {

            context("when the older write succeeds") {

                it("TC-25: keeps the newer write's value") {
                    withServer {
                        val uuid = randomString()
                        val mine = randomMine()
                        val (older, newer) = twoOtherRatings(mine.rating)
                        val optimistic = loaded(mine.copy(rating = newer))
                        val gateA = CountDownLatch(1)
                        val gateB = CountDownLatch(1)
                        val repository = threadedSut()
                        load(repository, uuid, reviewsBody(mine))
                        script.answer("PATCH", reply(200, randomMine().toJson().toString()), gateA)
                        script.answer("PATCH", reply(200, randomMine().toJson().toString()), gateB)

                        val a = scope.async { repository.updateMyReview(uuid, ratingPatch(older)) }
                        nextRequest()
                        val b = scope.async { repository.updateMyReview(uuid, ratingPatch(newer)) }
                        repository.awaitState(uuid, optimistic)
                        gateA.countDown()
                        a.await()

                        repository.getReviewsFlow(uuid).first() shouldBe optimistic
                        gateB.countDown()
                        b.await()
                    }
                }
            }

            context("when the older write fails") {

                it("TC-26: keeps the newer write's value") {
                    withServer {
                        val uuid = randomString()
                        val mine = randomMine()
                        val (older, newer) = twoOtherRatings(mine.rating)
                        val optimistic = loaded(mine.copy(rating = newer))
                        val gateA = CountDownLatch(1)
                        val gateB = CountDownLatch(1)
                        val repository = threadedSut()
                        load(repository, uuid, reviewsBody(mine))
                        script.answer("PATCH", reply(500), gateA)
                        script.answer("PATCH", reply(500), gateB)

                        val a = scope.async { repository.updateMyReview(uuid, ratingPatch(older)) }
                        nextRequest()
                        val b = scope.async { repository.updateMyReview(uuid, ratingPatch(newer)) }
                        repository.awaitState(uuid, optimistic)
                        gateA.countDown()
                        a.await()

                        repository.getReviewsFlow(uuid).first() shouldBe optimistic
                        gateB.countDown()
                        b.await()
                    }
                }
            }

            it("TC-27: holds the newer write back until the older one is answered") {
                withServer {
                    val uuid = randomString()
                    val mine = randomMine()
                    val (older, newer) = twoOtherRatings(mine.rating)
                    val gate = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, reviewsBody(mine))
                    script.answer("PATCH", reply(200, randomMine().toJson().toString()), gate)
                    script.answer("PATCH", reply(200, randomMine().toJson().toString()))

                    val a = scope.async { repository.updateMyReview(uuid, ratingPatch(older)) }
                    nextRequest()
                    val b = scope.async { repository.updateMyReview(uuid, ratingPatch(newer)) }

                    // There is no event for "a request was not sent", so this waits a bounded time for one to arrive.
                    server.takeRequest(300, TimeUnit.MILLISECONDS).shouldBeNull()
                    gate.countDown()
                    listOf(a, b).awaitAll()
                }
            }
        }

        context("when the caller is cancelled") {

            it("TC-28: lets the write finish") {
                withServer {
                    val uuid = randomString()
                    val saved = randomMine()
                    val gate = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, reviewsBody())
                    script.answer("PATCH", reply(200, saved.toJson().toString()), gate)
                    script.answer("GET", reply(200, reviewsBody(saved)))

                    val caller = scope.launch { repository.updateMyReview(uuid, ratingPatch(randomRating())) }
                    nextRequest()
                    caller.cancelAndJoin()
                    gate.countDown()

                    repository.awaitState(uuid, loaded(saved))
                }
            }
        }

        context("when a read started before the save") {

            it("TC-30: keeps my saved review") {
                withServer {
                    val uuid = randomString()
                    val saved = randomMine()
                    val staleGate = CountDownLatch(1)
                    val nextGate = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, reviewsBody())
                    script.answer("GET", reply(200, reviewsBody(randomMine())), staleGate)
                    script.answer("PATCH", reply(200, saved.toJson().toString()))
                    script.answer("GET", reply(200, reviewsBody(saved)), nextGate)

                    val refresh = scope.async { repository.refresh(uuid) }
                    nextRequest().method shouldBe "GET"
                    repository.updateMyReview(uuid, ratingPatch(randomRating()))
                    staleGate.countDown()
                    refresh.await()

                    reviewsOf(repository.getReviewsFlow(uuid).first()).mine shouldBe saved
                    nextGate.countDown()
                }
            }

            it("TC-30: reads once more for the household's part") {
                withServer {
                    val uuid = randomString()
                    val saved = randomMine()
                    val staleGate = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, reviewsBody())
                    script.answer("GET", reply(200, reviewsBody()), staleGate)
                    script.answer("PATCH", reply(200, saved.toJson().toString()))
                    script.answer("GET", reply(200, reviewsBody(saved, listOf(obj("user_id" to 2L)))))

                    val refresh = scope.async { repository.refresh(uuid) }
                    nextRequest().method shouldBe "GET"
                    repository.updateMyReview(uuid, ratingPatch(randomRating()))
                    staleGate.countDown()
                    refresh.await()

                    withTimeout(5_000) {
                        repository.getReviewsFlow(uuid).first { reviewsOf(it).others.isNotEmpty() }
                    }
                }
            }
        }

        context("when a read lands during the write") {

            it("TC-31: keeps showing the write's value") {
                withServer {
                    val uuid = randomString()
                    val mine = randomMine()
                    val rating = otherRating(mine.rating)
                    val getGate = CountDownLatch(1)
                    val patchGate = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, reviewsBody(mine))
                    script.answer("GET", reply(200, reviewsBody(randomMine())), getGate)
                    script.answer("PATCH", reply(500), patchGate)

                    val refresh = scope.async { repository.refresh(uuid) }
                    nextRequest().method shouldBe "GET"
                    val write = scope.async { repository.updateMyReview(uuid, ratingPatch(rating)) }
                    nextRequest().method shouldBe "PATCH"
                    getGate.countDown()
                    refresh.await()

                    reviewsOf(repository.getReviewsFlow(uuid).first()).mine shouldBe mine.copy(rating = rating)
                    patchGate.countDown()
                    write.await()
                }
            }

            it("TC-31: falls back to the read's answer once the write fails") {
                withServer {
                    val uuid = randomString()
                    val read = randomMine()
                    val getGate = CountDownLatch(1)
                    val patchGate = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, reviewsBody())
                    script.answer("GET", reply(200, reviewsBody(read)), getGate)
                    script.answer("PATCH", reply(500), patchGate)

                    val refresh = scope.async { repository.refresh(uuid) }
                    nextRequest().method shouldBe "GET"
                    val write = scope.async { repository.updateMyReview(uuid, ratingPatch(randomRating())) }
                    nextRequest().method shouldBe "PATCH"
                    getGate.countDown()
                    refresh.await()
                    patchGate.countDown()
                    write.await()

                    repository.getReviewsFlow(uuid).first() shouldBe loaded(read)
                }
            }
        }

        context("when another book has loaded") {

            it("TC-32: leaves that book's state alone") {
                withServer {
                    val uuid = randomString()
                    val other = randomString()
                    val otherMine = randomMine()
                    val repository = sut(url)
                    load(repository, other, reviewsBody(otherMine))
                    load(repository, uuid, reviewsBody())
                    script.answer("PATCH", reply(200, randomMine().toJson().toString()))

                    repository.updateMyReview(uuid, ratingPatch(randomRating()))

                    repository.getReviewsFlow(other).first() shouldBe loaded(otherMine)
                }
            }
        }
    }

    describe("deleting my review") {

        it("TC-20: deletes through the book's path") {
            withServer {
                val uuid = randomString()
                val repository = sut(url)
                load(repository, uuid, reviewsBody(randomMine()))
                script.answer("DELETE", reply(204))

                repository.deleteMyReview(uuid) shouldBe ReviewWriteResult.Saved(null)

                val request = nextRequest()
                request.method shouldBe "DELETE"
                request.url.pathSegments shouldContainExactly listOf("api", "reviews", uuid)
            }
        }

        context("when the server is still answering") {

            it("TC-20: shows no review") {
                withServer {
                    val uuid = randomString()
                    val gate = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, reviewsBody(randomMine()))
                    script.answer("DELETE", reply(204), gate)
                    script.answer("GET", reply(200, reviewsBody()))

                    val write = scope.async { repository.deleteMyReview(uuid) }

                    repository.awaitState(uuid, loaded())
                    gate.countDown()
                    write.await()
                }
            }
        }

        context("when the delete fails") {

            it("TC-23: restores the review") {
                withServer {
                    val uuid = randomString()
                    val mine = randomMine()
                    val repository = sut(url)
                    load(repository, uuid, reviewsBody(mine))
                    script.answer("DELETE", reply(500))

                    repository.deleteMyReview(uuid)

                    repository.getReviewsFlow(uuid).first() shouldBe loaded(mine)
                }
            }
        }
    }
})
