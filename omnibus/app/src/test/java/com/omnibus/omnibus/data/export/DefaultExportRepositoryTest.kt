package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

private const val SEND = "/api/kindle/send"
private const val STATUS = "/api/kindle/send/status"
private const val KEPUB = "/api/ebooks/uuid-1/kepub"
private const val EBOOK = "/api/ebooks/uuid-1/download"
private const val MANIFEST = "/api/audiobooks/uuid-1/manifest"
private const val AUDIO = "/api/audiobooks/uuid-1/download"
private const val TASK_7 = """{"task_id":7}"""
private const val PENDING = """{"status":"pending"}"""
private const val SENT = """{"status":"sent"}"""
private val EPUB_BYTES = "epub-bytes".toByteArray()
private const val MANIFEST_3 = """{"mode":"direct","parts":[{"ordinal":1},{"ordinal":2},{"ordinal":3}]}"""
private const val ATTACH_P_M4B = "attachment; filename=\"p.m4b\""
private const val ONE_MIB = 1024 * 1024

private fun json(code: Int, body: String): MockResponse = MockResponse.Builder().code(code).body(body).build()

private fun notFound(): MockResponse = MockResponse.Builder().code(404).build()

/** Closes the socket before any response, so the client sees a dropped connection. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** A 200 response carrying [body] and the given headers. */
private fun file(body: ByteArray, vararg headers: Pair<String, String>): MockResponse {
    val builder = MockResponse.Builder().code(200)
    headers.forEach { (name, value) -> builder.addHeader(name, value) }
    return builder.body(Buffer().write(body)).build()
}

/** Answers successive calls with each response in turn, then keeps answering with the last. */
private fun seq(vararg responses: MockResponse): () -> MockResponse {
    val queue = ArrayDeque(responses.toList())
    return { if (queue.size > 1) queue.removeFirst() else queue.first() }
}

/** The request target as the client wrote it: encoded path plus query. */
private fun RecordedRequest.target(): String = url.encodedPath + url.encodedQuery?.let { "?$it" }.orEmpty()

private suspend fun waitUntil(condition: () -> Boolean) {
    withTimeout(5_000) {
        while (!condition()) delay(10)
    }
}

private suspend fun saveVia(kind: String, repository: ExportRepository): ExportSaveResult =
    if (kind == "kepub") {
        repository.saveKepub("uuid-1", "Dune")
    } else {
        repository.saveEbook("uuid-1", "Dune", "epub", expectedBytes = 0)
    }

class DefaultExportRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var baseUrlProvider: TestServerBaseUrlProvider
    lateinit var client: OmnibusHttpClient
    lateinit var tempDir: File
    lateinit var store: TestExportFileStore

    /** Every request the server receives, in arrival order. */
    val requests = ConcurrentLinkedQueue<RecordedRequest>()

    /** Answers the next request. Tests replace it. */
    var respond: (RecordedRequest) -> MockResponse = { notFound() }

    /** Answers by `METHOD encodedPath`. A request not in [routes] gets a 404. */
    fun on(vararg routes: Pair<String, () -> MockResponse>) {
        val table = routes.toMap()
        respond = { request -> table["${request.method} ${request.url.encodedPath}"]?.invoke() ?: notFound() }
    }

    fun freshStore(usable: Long = Long.MAX_VALUE): TestExportFileStore {
        store = TestExportFileStore(File(tempDir, "store-${System.nanoTime()}"), usable)
        return store
    }

    fun repo(
        timings: ExportTimings = ExportTimings(pollIntervalMillis = 1, pollBudgetMillis = 50, kepubAttempts = 2),
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        execute: (suspend (Request) -> Response)? = null,
    ): DefaultExportRepository = DefaultExportRepository(
        ioDispatcher = dispatcher,
        omnibusHttpClient = client,
        fileStore = store,
        timings = timings,
        execute = execute ?: { client.execute(it) },
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        tempDir = Files.createTempDirectory("export-repository").toFile()
        server = MockWebServer().apply { start() }
        requests.clear()
        respond = { notFound() }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return respond(request)
            }
        }
        baseUrlProvider = TestServerBaseUrlProvider(baseUrl = server.url("/"))
        client = OmnibusHttpClient(
            baseUrlProvider = baseUrlProvider,
            authTokenProvider = TestAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        )
        freshStore()
    }

    afterEach {
        server.close()
        tempDir.deleteRecursively()
    }

    describe("sendToKindle") {

        it("#118 TC-36: sends the book as JSON, then polls pending, pending, sent to Sent") {
            on(
                "POST $SEND" to { json(200, TASK_7) },
                "GET $STATUS" to seq(json(200, PENDING), json(200, PENDING), json(200, SENT)),
            )

            repo().sendToKindle("uuid-1") shouldBe KindleSendResult.Sent

            val posted = requests.first()
            posted.method shouldBe "POST"
            posted.url.encodedPath shouldBe SEND
            posted.body!!.utf8() shouldBe """{"book_uuid":"uuid-1"}"""
            posted.headers["Content-Type"]!! shouldStartWith "application/json"
            val polls = requests.drop(1)
            polls.size shouldBe 3
            polls.forEach { poll ->
                poll.method shouldBe "GET"
                poll.url.encodedPath shouldBe STATUS
                poll.url.queryParameter("task_id") shouldBe "7"
            }
        }

        it("#118 TC-37: maps each status answer after a good send to its outcome") {
            val cases: List<Triple<String, () -> MockResponse, KindleSendResult>> = listOf(
                Triple("failed with a message", { json(200, """{"status":"failed","message":"Mailbox rejected"}""") }, KindleSendResult.Rejected("Mailbox rejected")),
                Triple("failed with a blank message", { json(200, """{"status":"failed","message":"  "}""") }, KindleSendResult.Rejected(null)),
                Triple("failed with no message", { json(200, """{"status":"failed"}""") }, KindleSendResult.Rejected(null)),
                Triple("failed with a null message", { json(200, """{"status":"failed","message":null}""") }, KindleSendResult.Rejected(null)),
                Triple("status 404", { notFound() }, KindleSendResult.Unconfirmed),
                Triple("socket closed", { dropped() }, KindleSendResult.Unconfirmed),
                Triple("status 500", { json(500, "boom") }, KindleSendResult.Failed),
                Triple("unknown status", { json(200, """{"status":"weird"}""") }, KindleSendResult.Failed),
                Triple("body that is not JSON", { json(200, "not json") }, KindleSendResult.Failed),
            )
            cases.forEach { (name, status, expected) ->
                requests.clear()
                on("POST $SEND" to { json(200, TASK_7) }, "GET $STATUS" to status)
                withClue(name) {
                    repo().sendToKindle("uuid-1") shouldBe expected
                }
            }
        }

        it("#118 TC-38: a send that stays pending is unconfirmed after no more than the poll budget allows") {
            on(
                "POST $SEND" to { json(200, TASK_7) },
                "GET $STATUS" to { json(200, PENDING) },
            )

            repo(ExportTimings(pollIntervalMillis = 1, pollBudgetMillis = 50, kepubAttempts = 2))
                .sendToKindle("uuid-1") shouldBe KindleSendResult.Unconfirmed

            val polls = requests.count { it.url.encodedPath == STATUS }
            withClue("status requests=$polls") {
                (polls in 1..50) shouldBe true
            }
        }

        it("#118 TC-39: maps each queue answer to its result, and sends nothing without a server") {
            val cases: List<Triple<String, (() -> MockResponse)?, KindleSendResult>> = listOf(
                Triple("HTTP 409", { json(409, "email delivery is not configured on this server") }, KindleSendResult.NotConfigured),
                Triple("HTTP 422", { json(422, "add a Kindle address") }, KindleSendResult.NoAddress),
                Triple("HTTP 404", { notFound() }, KindleSendResult.NotFound),
                Triple("HTTP 500", { json(500, "boom") }, KindleSendResult.Failed),
                Triple("HTTP 401", { json(401, "") }, KindleSendResult.Failed),
                Triple("a 200 body that is not JSON", { json(200, "not json") }, KindleSendResult.Failed),
                Triple("a dropped connection", { dropped() }, KindleSendResult.Unreachable),
                Triple("no server saved", null, KindleSendResult.NoServer),
            )
            cases.forEach { (name, answer, expected) ->
                requests.clear()
                baseUrlProvider.baseUrl = if (answer == null) null else server.url("/")
                if (answer != null) on("POST $SEND" to answer)
                withClue(name) {
                    repo().sendToKindle("uuid-1") shouldBe expected
                    if (answer == null) requests.size shouldBe 0
                }
            }
        }

        it("#118 TC-40: cancelling while the send is being polled rethrows the cancellation") {
            on(
                "POST $SEND" to { json(200, TASK_7) },
                "GET $STATUS" to { json(200, PENDING) },
            )

            coroutineScope {
                val job = async(Dispatchers.IO) {
                    repo(ExportTimings(pollIntervalMillis = 20, pollBudgetMillis = 10_000, kepubAttempts = 2))
                        .sendToKindle("uuid-1")
                }
                waitUntil { requests.any { it.url.encodedPath == STATUS } }
                job.cancel()
                shouldThrow<CancellationException> { job.await() }
            }
        }
    }

    describe("saveKepub") {

        it("#118 TC-41: saves the KEPUB under its server name, and under the EPUB name when the server falls back") {
            on(
                "GET $KEPUB" to {
                    file(
                        EPUB_BYTES,
                        "Content-Type" to "application/epub+zip",
                        "Content-Disposition" to "attachment; filename=\"uuid-1.kepub.epub\"",
                    )
                },
            )

            val saved = repo().saveKepub("uuid-1", "Dune").shouldBeInstanceOf<ExportSaveResult.Saved>()
            val exported = saved.files.single()
            exported.file.name shouldBe "Dune.kepub.epub"
            exported.mimeType shouldBe "application/epub+zip"
            withClue("bytes") { exported.file.readBytes().contentEquals(EPUB_BYTES) shouldBe true }
            exported.file.parentFile shouldBe store.folders.single()
            requests.single().url.encodedPath shouldBe KEPUB
            requests.single().headers["Accept"] shouldBe "*/*"

            requests.clear()
            freshStore()
            on(
                "GET $KEPUB" to {
                    file(
                        EPUB_BYTES,
                        "Content-Type" to "application/epub+zip",
                        "Content-Disposition" to "attachment; filename=\"uuid-1.epub\"",
                    )
                },
            )
            val fallback = repo().saveKepub("uuid-1", "Dune").shouldBeInstanceOf<ExportSaveResult.Saved>()
            fallback.files.single().file.name shouldBe "Dune.epub"
        }

        it("#118 TC-42: retries a KEPUB request that times out before its headers, and gives up as unreachable") {
            val shortClient = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
            val slowOk = file(EPUB_BYTES, "Content-Disposition" to "attachment; filename=\"uuid-1.kepub.epub\"")
                .newBuilder()
                .headersDelay(2, TimeUnit.SECONDS)
                .build()
            val execute: suspend (Request) -> Response = { shortClient.newCall(it).execute() }

            on("GET $KEPUB" to seq(slowOk, file(EPUB_BYTES, "Content-Disposition" to "attachment; filename=\"uuid-1.kepub.epub\"")))
            repo(execute = execute).saveKepub("uuid-1", "Dune").shouldBeInstanceOf<ExportSaveResult.Saved>()
            requests.size shouldBe 2

            requests.clear()
            freshStore()
            on("GET $KEPUB" to { slowOk })
            repo(execute = execute).saveKepub("uuid-1", "Dune") shouldBe ExportSaveResult.Unreachable
            requests.size shouldBe 2
            store.folders.single().exists() shouldBe false
        }
    }

    describe("saves that fail") {

        it("#118 TC-43: maps 403, 404 and 500 for the KEPUB and the ebook, and deletes the folder each time") {
            val statuses: List<Pair<Int, ExportSaveResult>> = listOf(
                403 to ExportSaveResult.Forbidden,
                404 to ExportSaveResult.NotFound,
                500 to ExportSaveResult.Failed,
            )
            for (kind in listOf("kepub", "ebook")) {
                val path = if (kind == "kepub") KEPUB else EBOOK
                statuses.forEach { (code, expected) ->
                    requests.clear()
                    freshStore()
                    on("GET $path" to { json(code, "") })
                    withClue("$kind HTTP $code") {
                        saveVia(kind, repo()) shouldBe expected
                        store.folders.single().exists() shouldBe false
                    }
                }
            }
        }
    }

    describe("saveEbook") {

        it("#118 TC-44: names the ebook from the server's file name, its extension, or the fallback") {
            on("GET $EBOOK" to { file(EPUB_BYTES, "Content-Disposition" to "attachment; filename*=UTF-8''Caf%C3%A9.epub") })
            val first = repo().saveEbook("uuid-1", "Dune", "epub", expectedBytes = 0)
                .shouldBeInstanceOf<ExportSaveResult.Saved>().files.single()
            first.file.name shouldBe "Dune.epub"
            requests.single().url.encodedPath shouldBe EBOOK

            requests.clear()
            freshStore()
            on(
                "GET $EBOOK" to {
                    file(
                        EPUB_BYTES,
                        "Content-Type" to "application/pdf",
                        "Content-Disposition" to "attachment; filename=\"x.PDF\"",
                    )
                },
            )
            val pdf = repo().saveEbook("uuid-1", "Dune", "epub", expectedBytes = 0)
                .shouldBeInstanceOf<ExportSaveResult.Saved>().files.single()
            pdf.file.name shouldBe "Dune.pdf"
            pdf.mimeType shouldBe "application/pdf"

            requests.clear()
            freshStore()
            on("GET $EBOOK" to { file(EPUB_BYTES) })
            val fallback = repo().saveEbook("uuid-1", "Dune", "pdf", expectedBytes = 0)
                .shouldBeInstanceOf<ExportSaveResult.Saved>().files.single()
            fallback.file.name shouldBe "Dune.pdf"
        }

        it("#118 TC-45: infers the MIME type from the header or the extension, treating octet-stream as unknown") {
            val cases: List<Triple<String?, String, String>> = listOf(
                Triple(null, "a.epub", "application/epub+zip"),
                Triple(null, "Dune.kepub.epub", "application/epub+zip"),
                Triple(null, "a.pdf", "application/pdf"),
                Triple(null, "a.m4b", "audio/mp4"),
                Triple(null, "a.m4a", "audio/mp4"),
                Triple(null, "a.mp3", "audio/mpeg"),
                Triple(null, "a.xyz", "application/octet-stream"),
                Triple("application/octet-stream", "a.epub", "application/epub+zip"),
            )
            cases.forEach { (contentType, name, expected) ->
                withClue("contentType=$contentType name=$name") {
                    exportMimeType(contentType, name) shouldBe expected
                }
            }
        }

        it("#118 TC-46: a connection dropped before the response or mid-body is unreachable, and the folder is deleted") {
            val answers: List<() -> MockResponse> = listOf(
                { dropped() },
                {
                    MockResponse.Builder()
                        .code(200)
                        .body(Buffer().write(ByteArray(300 * 1024)))
                        .onResponseBody(SocketEffect.CloseSocket())
                        .build()
                },
            )
            answers.forEach { answer ->
                requests.clear()
                freshStore()
                on("GET $EBOOK" to answer)
                repo().saveEbook("uuid-1", "Dune", "epub", expectedBytes = 0) shouldBe ExportSaveResult.Unreachable
                store.folders.single().exists() shouldBe false
            }
        }

        it("#118 TC-47: refuses a download that would not fit, before any request, and allows one that fits") {
            data class SpaceCase(
                val name: String,
                val usable: Long,
                val audio: Boolean,
                val expectedBytes: Long,
                val expected: ExportSaveResult,
                val served: Int,
            )
            val e = 1_000_000L
            val cases = listOf(
                SpaceCase("ebook 100 MB, 10 MB free", 10_000_000, false, 100_000_000, ExportSaveResult.NoSpace, 0),
                SpaceCase("unknown size, 1 byte free", 1, false, 0, ExportSaveResult.Saved(emptyList()), 1),
                SpaceCase("size plus 8 MiB exactly free", e + 8 * ONE_MIB, false, e, ExportSaveResult.Saved(emptyList()), 1),
                SpaceCase("one byte short of size plus 8 MiB", e + 8 * ONE_MIB - 1, false, e, ExportSaveResult.NoSpace, 0),
                SpaceCase("audiobook 100 MB, 10 MB free", 10_000_000, true, 100_000_000, ExportSaveResult.NoSpace, 0),
            )
            on(
                "GET $EBOOK" to { file(EPUB_BYTES, "Content-Disposition" to "attachment; filename=\"a.epub\"") },
                "GET $MANIFEST" to { json(200, MANIFEST_3) },
            )
            cases.forEach { case ->
                requests.clear()
                freshStore(usable = case.usable)
                withClue(case.name) {
                    val result = if (case.audio) {
                        repo().saveAudiobook("uuid-1", "Dune", "m4b", case.expectedBytes)
                    } else {
                        repo().saveEbook("uuid-1", "Dune", "epub", case.expectedBytes)
                    }
                    result::class shouldBe case.expected::class
                    requests.size shouldBe case.served
                }
            }
        }

        it("#118 TC-48: streams a body larger than one chunk byte for byte, and keeps the folder with the file") {
            val body = Random(48).nextBytes(2 * ONE_MIB + 17)
            on("GET $EBOOK" to { file(body, "Content-Disposition" to "attachment; filename=\"a.epub\"") })

            val saved = repo().saveEbook("uuid-1", "Dune", "epub", expectedBytes = 0)
                .shouldBeInstanceOf<ExportSaveResult.Saved>()
            val exported = saved.files.single()
            withClue("bytes") { exported.file.readBytes().contentEquals(body) shouldBe true }
            exported.file.exists() shouldBe true
            exported.file.parentFile shouldBe store.folders.single()
        }

        it("#118 TC-49: cancelling a throttled download rethrows the cancellation and deletes the folder") {
            val body = ByteArray(ONE_MIB) { 1 }
            on(
                "GET $EBOOK" to {
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Disposition", "attachment; filename=\"a.epub\"")
                        .body(Buffer().write(body))
                        .throttleBody(16L * 1024, 50, TimeUnit.MILLISECONDS)
                        .build()
                },
            )

            coroutineScope {
                val job = async(Dispatchers.IO) {
                    repo(dispatcher = Dispatchers.IO).saveEbook("uuid-1", "Dune", "epub", expectedBytes = 0)
                }
                waitUntil { store.folders.singleOrNull()?.listFiles().orEmpty().isNotEmpty() }
                job.cancel()
                shouldThrow<CancellationException> { job.await() }
            }
            store.folders.single().exists() shouldBe false
        }

        it("#118 TC-50: encodes the uuid as one path segment, and sends nothing without a server") {
            respond = { file(EPUB_BYTES, "Content-Disposition" to "attachment; filename=\"a.epub\"") }

            repo().saveEbook("a b/c", "Dune", "epub", expectedBytes = 0)
                .shouldBeInstanceOf<ExportSaveResult.Saved>()
            requests.single().url.encodedPath shouldBe "/api/ebooks/a%20b%2Fc/download"

            requests.clear()
            baseUrlProvider.baseUrl = null
            repo().saveEbook("a b/c", "Dune", "epub", expectedBytes = 0) shouldBe ExportSaveResult.NoServer
            requests.size shouldBe 0
        }
    }

    describe("saveAudiobook") {

        it("#118 TC-51: downloads each part in manifest order, numbered by position") {
            respond = { request ->
                if (request.url.encodedPath == MANIFEST) {
                    json(200, MANIFEST_3)
                } else {
                    val part = request.url.queryParameter("part")!!
                    file(part.toByteArray(), "Content-Type" to "audio/mp4", "Content-Disposition" to ATTACH_P_M4B)
                }
            }

            val saved = repo().saveAudiobook("uuid-1", "Dune", "m4b", expectedBytes = 0)
                .shouldBeInstanceOf<ExportSaveResult.Saved>()
            saved.files.map { it.file.name } shouldBe listOf(
                "Dune - part 01.m4b",
                "Dune - part 02.m4b",
                "Dune - part 03.m4b",
            )
            saved.files.map { it.file.readText() } shouldBe listOf("1", "2", "3")
            requests.map { it.target() } shouldBe listOf(
                MANIFEST,
                "$AUDIO?part=1",
                "$AUDIO?part=2",
                "$AUDIO?part=3",
            )
        }

        it("#118 TC-52: a single-part or hls book makes one request for the whole book, with no part query") {
            val cases: List<Triple<String, String, () -> MockResponse>> = listOf(
                Triple(
                    "direct, one part",
                    """{"mode":"direct","parts":[{"ordinal":1}]}""",
                    { file(EPUB_BYTES, "Content-Disposition" to ATTACH_P_M4B) },
                ),
                Triple(
                    "hls",
                    """{"mode":"hls"}""",
                    { file(EPUB_BYTES, "Content-Disposition" to ATTACH_P_M4B) },
                ),
                Triple(
                    "hls with no Content-Disposition",
                    """{"mode":"hls"}""",
                    { file(EPUB_BYTES) },
                ),
            )
            cases.forEach { (name, manifest, audio) ->
                requests.clear()
                freshStore()
                on(
                    "GET $MANIFEST" to { json(200, manifest) },
                    "GET $AUDIO" to audio,
                )
                withClue(name) {
                    val file = repo().saveAudiobook("uuid-1", "Dune", "m4b", expectedBytes = 0)
                        .shouldBeInstanceOf<ExportSaveResult.Saved>().files.single()
                    file.file.name shouldBe "Dune.m4b"
                    requests.map { it.target() } shouldBe listOf(MANIFEST, AUDIO)
                }
            }
        }

        it("#118 TC-53: a failed manifest fails the download before any audio request, and deletes the folder") {
            val cases: List<Triple<String, () -> MockResponse, ExportSaveResult>> = listOf(
                Triple("HTTP 404", { notFound() }, ExportSaveResult.NotFound),
                Triple("HTTP 403", { json(403, "") }, ExportSaveResult.Forbidden),
                Triple("HTTP 500", { json(500, "boom") }, ExportSaveResult.Failed),
                Triple("a body that is not JSON", { json(200, "not json") }, ExportSaveResult.Failed),
            )
            cases.forEach { (name, manifest, expected) ->
                requests.clear()
                freshStore()
                on("GET $MANIFEST" to manifest, "GET $AUDIO" to { file(EPUB_BYTES) })
                withClue(name) {
                    repo().saveAudiobook("uuid-1", "Dune", "m4b", expectedBytes = 0) shouldBe expected
                    requests.none { it.url.encodedPath == AUDIO } shouldBe true
                    store.folders.single().exists() shouldBe false
                }
            }
        }

        it("#118 TC-54: a part that fails stops the download, never requests the next part, and deletes the folder") {
            respond = { request ->
                when {
                    request.url.encodedPath == MANIFEST -> json(200, MANIFEST_3)
                    request.url.queryParameter("part") == "2" -> notFound()
                    else -> file(EPUB_BYTES, "Content-Disposition" to ATTACH_P_M4B)
                }
            }

            repo().saveAudiobook("uuid-1", "Dune", "m4b", expectedBytes = 0) shouldBe ExportSaveResult.NotFound
            requests.map { it.target() } shouldBe listOf(MANIFEST, "$AUDIO?part=1", "$AUDIO?part=2")
            store.folders.single().exists() shouldBe false
        }

        it("#118 TC-55: cancelling during a part rethrows the cancellation, deletes the folder, and requests no later part") {
            respond = { request ->
                when {
                    request.url.encodedPath == MANIFEST -> json(200, MANIFEST_3)
                    request.url.queryParameter("part") == "2" -> MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Disposition", ATTACH_P_M4B)
                        .body(Buffer().write(ByteArray(ONE_MIB) { 2 }))
                        .throttleBody(16L * 1024, 50, TimeUnit.MILLISECONDS)
                        .build()
                    else -> file(EPUB_BYTES, "Content-Disposition" to ATTACH_P_M4B)
                }
            }

            coroutineScope {
                val job = async(Dispatchers.IO) {
                    repo(dispatcher = Dispatchers.IO).saveAudiobook("uuid-1", "Dune", "m4b", expectedBytes = 0)
                }
                waitUntil { store.folders.singleOrNull()?.listFiles().orEmpty().any { it.name.contains("part 02") } }
                job.cancel()
                shouldThrow<CancellationException> { job.await() }
            }
            store.folders.single().exists() shouldBe false
            requests.none { it.url.queryParameter("part") == "3" } shouldBe true
        }
    }
})
