package com.omnibus.omnibus.data.reviews

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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

private const val UUID = "book-1"

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

/** The body of `GET api/reviews/{uuid}` with the given parts. */
private fun reviewsBody(mine: JsonObject? = null, others: List<JsonObject> = emptyList(), summary: JsonObject? = null): String =
    obj("mine" to mine, "others" to JsonArray(others), "summary" to summary).toString()

private val EMPTY = ReviewsState.Loaded(BookReviews(mine = null, others = emptyList(), summary = null))

/** The state's reviews, or a failed assertion when it is not Loaded. */
private fun reviewsOf(state: ReviewsState): BookReviews = (state as ReviewsState.Loaded).reviews

/**
 * Answers requests from a script, in order. A step can hold its response until the test releases [CountDownLatch].
 * A request with no step gets a 500, so an unexpected call fails the test instead of hanging.
 */
private class ScriptedDispatcher : Dispatcher() {
    private class Step(val response: MockResponse, val gate: CountDownLatch?)

    private val steps = ArrayDeque<Step>()

    fun answer(response: MockResponse, gate: CountDownLatch? = null) {
        synchronized(steps) { steps.addLast(Step(response, gate)) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val step = synchronized(steps) { steps.removeFirstOrNull() } ?: return reply(500, "unscripted")
        step.gate?.await(5, TimeUnit.SECONDS)
        return step.response
    }
}

/** Collects a book's flow in the background, recording every emission in order. */
private class Observed(repository: ReviewsRepository, uuid: String, scope: CoroutineScope) {
    val states = CopyOnWriteArrayList<ReviewsState>()
    private val job: Job = scope.launch { repository.getReviewsFlow(uuid).collect { states += it } }

    /** Waits until the latest emission is [expected]. */
    suspend fun awaitLast(expected: ReviewsState) {
        withTimeout(5_000) {
            while (states.lastOrNull() != expected) delay(10)
        }
    }

    fun stop() = job.cancel()
}

class DefaultReviewsRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    val scopes = mutableListOf<CoroutineScope>()

