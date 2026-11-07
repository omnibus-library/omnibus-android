package com.omnibus.omnibus.data.reviews

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
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
 * Answers requests from a script. A step can hold its response until the test releases [CountDownLatch]. A step with a
 * [method] answers only that method, so a GET and a PATCH in flight at once cannot take each other's answers. Otherwise
 * the first step answers the next request. A request with no step gets a 500, so an unexpected call fails the test
 * instead of hanging.
 */
private class ScriptedDispatcher : Dispatcher() {
    private class Step(val method: String?, val response: MockResponse, val gate: CountDownLatch?)

    private val steps = ArrayDeque<Step>()

    fun answer(response: MockResponse, gate: CountDownLatch? = null, method: String? = null) {
        synchronized(steps) { steps.addLast(Step(method, response, gate)) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val step = synchronized(steps) {
            steps.indexOfFirst { it.method == null || it.method == request.method }
                .takeIf { it >= 0 }
                ?.let { steps.removeAt(it) }
        } ?: return reply(500, "unscripted")
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
        monitor: ConnectivityMonitor = TestConnectivityMonitor(online = true),
        provider: TestServerBaseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
    ): DefaultReviewsRepository {
        val scope = newScope(if (held) Dispatchers.Default else Dispatchers.Unconfined)
        return DefaultReviewsRepository(
            ioDispatcher = if (held) Dispatchers.IO else Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = provider,
                authTokenProvider = TestAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
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
            val monitor = TestConnectivityMonitor(online = false)
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
            observeNow(repositoryFor(monitor = TestConnectivityMonitor(online = false))).states.last() shouldBe
                ReviewsState.Offline

            script.answer(dropped())
            observeNow(repositoryFor(monitor = TestConnectivityMonitor(online = true))).states.last() shouldBe
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
            val offlineRepository = repositoryFor(held = true, monitor = TestConnectivityMonitor(online = false))
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
            val monitor = TestConnectivityMonitor(online = false)
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
            val monitor = TestConnectivityMonitor(online = false)
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
            val failedMonitor = TestConnectivityMonitor(online = false)
            script.answer(reply(500))
            observeNow(repositoryFor(monitor = failedMonitor)).states.last() shouldBe ReviewsState.Failed
            val beforeFailed = server.requestCount
            failedMonitor.isOnline.value = true
            server.requestCount shouldBe beforeFailed

            val loadedMonitor = TestConnectivityMonitor(online = true)
            script.answer(reply(200, reviewsBody()))
            observeNow(repositoryFor(monitor = loadedMonitor)).states.last() shouldBe EMPTY
            val beforeLoaded = server.requestCount
            loadedMonitor.isOnline.value = false
            loadedMonitor.isOnline.value = true
            server.requestCount shouldBe beforeLoaded

            val cancelledMonitor = TestConnectivityMonitor(online = false)
            script.answer(dropped())
            val cancelled = observeNow(repositoryFor(monitor = cancelledMonitor))
            cancelled.states.last() shouldBe ReviewsState.Offline
            cancelled.stop()
            val beforeCancelled = server.requestCount
            cancelledMonitor.isOnline.value = true
            server.requestCount shouldBe beforeCancelled
        }
    }

    describe("writing my review") {

        /** The signed-in reader's review with [rating], [text] and [notes], as the repository maps it. */
        fun mine(rating: Float? = null, text: String? = null, notes: String? = null, updatedAt: Long = 100_000L) =
            Review(0L, "", false, rating, text, notes, isSpoiler = false, updatedAtMillis = updatedAt)

        /** A Loaded state whose reviews are [mine] and [others]. */
        fun loaded(mine: Review?, others: List<Review> = emptyList(), summary: ReviewSummary? = null) =
            ReviewsState.Loaded(BookReviews(mine, others, summary))

        /** Loads [uuid] with one scripted GET, and takes that request from the server's queue. */
        suspend fun load(
            repository: ReviewsRepository,
            uuid: String = UUID,
            mine: JsonObject? = null,
            others: List<JsonObject> = emptyList(),
            summary: JsonObject? = null,
        ) {
            script.answer(reply(200, reviewsBody(mine, others, summary)), method = "GET")
            repository.refresh(uuid)
            nextRequest()
        }

        /** Waits until the repository's reviews for [UUID] satisfy [predicate]. */
        suspend fun awaitReviews(repository: ReviewsRepository, predicate: (BookReviews) -> Boolean) {
            withTimeout(5_000) {
                while (!predicate(reviewsOf(repository.getReviewsFlow(UUID).first()))) delay(10)
            }
        }

        /** A PATCH answer for a review with [stars], [text] and [notes], updated at [seconds]. */
        fun patchAnswer(stars: Double? = null, text: String? = null, notes: String? = null, seconds: Long = 200L): String =
            obj("stars" to stars, "body_md" to text, "notes_md" to notes, "updated_at" to seconds).toString()

        it("TC-14: a rating PATCH sends the stars with the bearer token and returns the stored review") {
            val repository = repositoryFor()
            load(repository)
            script.answer(reply(200, patchAnswer(stars = 4.5)), method = "PATCH")
            script.answer(reply(200, reviewsBody(mine = obj("stars" to 4.5, "updated_at" to 200L))), method = "GET")

            val result = repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f)))

