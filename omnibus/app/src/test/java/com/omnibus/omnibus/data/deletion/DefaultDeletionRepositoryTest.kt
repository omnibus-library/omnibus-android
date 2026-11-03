package com.omnibus.omnibus.data.deletion

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect

private const val UUID = "book-1"

private const val MANIFEST_PATH = "/api/books/$UUID/deletion-manifest"
private const val DELETE_PATH = "/api/books/$UUID/delete-files"

/** A manifest body with two files, one copy and some impact, in the server's field names. */
private const val FULL_MANIFEST = """
{
  "files": [
    {"id": 1, "format": "epub", "filename": "a.epub", "ordinal": 0, "label": "Read copy", "size_bytes": 2048},
    {"id": 2, "format": "pdf", "filename": "b.pdf", "ordinal": 1, "label": null, "size_bytes": 0}
  ],
  "copies": [
    {"id": 5, "book_uuid": "$UUID", "isbn": "9780000000001", "checked_in_at": 1700000000, "note": "shelf 3"}
  ],
  "impact": {
    "highlights": 4, "journal_entries": 1, "bookmarks": 2, "reading_sessions": 3,
    "listening_sessions": 0, "ratings": 6, "shelves": 7
  }
}
"""

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

private fun disconnect(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

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

class DefaultDeletionRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    val scopes = mutableListOf<CoroutineScope>()

    /** A new scope that is cancelled after the test. */
    fun newScope(dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher).also { scopes += it }

    /**
     * A repository for the test server, or for no server when [baseUrlProvider] has none. [held] runs HTTP on real
     * threads, which lets a test hold a request open. Otherwise everything runs unconfined on the calling thread.
     */
    fun repositoryFor(held: Boolean = false, applicationScope: CoroutineScope? = null): DefaultDeletionRepository {
        val scope = applicationScope ?: newScope(if (held) Dispatchers.Default else Dispatchers.Unconfined)
        return DefaultDeletionRepository(
            ioDispatcher = if (held) Dispatchers.IO else Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = baseUrlProvider,
                authTokenProvider = FakeAuthTokenProvider(token = "secret"),
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
            applicationScope = scope,
        )
    }

    /** Waits for the next request the server received, in arrival order. */
    fun nextRequest(): RecordedRequest =
        checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "No request reached the server" }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        script = ScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
        baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/"))
    }

    afterEach {
        scopes.forEach { it.cancel() }
        scopes.clear()
        server.close()
    }

    describe("reading the deletion manifest") {

        it("#121 TC-18: a GET with the bearer token maps files, copies and impact to the domain types, and itemCount adds them") {
            script.answer("GET", reply(200, FULL_MANIFEST))

            val result = repositoryFor().fetchManifest(UUID)

            val request = nextRequest()
            request.method shouldBe "GET"
            request.url.encodedPath shouldBe MANIFEST_PATH
            request.headers["Authorization"] shouldBe "Bearer secret"
            server.requestCount shouldBe 1

            val manifest = (result as DeletionManifestResult.Success).manifest
            manifest.files shouldBe listOf(
                DeletableFile(id = 1, format = "epub", filename = "a.epub", label = "Read copy", sizeBytes = 2048),
                DeletableFile(id = 2, format = "pdf", filename = "b.pdf", label = null, sizeBytes = 0),
            )
            manifest.copies shouldBe listOf(DeletableCopy(id = 5, isbn = "9780000000001", note = "shelf 3"))
            manifest.impact shouldBe DeletionImpact(
                highlights = 4,
                journalEntries = 1,
                bookmarks = 2,
                readingSessions = 3,
                listeningSessions = 0,
                reviews = 6,
                shelves = 7,
            )
            manifest.itemCount shouldBe 3
        }

        it("#121 TC-19: unknown keys are ignored, missing fields take their defaults, and {} is an empty manifest") {
            val repository = repositoryFor()

            script.answer(
                "GET",
                reply(
                    200,
                    """{"files":[{"id":1,"filename":"a.epub","extra":true,"hash":"x"}],"copies":[],"impact":{"highlights":1,"unknown":4},"other":"x"}""",
                ),
            )
            (repository.fetchManifest(UUID) as DeletionManifestResult.Success).manifest.let { manifest ->
                manifest.files shouldBe listOf(
                    DeletableFile(id = 1, format = "", filename = "a.epub", label = null, sizeBytes = 0),
                )
                manifest.copies shouldBe emptyList()
                manifest.impact shouldBe DeletionImpact(highlights = 1)
            }

            script.answer("GET", reply(200, """{"files":[{"id":4,"format":"pdf","filename":"c.pdf","label":null,"size_bytes":5}],"copies":[{"id":5,"isbn":null,"note":null}]}"""))
            (repository.fetchManifest(UUID) as DeletionManifestResult.Success).manifest.let { manifest ->
                manifest.files shouldBe listOf(DeletableFile(id = 4, format = "pdf", filename = "c.pdf", label = null, sizeBytes = 5))
                manifest.copies shouldBe listOf(DeletableCopy(id = 5, isbn = null, note = null))
                manifest.impact shouldBe DeletionImpact()
            }

            script.answer("GET", reply(200, "{}"))
            (repository.fetchManifest(UUID) as DeletionManifestResult.Success).manifest.let { manifest ->
                manifest.files shouldBe emptyList()
                manifest.copies shouldBe emptyList()
                manifest.itemCount shouldBe 0
            }
        }

        it("#121 TC-20: the uuid is one encoded path segment, and a blank uuid fails with no request for both calls") {
            script.answer("GET", reply(200, "{}"))
            repositoryFor().fetchManifest("a/b c")
            nextRequest().url.encodedPath shouldBe "/api/books/a%2Fb%20c/deletion-manifest"

            script.answer("POST", reply(200, "{}"))
            repositoryFor().deleteItems("a/b c", setOf(1), emptySet())
            nextRequest().url.encodedPath shouldBe "/api/books/a%2Fb%20c/delete-files"

            val before = server.requestCount
            val repository = repositoryFor()
            repository.fetchManifest("") shouldBe DeletionManifestResult.Failed(DeletionError.BookNotFound)
            repository.fetchManifest("   ") shouldBe DeletionManifestResult.Failed(DeletionError.BookNotFound)
            repository.deleteItems("", setOf(1), emptySet()) shouldBe DeleteItemsResult.Failed(DeletionError.BookNotFound)
            repository.deleteItems("   ", emptySet(), setOf(2)) shouldBe DeleteItemsResult.Failed(DeletionError.BookNotFound)
            server.requestCount shouldBe before
        }

        it("#121 TC-23: every failed manifest read maps to its error, and nothing is thrown") {
            val failures = listOf(
                reply(401) to DeletionError.SessionExpired,
                reply(403) to DeletionError.Forbidden,
                reply(404) to DeletionError.BookNotFound,
                reply(500) to DeletionError.Http(500),
                disconnect() to DeletionError.Unreachable,
            )
            for ((failure, error) in failures) {
                script.answer("GET", failure)
                repositoryFor().fetchManifest(UUID) shouldBe DeletionManifestResult.Failed(error)
            }
        }

        it("#121 TC-25: a 200 that does not decode, and a missing server, fail the read without throwing") {
            script.answer("GET", reply(200, "not json"))
            repositoryFor().fetchManifest(UUID) shouldBe DeletionManifestResult.Failed(DeletionError.UnexpectedResponse)

            script.answer("GET", reply(200, """{"files":3}"""))
            repositoryFor().fetchManifest(UUID) shouldBe DeletionManifestResult.Failed(DeletionError.UnexpectedResponse)

            baseUrlProvider.baseUrl = null
            val before = server.requestCount
            repositoryFor().fetchManifest(UUID) shouldBe DeletionManifestResult.Failed(DeletionError.NoServer)
            server.requestCount shouldBe before
            baseUrlProvider.baseUrl = server.url("/")
        }
    }

    describe("deleting files and copies") {

        it("#121 TC-21: one POST to delete-files with the bearer token and a JSON body that always has both keys, ids sorted") {
            val repository = repositoryFor()
            val bodies = listOf(
                Triple(setOf(2L, 1L), emptySet<Long>(), """{"file_ids":[1,2],"copy_ids":[]}"""),
                Triple(emptySet<Long>(), setOf(5L), """{"file_ids":[],"copy_ids":[5]}"""),
                Triple(emptySet<Long>(), emptySet<Long>(), """{"file_ids":[],"copy_ids":[]}"""),
            )
            for ((files, copies, expected) in bodies) {
                script.answer("POST", reply(200, """{"book_deleted":false}"""))
                repository.deleteItems(UUID, files, copies)

                val request = nextRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe DELETE_PATH
                request.headers["Authorization"] shouldBe "Bearer secret"
                request.headers["Content-Type"].orEmpty() shouldContain "application/json"
                request.body?.utf8() shouldBe expected
            }
            server.requestCount shouldBe bodies.size
        }

        it("#121 TC-22: a 2xx answer maps its ids and book_deleted, a missing body reads as nothing deleted, and unknown keys are ignored") {
            val repository = repositoryFor()

            script.answer("POST", reply(200, """{"deleted_file_ids":[1,2],"deleted_copy_ids":[5],"book_deleted":true}"""))
            repository.deleteItems(UUID, setOf(1, 2), setOf(5)) shouldBe
                DeleteItemsResult.Deleted(deletedFileIds = listOf(1, 2), deletedCopyIds = listOf(5), bookDeleted = true)

            script.answer("POST", reply(200, """{"deleted_file_ids":[3],"deleted_copy_ids":[],"book_deleted":false,"extra":{"x":1}}"""))
            repository.deleteItems(UUID, setOf(3), emptySet()) shouldBe
                DeleteItemsResult.Deleted(deletedFileIds = listOf(3), deletedCopyIds = emptyList(), bookDeleted = false)

            script.answer("POST", reply(200, "{}"))
            repository.deleteItems(UUID, emptySet(), emptySet()) shouldBe
                DeleteItemsResult.Deleted(deletedFileIds = emptyList(), deletedCopyIds = emptyList(), bookDeleted = false)
        }

        it("#121 TC-24: every failed delete maps to its error, and nothing is thrown") {
            val failures = listOf(
                reply(401) to DeletionError.SessionExpired,
                reply(403) to DeletionError.Forbidden,
                reply(404) to DeletionError.BookNotFound,
                reply(422) to DeletionError.ItemNotFound,
                reply(500) to DeletionError.Http(500),
                disconnect() to DeletionError.Unreachable,
            )
            for ((failure, error) in failures) {
                script.answer("POST", failure)
                repositoryFor().deleteItems(UUID, setOf(1), emptySet()) shouldBe DeleteItemsResult.Failed(error)
            }
        }

        it("#121 TC-25: a 2xx delete that does not decode, and a missing server, fail the delete without throwing") {
            script.answer("POST", reply(200, "not json"))
            repositoryFor().deleteItems(UUID, setOf(1), emptySet()) shouldBe
                DeleteItemsResult.Failed(DeletionError.UnexpectedResponse)

            script.answer("POST", reply(200, """{"deleted_file_ids":3}"""))
            repositoryFor().deleteItems(UUID, setOf(1), emptySet()) shouldBe
                DeleteItemsResult.Failed(DeletionError.UnexpectedResponse)

            baseUrlProvider.baseUrl = null
            val before = server.requestCount
            repositoryFor().deleteItems(UUID, setOf(1), emptySet()) shouldBe DeleteItemsResult.Failed(DeletionError.NoServer)
            server.requestCount shouldBe before
            baseUrlProvider.baseUrl = server.url("/")
        }

        it("#121 TC-26: cancelling the caller leaves the POST to finish in the application scope, and the caller gets a cancellation") {
            val applicationScope = newScope(Dispatchers.Default)
            val repository = repositoryFor(held = true, applicationScope = applicationScope)
            val gate = CountDownLatch(1)
            script.answer("POST", reply(200, """{"deleted_file_ids":[1],"deleted_copy_ids":[],"book_deleted":false}"""), gate)

            var result: DeleteItemsResult? = null
            val caller = newScope(Dispatchers.Default).launch {
                result = repository.deleteItems(UUID, setOf(1), emptySet())
            }
            nextRequest()
            val pending: List<Job> = applicationScope.coroutineContext.job.children.toList()
            caller.cancelAndJoin()
            gate.countDown()

            // The caller never received a result, so it was cancelled rather than handed a Failed.
            result shouldBe null
            withTimeout(5_000) { pending.forEach { it.join() } }
            pending.none { it.isCancelled } shouldBe true
            server.requestCount shouldBe 1
        }
    }
})