    /** A new scope that is cancelled after the test. */
    fun newScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher).also { scopes += it }

    /**
     * A repository for [baseUrl] (null means no server is saved). [held] runs HTTP on real threads, which lets a test
     * hold a request open. Otherwise everything runs unconfined on the calling thread.
     */
    fun repositoryFor(
        baseUrl: HttpUrl? = server.url("/"),
        held: Boolean = false,
        monitor: ConnectivityMonitor = FakeConnectivityMonitor(online = true),
    ): DefaultReviewsRepository {
        val scope = newScope(if (held) Dispatchers.Default else Dispatchers.Unconfined)
        return DefaultReviewsRepository(
            ioDispatcher = if (held) Dispatchers.IO else Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = baseUrl),
                authTokenProvider = FakeAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
            connectivityMonitor = monitor,
            applicationScope = scope,
        )
    }

    /** Waits for the next request the server received, in arrival order. */
    fun nextRequest(): RecordedRequest =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }

    /** Observes [uuid] unconfined, so the state is settled by the time this returns. */
    fun observeNow(repository: ReviewsRepository, uuid: String = UUID): Observed =
        Observed(repository, uuid, newScope(Dispatchers.Unconfined))

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        script = ScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
    }

    afterEach {
        scopes.forEach { it.cancel() }
        scopes.clear()
        server.close()
    }

    describe("the shared state") {

        it("TC-1: a first collection replays Loading, fetches once and settles; a Loaded uuid does not fetch again") {
            val repository = repositoryFor(held = true)
            val gate = CountDownLatch(1)
            script.answer(reply(200, reviewsBody()), gate)
            val first = Observed(repository, UUID, newScope(Dispatchers.Default))

            nextRequest()
            first.awaitLast(ReviewsState.Loading)
            first.states.first() shouldBe ReviewsState.Loading
            gate.countDown()
            first.awaitLast(EMPTY)
            server.requestCount shouldBe 1

            val second = Observed(repository, UUID, newScope(Dispatchers.Default))
            second.awaitLast(EMPTY)
            second.states shouldBe listOf(EMPTY)
            server.requestCount shouldBe 1
        }

        it("TC-2: collectors and a refresh share one in-flight GET, and refresh returns when it settles") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            val gate = CountDownLatch(1)
            script.answer(reply(200, reviewsBody()), gate)
            val first = Observed(repository, UUID, scope)
            nextRequest()
            val second = Observed(repository, UUID, scope)

            val refresh = scope.async { repository.refresh(UUID) }
            delay(200) // give the refresh and the second collector time to join the GET in flight
            gate.countDown()
            refresh.await()

            repository.getReviewsFlow(UUID).first() shouldBe EMPTY
            first.awaitLast(EMPTY)
            second.awaitLast(EMPTY)
            server.requestCount shouldBe 1
        }

        it("TC-3: each uuid has its own encoded path and its own state") {
            val repository = repositoryFor()
            script.answer(reply(200, reviewsBody()))
            script.answer(reply(500))

            repository.refresh("book-1")
            repository.refresh("a b/c")

            val ok = nextRequest()
            ok.method shouldBe "GET"
            ok.url.pathSegments shouldContainExactly listOf("api", "reviews", "book-1")
            ok.headers["Authorization"] shouldBe "Bearer secret"
            nextRequest().url.pathSegments shouldContainExactly listOf("api", "reviews", "a b/c")
            repository.getReviewsFlow("book-1").first() shouldBe EMPTY
            observeNow(repository, "a b/c").states.last() shouldBe ReviewsState.Failed
        }

        it("TC-4: refresh refetches a Loaded uuid, and a failed refetch keeps Loaded") {
            val monitor = FakeConnectivityMonitor(online = false)
            val repository = repositoryFor(monitor = monitor)
            script.answer(reply(200, reviewsBody(others = listOf(obj("user_id" to 9L, "username" to "Zed")))))
            repository.refresh(UUID)
            val observed = observeNow(repository)
            val before = observed.states.last()

            script.answer(reply(200, reviewsBody(others = listOf(obj("user_id" to 2L, "username" to "Amy")))))
            repository.refresh(UUID)
            val changed = observed.states.last()
            script.answer(reply(500))
            repository.refresh(UUID)
            script.answer(dropped())
            repository.refresh(UUID)

            reviewsOf(changed).others.map { it.userId } shouldBe listOf(2L)
            observed.states shouldBe listOf(before, changed)
            repository.getReviewsFlow(UUID).first() shouldBe changed
        }

        it("TC-5: others keep the server order, a row without user_id is dropped, unknown keys are ignored") {
            val repository = repositoryFor()
            val rows = listOf(
                obj("user_id" to 9L, "username" to "Zed", "body_html" to "<p>x</p>", "buckets" to JsonArray(listOf(JsonPrimitive(1)))),
                obj("user_id" to null, "username" to "Nobody"),
                obj("user_id" to 2L, "username" to "Amy", "unknown_key" to obj("nested" to true)),
                obj("user_id" to 5L, "username" to "Bo"),
            )
            script.answer(reply(200, reviewsBody(others = rows)))

            repository.refresh(UUID)

            reviewsOf(repository.getReviewsFlow(UUID).first()).others.map { it.userId } shouldBe listOf(9L, 2L, 5L)
        }

        it("TC-6: mine maps with blank attribution and private notes are mine-only") {
            val repository = repositoryFor()
            val mine = obj("stars" to 4, "body_md" to "Body", "notes_md" to "private", "spoiler" to true)
            val other = obj("user_id" to 3L, "username" to "  Maren ", "notes_md" to "not for you")
            script.answer(reply(200, reviewsBody(mine = mine, others = listOf(other))))
            repository.refresh(UUID)

            val first = reviewsOf(repository.getReviewsFlow(UUID).first())
            first.mine!!.userId shouldBe 0L
            first.mine!!.displayName shouldBe ""
            first.mine!!.hasAvatar shouldBe false
            first.mine!!.rating shouldBe 4f
            first.mine!!.text shouldBe "Body"
            first.mine!!.privateNotes shouldBe "private"
            first.mine!!.isSpoiler shouldBe true
            first.others.single().privateNotes shouldBe null
            first.others.single().displayName shouldBe "Maren"

            script.answer(reply(200, reviewsBody(mine = obj("stars" to 4, "notes_md" to "  "))))
            repository.refresh(UUID)
            reviewsOf(repository.getReviewsFlow(UUID).first()).mine!!.privateNotes shouldBe null
        }

        it("TC-7: stars outside 0.5..5 and blank text map to null") {
            val repository = repositoryFor()
            val rows = listOf(
                obj("user_id" to 1L, "stars" to 0.5, "body_md" to ""),
                obj("user_id" to 2L, "stars" to 5, "body_md" to "  "),
                obj("user_id" to 3L, "stars" to 0, "body_md" to "Great"),
                obj("user_id" to 4L, "stars" to 5.5),
                obj("user_id" to 5L),
            )
            script.answer(reply(200, reviewsBody(others = rows)))
            repository.refresh(UUID)

            val others = reviewsOf(repository.getReviewsFlow(UUID).first()).others
            others.map { it.rating } shouldBe listOf(0.5f, 5f, null, null, null)
            others.map { it.text } shouldBe listOf(null, null, "Great", null, null)
        }

        it("TC-8: updatedAtMillis prefers seconds, then the ISO string, then null") {
            val repository = repositoryFor()
            val rows = listOf(
                obj("user_id" to 1L, "updated_at" to 1_761_775_445L),
                obj("user_id" to 2L, "updated_at_iso" to "2025-10-29T21:24:05Z"),
                obj("user_id" to 3L),
            )
            script.answer(reply(200, reviewsBody(others = rows)))
            repository.refresh(UUID)

            val others = reviewsOf(repository.getReviewsFlow(UUID).first()).others
            others[0].updatedAtMillis shouldBe 1_761_775_445_000L
            others[1].updatedAtMillis.shouldNotBeNull() shouldBe BookDetailFormat.parseServerTimestamp("2025-10-29T21:24:05Z")
            others[2].updatedAtMillis shouldBe null
        }

        it("TC-9: the summary maps only with at least one rating") {
            val repository = repositoryFor()
            val summaries = listOf(
                obj("average_stars" to 4.33, "ratings" to 3),
                obj("average_stars" to 4.0, "ratings" to 1),
                obj("average_stars" to 4.0, "ratings" to 0),
                null,
            )
            val expected = listOf(ReviewSummary(4.33f, 3), ReviewSummary(4.0f, 1), null, null)
            summaries.forEachIndexed { index, summary ->
                script.answer(reply(200, reviewsBody(summary = summary)))
                repository.refresh(UUID)

                reviewsOf(repository.getReviewsFlow(UUID).first()).summary shouldBe expected[index]
            }
        }

        it("TC-10: an IOException is Offline while offline and Failed while online") {
            script.answer(dropped())
            observeNow(repositoryFor(monitor = FakeConnectivityMonitor(online = false))).states.last() shouldBe
                ReviewsState.Offline

            script.answer(dropped())
            observeNow(repositoryFor(monitor = FakeConnectivityMonitor(online = true))).states.last() shouldBe
                ReviewsState.Failed
        }

        it("TC-11: every other failure is Failed, and no server or a blank uuid sends nothing") {
            for (code in listOf(401, 404, 500)) {
                script.answer(reply(code))
                observeNow(repositoryFor()).states.last() shouldBe ReviewsState.Failed
            }
            script.answer(reply(200, "not json"))
            observeNow(repositoryFor()).states.last() shouldBe ReviewsState.Failed

            val before = server.requestCount
            observeNow(repositoryFor(baseUrl = null)).states.last() shouldBe ReviewsState.Failed
            observeNow(repositoryFor(), uuid = "  ").states.last() shouldBe ReviewsState.Failed
            server.requestCount shouldBe before
        }

        it("TC-12: a refetch from Failed or Offline passes through Loading") {
            val failedGate = CountDownLatch(1)
            val failedRepository = repositoryFor(held = true)
            script.answer(reply(500), failedGate)
            val failed = Observed(failedRepository, UUID, newScope(Dispatchers.Default))
            nextRequest()
            failed.awaitLast(ReviewsState.Loading)
            failedGate.countDown()
            failed.awaitLast(ReviewsState.Failed)

            val okGate = CountDownLatch(1)
            script.answer(reply(200, reviewsBody()), okGate)
            val refresh = newScope(Dispatchers.Default).async { failedRepository.refresh(UUID) }
            nextRequest()
            okGate.countDown()
            refresh.await()
            failed.awaitLast(EMPTY)
            failed.states shouldBe listOf(ReviewsState.Loading, ReviewsState.Failed, ReviewsState.Loading, EMPTY)

            val offlineGate = CountDownLatch(1)
            script.answer(dropped(), offlineGate)
            val offlineRepository = repositoryFor(held = true, monitor = FakeConnectivityMonitor(online = false))
            val offline = Observed(offlineRepository, UUID, newScope(Dispatchers.Default))
            nextRequest()
            offline.awaitLast(ReviewsState.Loading)
            offlineGate.countDown()
            offline.awaitLast(ReviewsState.Offline)

            val refreshGate = CountDownLatch(1)
            script.answer(reply(200, reviewsBody()), refreshGate)
            val second = newScope(Dispatchers.Default).async { offlineRepository.refresh(UUID) }
            nextRequest()
            refreshGate.countDown()
            second.await()
            offline.awaitLast(EMPTY)
            offline.states shouldBe listOf(ReviewsState.Loading, ReviewsState.Offline, ReviewsState.Loading, EMPTY)
        }

        it("TC-13: going online while Offline refetches once") {
            val monitor = FakeConnectivityMonitor(online = false)
            val repository = repositoryFor(monitor = monitor)
            script.answer(dropped())
            val observed = observeNow(repository)
            observed.states.last() shouldBe ReviewsState.Offline
            val before = server.requestCount

            script.answer(reply(200, reviewsBody()))
            monitor.isOnline.value = true
            observed.states.last() shouldBe EMPTY
            server.requestCount shouldBe before + 1

            monitor.isOnline.value = false
            monitor.isOnline.value = true
            server.requestCount shouldBe before + 1
        }

        it("TC-14: two collectors share the one reconnect GET") {
            val monitor = FakeConnectivityMonitor(online = false)
            val repository = repositoryFor(held = true, monitor = monitor)
            val scope = newScope(Dispatchers.Default)
            val dropGate = CountDownLatch(1)
            script.answer(dropped(), dropGate)
            val first = Observed(repository, UUID, scope)
            nextRequest()
            first.awaitLast(ReviewsState.Loading)
            val second = Observed(repository, UUID, scope)
            delay(200)
            dropGate.countDown()
            first.awaitLast(ReviewsState.Offline)
            second.awaitLast(ReviewsState.Offline)
            server.requestCount shouldBe 1

            val okGate = CountDownLatch(1)
            script.answer(reply(200, reviewsBody()), okGate)
            monitor.isOnline.value = true
            nextRequest()
            delay(200) // give both collectors time to join the reconnect GET in flight
            okGate.countDown()

            first.awaitLast(EMPTY)
            second.awaitLast(EMPTY)
            server.requestCount shouldBe 2
        }

        it("TC-15: reconnect refetches only an Offline state that is being collected") {
            val failedMonitor = FakeConnectivityMonitor(online = false)
            script.answer(reply(500))
            observeNow(repositoryFor(monitor = failedMonitor)).states.last() shouldBe ReviewsState.Failed
            val beforeFailed = server.requestCount
            failedMonitor.isOnline.value = true
            server.requestCount shouldBe beforeFailed

            val loadedMonitor = FakeConnectivityMonitor(online = true)
            script.answer(reply(200, reviewsBody()))
            observeNow(repositoryFor(monitor = loadedMonitor)).states.last() shouldBe EMPTY
            val beforeLoaded = server.requestCount
            loadedMonitor.isOnline.value = false
            loadedMonitor.isOnline.value = true
            server.requestCount shouldBe beforeLoaded

            val cancelledMonitor = FakeConnectivityMonitor(online = false)
            script.answer(dropped())
            val cancelled = observeNow(repositoryFor(monitor = cancelledMonitor))
            cancelled.states.last() shouldBe ReviewsState.Offline
            cancelled.stop()
            val beforeCancelled = server.requestCount
            cancelledMonitor.isOnline.value = true
            server.requestCount shouldBe beforeCancelled
        }
    }
})