            val patch = nextRequest()
            patch.method shouldBe "PATCH"
            patch.url.pathSegments shouldContainExactly listOf("api", "reviews", UUID)
            patch.headers["Authorization"] shouldBe "Bearer secret"
            patch.body!!.utf8() shouldBe """{"stars":4.5}"""
            result shouldBe ReviewWriteResult.Saved(mine(rating = 4.5f, updatedAt = 200_000L))
        }

        it("TC-15: clearing the rating sends exactly {\"stars\":null} and keeps the text and notes") {
            val repository = repositoryFor()
            load(repository, mine = obj("stars" to 4.0, "body_md" to "Body", "notes_md" to "private", "updated_at" to 100L))
            val kept = obj("body_md" to "Body", "notes_md" to "private", "updated_at" to 200L)
            script.answer(reply(200, kept.toString()), method = "PATCH")
            script.answer(reply(200, reviewsBody(mine = kept)), method = "GET")

            val result = repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(null)))

            nextRequest().body!!.utf8() shouldBe """{"stars":null}"""
            val expected = mine(text = "Body", notes = "private", updatedAt = 200_000L)
            result shouldBe ReviewWriteResult.Saved(expected)
            reviewsOf(repository.getReviewsFlow(UUID).first()).mine shouldBe expected
        }

        it("TC-17: a patch that changes nothing returns Unchanged, sends nothing and leaves the state alone") {
            val repository = repositoryFor()
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val state = repository.getReviewsFlow(UUID).first()
            val before = server.requestCount

            repository.updateMyReview(UUID, ReviewPatch()) shouldBe ReviewWriteResult.Unchanged
            repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4f))) shouldBe ReviewWriteResult.Unchanged
            server.requestCount shouldBe before
            repository.getReviewsFlow(UUID).first() shouldBe state

            val blank = repositoryFor()
            load(blank)
            blank.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(null))) shouldBe ReviewWriteResult.Unchanged
            server.requestCount shouldBe before + 1
        }

        it("TC-18: both collectors see the optimistic rating before the answer, then the server's review") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val gate = CountDownLatch(1)
            script.answer(reply(200, patchAnswer(stars = 4.5, notes = "server notes", seconds = 300L)), gate, "PATCH")
            script.answer(
                reply(200, reviewsBody(mine = obj("stars" to 4.5, "notes_md" to "server notes", "updated_at" to 300L))),
                method = "GET",
            )
            val first = Observed(repository, UUID, scope)
            val second = Observed(repository, UUID, scope)

            val write = scope.async { repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) }
            nextRequest()
            first.awaitLast(loaded(mine(rating = 4.5f)))
            second.awaitLast(loaded(mine(rating = 4.5f)))

            gate.countDown()
            write.await() shouldBe ReviewWriteResult.Saved(mine(4.5f, notes = "server notes", updatedAt = 300_000L))
            first.awaitLast(loaded(mine(4.5f, notes = "server notes", updatedAt = 300_000L)))
            second.awaitLast(loaded(mine(4.5f, notes = "server notes", updatedAt = 300_000L)))
        }

        it("TC-19: a patch that empties a stars-only review shows no review at once, and Saved(null) after the JSON null") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val gate = CountDownLatch(1)
            script.answer(reply(200, "null"), gate, "PATCH")
            script.answer(reply(200, reviewsBody()), method = "GET")
            val observed = Observed(repository, UUID, scope)

            val write = scope.async { repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(null))) }
            nextRequest()
            observed.awaitLast(EMPTY)

            gate.countDown()
            write.await() shouldBe ReviewWriteResult.Saved(null)
            observed.awaitLast(EMPTY)
            repository.getReviewsFlow(UUID).first() shouldBe EMPTY
        }

        it("TC-20: a delete sends DELETE with or without a review, shows no review at once and answers Saved(null)") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val gate = CountDownLatch(1)
            script.answer(reply(204), gate, "DELETE")
            script.answer(reply(200, reviewsBody()), method = "GET")
            val observed = Observed(repository, UUID, scope)

            val write = scope.async { repository.deleteMyReview(UUID) }
            val delete = nextRequest()
            delete.method shouldBe "DELETE"
            delete.url.pathSegments shouldContainExactly listOf("api", "reviews", UUID)
            observed.awaitLast(EMPTY)
            gate.countDown()
            write.await() shouldBe ReviewWriteResult.Saved(null)
            nextRequest().method shouldBe "GET"
            repository.getReviewsFlow(UUID).first() shouldBe EMPTY

            val empty = repositoryFor()
            load(empty)
            script.answer(reply(204), method = "DELETE")
            script.answer(reply(200, reviewsBody()), method = "GET")
            empty.deleteMyReview(UUID) shouldBe ReviewWriteResult.Saved(null)
            nextRequest().method shouldBe "DELETE"
        }

        it("TC-21: after a save one refetch updates summary and others, and mine stays the saved review") {
            val repository = repositoryFor()
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            script.answer(reply(200, patchAnswer(stars = 4.5)), method = "PATCH")
            script.answer(
                reply(
                    200,
                    reviewsBody(
                        mine = obj("stars" to 4.5, "updated_at" to 200L),
                        others = listOf(obj("user_id" to 2L, "username" to "Amy", "stars" to 3.0)),
                        summary = obj("average_stars" to 3.75, "ratings" to 2),
                    ),
                ),
                method = "GET",
            )

            repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Saved(mine(4.5f, updatedAt = 200_000L))

            server.requestCount shouldBe 3
            val state = reviewsOf(repository.getReviewsFlow(UUID).first())
            state.mine shouldBe mine(4.5f, updatedAt = 200_000L)
            state.others.map { it.userId } shouldBe listOf(2L)
            state.summary shouldBe ReviewSummary(3.75f, 2)
        }

        it("TC-21: a GET that started after the write is joined, so no second refetch is made") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val patchGate = CountDownLatch(1)
            val getGate = CountDownLatch(1)
            script.answer(reply(200, patchAnswer(stars = 4.5)), patchGate, "PATCH")
            script.answer(
                reply(200, reviewsBody(mine = obj("stars" to 4.5, "updated_at" to 200L), others = listOf(obj("user_id" to 2L)))),
                getGate,
                "GET",
            )

            val write = scope.async { repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) }
            nextRequest().method shouldBe "PATCH"
            val refresh = scope.async { repository.refresh(UUID) }
            nextRequest().method shouldBe "GET"

            patchGate.countDown()
            write.await() shouldBe ReviewWriteResult.Saved(mine(4.5f, updatedAt = 200_000L))
            getGate.countDown()
            refresh.await()
            awaitReviews(repository) { it.others.isNotEmpty() }

            server.requestCount shouldBe 3
        }

        it("TC-22: a refetch that fails after a save keeps the loaded state and the saved review") {
            val repository = repositoryFor()
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            script.answer(reply(200, patchAnswer(stars = 4.5)), method = "PATCH")
            script.answer(reply(500), method = "GET")

            repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Saved(mine(4.5f, updatedAt = 200_000L))
            reviewsOf(repository.getReviewsFlow(UUID).first()).mine shouldBe mine(4.5f, updatedAt = 200_000L)

            script.answer(reply(200, patchAnswer(stars = 3.5, seconds = 300L)), method = "PATCH")
            script.answer(dropped(), method = "GET")
            repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(3.5f))) shouldBe
                ReviewWriteResult.Saved(mine(3.5f, updatedAt = 300_000L))
            reviewsOf(repository.getReviewsFlow(UUID).first()).mine shouldBe mine(3.5f, updatedAt = 300_000L)
        }

        it("TC-23: each write error maps to its ReviewError, nothing throws, and a blank uuid sends nothing") {
            val provider = TestServerBaseUrlProvider(baseUrl = server.url("/"))
            val repository = repositoryFor(provider = provider)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val previous = repository.getReviewsFlow(UUID).first()

            provider.baseUrl = null
            repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Failed(ReviewError.NoServer)
            repository.deleteMyReview(UUID) shouldBe ReviewWriteResult.Failed(ReviewError.NoServer)
            provider.baseUrl = server.url("/")
            repository.getReviewsFlow(UUID).first() shouldBe previous

            val patchFailures = listOf(
                dropped() to ReviewError.Unreachable,
                reply(400) to ReviewError.Http(400),
                reply(401) to ReviewError.SessionExpired,
                reply(404) to ReviewError.BookNotFound,
                reply(500) to ReviewError.Http(500),
                reply(200, "garbage") to ReviewError.UnexpectedResponse,
            )
            patchFailures.forEach { (response, error) ->
                script.answer(response, method = "PATCH")
                repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                    ReviewWriteResult.Failed(error)
                repository.getReviewsFlow(UUID).first() shouldBe previous
            }

            val deleteFailures = listOf(
                dropped() to ReviewError.Unreachable,
                reply(400) to ReviewError.Http(400),
                reply(401) to ReviewError.SessionExpired,
                reply(404) to ReviewError.BookNotFound,
                reply(500) to ReviewError.Http(500),
            )
            deleteFailures.forEach { (response, error) ->
                script.answer(response, method = "DELETE")
                repository.deleteMyReview(UUID) shouldBe ReviewWriteResult.Failed(error)
                repository.getReviewsFlow(UUID).first() shouldBe previous
            }

            val before = server.requestCount
            repository.updateMyReview(" ", ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Failed(ReviewError.BookNotFound)
            repository.deleteMyReview("") shouldBe ReviewWriteResult.Failed(ReviewError.BookNotFound)
            server.requestCount shouldBe before
        }

        it("TC-24: a failed write restores the exact previous state, and no refetch follows the failure") {
            val repository = repositoryFor()
            load(
                repository,
                mine = obj("stars" to 4.0, "updated_at" to 100L),
                others = listOf(obj("user_id" to 2L, "username" to "Amy", "stars" to 3.0)),
                summary = obj("average_stars" to 3.5, "ratings" to 2),
            )
            val previous = repository.getReviewsFlow(UUID).first()
            val observed = observeNow(repository)
            script.answer(reply(500), method = "PATCH")
            val before = server.requestCount

            repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Failed(ReviewError.Http(500))

            observed.states shouldContain loaded(mine(4.5f), reviewsOf(previous).others, reviewsOf(previous).summary)
            observed.states.last() shouldBe previous
            server.requestCount shouldBe before + 1
        }

        it("TC-25: an older write's success does not replace a newer write's optimistic value") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val observed = Observed(repository, UUID, scope)
            val gateA = CountDownLatch(1)
            val gateB = CountDownLatch(1)
            script.answer(reply(200, patchAnswer(stars = 4.5)), gateA, "PATCH")
            script.answer(reply(200, patchAnswer(stars = 3.5, seconds = 300L)), gateB, "PATCH")
            script.answer(reply(200, reviewsBody(mine = obj("stars" to 4.5, "updated_at" to 200L))), method = "GET")
            script.answer(reply(200, reviewsBody(mine = obj("stars" to 3.5, "updated_at" to 300L))), method = "GET")

            val a = scope.async { repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) }
            nextRequest()
            val b = scope.async { repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(3.5f))) }
            observed.awaitLast(loaded(mine(rating = 3.5f)))

            gateA.countDown()
            a.await() shouldBe ReviewWriteResult.Saved(mine(4.5f, updatedAt = 200_000L))
            repository.getReviewsFlow(UUID).first() shouldBe loaded(mine(rating = 3.5f))

            // The refetch after A and B's PATCH are both sent once A is answered, in either order.
            nextRequest()
            nextRequest()
            gateB.countDown()
            b.await() shouldBe ReviewWriteResult.Saved(mine(3.5f, updatedAt = 300_000L))
            observed.awaitLast(loaded(mine(3.5f, updatedAt = 300_000L)))
        }

        it("TC-26: an older write's failure does not roll back a newer write, and the newer failure restores the confirmed value") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val original = repository.getReviewsFlow(UUID).first()
            val gateA = CountDownLatch(1)
            val gateB = CountDownLatch(1)
            script.answer(reply(500), gateA, "PATCH")
            script.answer(reply(500), gateB, "PATCH")

            val a = scope.async { repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) }
            nextRequest()
            val b = scope.async { repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(3.5f))) }
            awaitReviews(repository) { it.mine?.rating == 3.5f }

            gateA.countDown()
            a.await() shouldBe ReviewWriteResult.Failed(ReviewError.Http(500))
            repository.getReviewsFlow(UUID).first() shouldBe loaded(mine(rating = 3.5f))

            nextRequest()
            gateB.countDown()
            b.await() shouldBe ReviewWriteResult.Failed(ReviewError.Http(500))
            repository.getReviewsFlow(UUID).first() shouldBe original
        }

        it("TC-27: writes reach the server in call order, and the second waits until the first is answered") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val gate = CountDownLatch(1)
            script.answer(reply(200, patchAnswer(stars = 4.5)), gate, "PATCH")
            script.answer(reply(200, patchAnswer(stars = 3.5, seconds = 300L)), method = "PATCH")
            script.answer(reply(200, reviewsBody(mine = obj("stars" to 3.5, "updated_at" to 300L))), method = "GET")
            script.answer(reply(200, reviewsBody(mine = obj("stars" to 3.5, "updated_at" to 300L))), method = "GET")

            var first: ReviewWriteResult? = null
            var second: ReviewWriteResult? = null
            val done = scope.launch {
                val a = async(start = CoroutineStart.UNDISPATCHED) {
                    repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f)))
                }
                val b = async(start = CoroutineStart.UNDISPATCHED) {
                    repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(3.5f)))
                }
                first = a.await()
                second = b.await()
            }

            nextRequest().body!!.utf8() shouldBe """{"stars":4.5}"""
            server.takeRequest(300, TimeUnit.MILLISECONDS) shouldBe null
            gate.countDown()
            val next = listOf(nextRequest(), nextRequest())
            next.single { it.method == "PATCH" }.body!!.utf8() shouldBe """{"stars":3.5}"""
            done.join()
            first shouldBe ReviewWriteResult.Saved(mine(4.5f, updatedAt = 200_000L))
            second shouldBe ReviewWriteResult.Saved(mine(3.5f, updatedAt = 300_000L))
        }

        it("TC-28: a cancelled caller gets CancellationException, but the write completes and the state updates") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val gate = CountDownLatch(1)
            script.answer(reply(200, patchAnswer(stars = 4.5)), gate, "PATCH")
            script.answer(reply(200, reviewsBody(mine = obj("stars" to 4.5, "updated_at" to 200L))), method = "GET")
            val observed = Observed(repository, UUID, scope)

            val caller = scope.async { repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) }
            nextRequest()
            caller.cancel()
            shouldThrow<CancellationException> { caller.await() }

            gate.countDown()
            observed.awaitLast(loaded(mine(4.5f, updatedAt = 200_000L)))
        }

        it("TC-29: a write while the state is Loading, Offline or Failed fails with NotLoaded and sends nothing") {
            val fresh = repositoryFor()
            val before = server.requestCount
            fresh.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Failed(ReviewError.NotLoaded)
            fresh.updateMyReview(UUID, ReviewPatch()) shouldBe ReviewWriteResult.Failed(ReviewError.NotLoaded)
            fresh.deleteMyReview(UUID) shouldBe ReviewWriteResult.Failed(ReviewError.NotLoaded)
            server.requestCount shouldBe before

            val failed = repositoryFor(baseUrl = null)
            observeNow(failed).states.last() shouldBe ReviewsState.Failed
            failed.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Failed(ReviewError.NotLoaded)
            failed.deleteMyReview(UUID) shouldBe ReviewWriteResult.Failed(ReviewError.NotLoaded)

            script.answer(dropped(), method = "GET")
            val offline = repositoryFor(monitor = TestConnectivityMonitor(online = false))
            observeNow(offline).states.last() shouldBe ReviewsState.Offline
            val offlineBefore = server.requestCount
            offline.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Failed(ReviewError.NotLoaded)
            offline.deleteMyReview(UUID) shouldBe ReviewWriteResult.Failed(ReviewError.NotLoaded)
            server.requestCount shouldBe offlineBefore
        }

        it("TC-30: a GET that started before a save never replaces the saved review, and one refetch follows it") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val staleGate = CountDownLatch(1)
            script.answer(
                reply(200, reviewsBody(mine = obj("stars" to 4.0, "updated_at" to 100L), others = listOf(obj("user_id" to 2L, "username" to "Amy", "stars" to 3.0)))),
                staleGate,
                "GET",
            )
            script.answer(reply(200, patchAnswer(stars = 4.5)), method = "PATCH")
            script.answer(
                reply(
                    200,
                    reviewsBody(
                        mine = obj("stars" to 4.5, "updated_at" to 200L),
                        others = listOf(obj("user_id" to 2L, "username" to "Amy", "stars" to 3.0), obj("user_id" to 5L, "username" to "Bo", "stars" to 4.0)),
                        summary = obj("average_stars" to 3.5, "ratings" to 2),
                    ),
                ),
                method = "GET",
            )

            val refresh = scope.async { repository.refresh(UUID) }
            nextRequest().method shouldBe "GET"
            repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Saved(mine(4.5f, updatedAt = 200_000L))
            nextRequest().method shouldBe "PATCH"

            staleGate.countDown()
            refresh.await()
            awaitReviews(repository) { it.summary != null }
            awaitReviews(repository) { it.others.size == 2 }

            val state = reviewsOf(repository.getReviewsFlow(UUID).first())
            state.mine shouldBe mine(4.5f, updatedAt = 200_000L)
            state.summary shouldBe ReviewSummary(3.5f, 2)
            server.requestCount shouldBe 4
        }

        it("TC-31: a GET that completes while a write is pending leaves the optimistic value, and a failure rolls back to it") {
            val repository = repositoryFor(held = true)
            val scope = newScope(Dispatchers.Default)
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            val getGate = CountDownLatch(1)
            val patchGate = CountDownLatch(1)
            script.answer(
                reply(200, reviewsBody(mine = obj("stars" to 3.0, "updated_at" to 150L), others = listOf(obj("user_id" to 2L, "username" to "Amy")))),
                getGate,
                "GET",
            )
            script.answer(reply(500), patchGate, "PATCH")

            val refresh = scope.async { repository.refresh(UUID) }
            nextRequest().method shouldBe "GET"
            val write = scope.async { repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f))) }
            nextRequest().method shouldBe "PATCH"

            getGate.countDown()
            refresh.await()
            // The visible state keeps the optimistic review (and its others) while the write is pending.
            reviewsOf(repository.getReviewsFlow(UUID).first()).mine?.rating shouldBe 4.5f

            patchGate.countDown()
            write.await() shouldBe ReviewWriteResult.Failed(ReviewError.Http(500))
            val rolledBack = reviewsOf(repository.getReviewsFlow(UUID).first())
            rolledBack.mine shouldBe mine(3f, updatedAt = 150_000L)
            rolledBack.others.map { it.userId } shouldBe listOf(2L)
        }

        it("TC-32: a write on one book leaves another book's state and requests alone") {
            val repository = repositoryFor()
            load(repository, uuid = "book-2", mine = obj("stars" to 3.0, "updated_at" to 50L))
            val other = repository.getReviewsFlow("book-2").first()
            load(repository, mine = obj("stars" to 4.0, "updated_at" to 100L))
            script.answer(reply(200, patchAnswer(stars = 4.5)), method = "PATCH")
            script.answer(reply(200, reviewsBody(mine = obj("stars" to 4.5, "updated_at" to 200L))), method = "GET")

            repository.updateMyReview(UUID, ReviewPatch(rating = Patch.Set(4.5f)))

            repository.getReviewsFlow("book-2").first() shouldBe other
            val patch = nextRequest()
            patch.url.pathSegments shouldContainExactly listOf("api", "reviews", UUID)
            server.requestCount shouldBe 4
        }
    }
})
