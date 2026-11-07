package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.export.local.ExportFileStore
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.TEST_RANDOM
import com.omnibus.omnibus.testing.dropped
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

private const val SEND = "/api/kindle/send"
private const val STATUS = "/api/kindle/send/status"
private const val ATTACH_P_M4B = "attachment; filename=\"p.m4b\""
private const val MANIFEST_3 = """{"mode":"direct","parts":[{"ordinal":1},{"ordinal":2},{"ordinal":3}]}"""
private const val ONE_MIB = 1024 * 1024
private const val PENDING = """{"status":"pending"}"""
private const val SENT = """{"status":"sent"}"""
private const val TASK_7 = """{"task_id":7}"""

private fun kepubPath(uuid: String) = "/api/ebooks/$uuid/kepub"

private fun ebookPath(uuid: String) = "/api/ebooks/$uuid/download"

private fun manifestPath(uuid: String) = "/api/audiobooks/$uuid/manifest"

private fun audioPath(uuid: String) = "/api/audiobooks/$uuid/download"

private fun randomFileBytes(): ByteArray = randomString().toByteArray()

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

/** A test server that records every request and answers through [respond], which a test replaces. */
private class RecordingServer(private val server: MockWebServer) {
    val requests = ConcurrentLinkedQueue<RecordedRequest>()

    @Volatile
    var respond: (RecordedRequest) -> MockResponse = { reply(404) }

    val url: HttpUrl get() = server.url("/")

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests.add(request)
                return respond(request)
            }
        }
    }

    /** Answers by `METHOD encodedPath`. A request not in [routes] gets a 404. */
    fun on(vararg routes: Pair<String, () -> MockResponse>) {
        val table = routes.toMap()
        respond = { request -> table["${request.method} ${request.url.encodedPath}"]?.invoke() ?: reply(404) }
    }
}

private inline fun <T> withServer(block: (RecordingServer) -> T): T =
    withMockServer { server -> block(RecordingServer(server)) }

/** Runs [block] with a [TestExportFileStore] under a temp directory, removed afterwards. */
private inline fun <T> withStore(usable: Long = Long.MAX_VALUE, block: (TestExportFileStore) -> T): T {
    val directory = Files.createTempDirectory("export-repository").toFile()
    return try {
        block(TestExportFileStore(File(directory, "store"), usable))
    } finally {
        directory.deleteRecursively()
    }
}

private inline fun <T> withServerAndStore(block: (RecordingServer, TestExportFileStore) -> T): T =
    withServer { server -> withStore { store -> block(server, store) } }

/** Runs a request with a read timeout short enough for [slowHeaders] to trip it. */
private fun shortTimeoutExecute(): suspend (Request) -> Response {
    val client = OkHttpClient.Builder().readTimeout(200, TimeUnit.MILLISECONDS).build()
    return { client.newCall(it).execute() }
}

private fun slowHeaders(): MockResponse = file(randomFileBytes()).newBuilder().headersDelay(2, TimeUnit.SECONDS).build()

/** The two ways of saving an ebook, which fail alike. */
private enum class EbookSave { Kepub, Ebook }

private suspend fun ExportRepository.save(kind: EbookSave, uuid: String): ExportSaveResult = when (kind) {
    EbookSave.Kepub -> saveKepub(uuid, randomString())
    EbookSave.Ebook -> saveEbook(uuid, randomString(), "epub", expectedBytes = 0)
}

private fun pathOf(kind: EbookSave, uuid: String): String = when (kind) {
    EbookSave.Kepub -> kepubPath(uuid)
    EbookSave.Ebook -> ebookPath(uuid)
}

private fun failedSaves(): List<Triple<EbookSave, Int, ExportSaveResult>> =
    EbookSave.entries.flatMap { kind ->
        listOf(
            Triple(kind, 403, ExportSaveResult.Forbidden),
            Triple(kind, 404, ExportSaveResult.NotFound),
            Triple(kind, 500, ExportSaveResult.Failed),
        )
    }

class DefaultExportRepositoryTest : DescribeSpec({

    fun sut(
        baseUrl: HttpUrl? = null,
        fileStore: ExportFileStore = TestExportFileStore(File(System.getProperty("java.io.tmpdir"), randomString())),
        timings: ExportTimings = ExportTimings(pollIntervalMillis = 1, pollBudgetMillis = 50, kepubAttempts = 2),
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
        execute: (suspend (Request) -> Response)? = null,
    ): DefaultExportRepository {
        val client = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        )
        return DefaultExportRepository(
            ioDispatcher = dispatcher,
            omnibusHttpClient = client,
            fileStore = fileStore,
            timings = timings,
            execute = execute ?: { client.execute(it) },
        )
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("sending a book to Kindle") {
        it("#118 TC-36: POSTs the book as JSON") {
            withServer { server ->
                val uuid = randomString()
                server.on("POST $SEND" to { reply(200, TASK_7) }, "GET $STATUS" to { reply(200, SENT) })

                sut(server.url).sendToKindle(uuid)

                val posted = server.requests.first()
                posted.method shouldBe "POST"
                posted.url.encodedPath shouldBe SEND
                posted.body!!.utf8() shouldBe """{"book_uuid":"$uuid"}"""
                posted.headers["Content-Type"]!! shouldStartWith "application/json"
            }
        }

        context("when the send is pending for a while") {
            it("#118 TC-36: polls the task until it is sent") {
                withServer { server ->
                    server.on(
                        "POST $SEND" to { reply(200, TASK_7) },
                        "GET $STATUS" to seq(reply(200, PENDING), reply(200, PENDING), reply(200, SENT)),
                    )

                    sut(server.url).sendToKindle(randomString()) shouldBe KindleSendResult.Sent

                    server.requests.drop(1).map { it.url.queryParameter("task_id") } shouldBe listOf("7", "7", "7")
                }
            }
        }

        context("when the send stays pending") {
            it("#118 TC-38: is unconfirmed once the poll budget is spent") {
                withServer { server ->
                    server.on("POST $SEND" to { reply(200, TASK_7) }, "GET $STATUS" to { reply(200, PENDING) })

                    sut(server.url).sendToKindle(randomString()) shouldBe KindleSendResult.Unconfirmed
                }
            }
        }

        context("when the send was queued") {
            it("#118 TC-37: maps each status answer to its outcome") {
                withServer { server ->
                    val cases = listOf(
                        reply(200, """{"status":"failed","message":"Mailbox rejected"}""") to
                            KindleSendResult.Rejected("Mailbox rejected"),
                        reply(200, """{"status":"failed","message":"  "}""") to KindleSendResult.Rejected(null),
                        reply(200, """{"status":"failed"}""") to KindleSendResult.Rejected(null),
                        reply(200, """{"status":"failed","message":null}""") to KindleSendResult.Rejected(null),
                        reply(404) to KindleSendResult.Unconfirmed,
                        dropped() to KindleSendResult.Unconfirmed,
                        reply(500, "boom") to KindleSendResult.Failed,
                        reply(200, """{"status":"weird"}""") to KindleSendResult.Failed,
                        reply(200, "not json") to KindleSendResult.Failed,
                    )
                    checkAll(Exhaustive.collection(cases)) { (status, expected) ->
                        server.on("POST $SEND" to { reply(200, TASK_7) }, "GET $STATUS" to { status })

                        sut(server.url).sendToKindle(randomString()) shouldBe expected
                    }
                }
            }
        }

        context("when the send can't be queued") {
            it("#118 TC-39: maps each answer to its result") {
                withServer { server ->
                    val cases = listOf(
                        reply(409, "email delivery is not configured on this server") to KindleSendResult.NotConfigured,
                        reply(422, "add a Kindle address") to KindleSendResult.NoAddress,
                        reply(404) to KindleSendResult.NotFound,
                        reply(500, "boom") to KindleSendResult.Failed,
                        reply(401, "") to KindleSendResult.Failed,
                        reply(200, "not json") to KindleSendResult.Failed,
                        dropped() to KindleSendResult.Unreachable,
                    )
                    checkAll(Exhaustive.collection(cases)) { (answer, expected) ->
                        server.on("POST $SEND" to { answer })

                        sut(server.url).sendToKindle(randomString()) shouldBe expected
                    }
                }
            }
        }

        context("when no server is saved") {
            it("#118 TC-39: returns NoServer") {
                sut().sendToKindle(randomString()) shouldBe KindleSendResult.NoServer
            }
        }
    }

    describe("saving a KEPUB") {
        it("#118 TC-41: names the file for what the server sent, a KEPUB or its EPUB fallback") {
            withServer { server ->
                val cases = listOf("x.kepub.epub" to "kepub.epub", "x.epub" to "epub")
                checkAll(Exhaustive.collection(cases)) { (served, extension) ->
                    withStore { store ->
                        val uuid = randomString()
                        val title = randomString()
                        server.on(
                            "GET ${kepubPath(uuid)}" to {
                                file(randomFileBytes(), "Content-Disposition" to "attachment; filename=\"$served\"")
                            },
                        )

                        val saved = sut(server.url, store).saveKepub(uuid, title)
                            .shouldBeInstanceOf<ExportSaveResult.Saved>()

                        saved.files.single().file.name shouldBe "$title.$extension"
                    }
                }
            }
        }

        context("when a request times out before its headers") {
            it("#118 TC-42: retries") {
                withServerAndStore { server, store ->
                    val uuid = randomString()
                    server.on("GET ${kepubPath(uuid)}" to seq(slowHeaders(), file(randomFileBytes())))

                    sut(server.url, store, execute = shortTimeoutExecute()).saveKepub(uuid, randomString())
                        .shouldBeInstanceOf<ExportSaveResult.Saved>()
                }
            }

            context("when every attempt times out") {
                it("#118 TC-42: gives up as unreachable") {
                    withServerAndStore { server, store ->
                        val uuid = randomString()
                        val response = slowHeaders()
                        server.on("GET ${kepubPath(uuid)}" to { response })

                        sut(server.url, store, execute = shortTimeoutExecute()).saveKepub(uuid, randomString()) shouldBe
                            ExportSaveResult.Unreachable
                    }
                }
            }
        }
    }

    describe("saving an ebook") {
        context("when the save is refused") {
            it("#118 TC-43: maps 403, 404 and 500 for the KEPUB and the ebook") {
                withServer { server ->
                    checkAll(Exhaustive.collection(failedSaves())) { (kind, code, expected) ->
                        withStore { store ->
                            val uuid = randomString()
                            server.on("GET ${pathOf(kind, uuid)}" to { reply(code) })

                            sut(server.url, store).save(kind, uuid) shouldBe expected
                        }
                    }
                }
            }

            it("#118 TC-43: deletes the folder after 403, 404 and 500 for the KEPUB and the ebook") {
                withServer { server ->
                    checkAll(Exhaustive.collection(failedSaves())) { (kind, code, _) ->
                        withStore { store ->
                            val uuid = randomString()
                            server.on("GET ${pathOf(kind, uuid)}" to { reply(code) })

                            sut(server.url, store).save(kind, uuid)

                            store.folders.single().exists() shouldBe false
                        }
                    }
                }
            }
        }

        it("#118 TC-44: names the file from the server's extension, else the fallback") {
            withServer { server ->
                val cases = listOf(
                    "attachment; filename*=UTF-8''Caf%C3%A9.epub" to "epub",
                    "attachment; filename=\"x.PDF\"" to "pdf",
                    null to "m4b",
                )
                checkAll(Exhaustive.collection(cases)) { (disposition, extension) ->
                    withStore { store ->
                        val uuid = randomString()
                        val title = randomString()
                        val headers = listOfNotNull(disposition?.let { "Content-Disposition" to it }).toTypedArray()
                        server.on("GET ${ebookPath(uuid)}" to { file(randomFileBytes(), *headers) })

                        val saved = sut(server.url, store).saveEbook(uuid, title, "m4b", expectedBytes = 0)
                            .shouldBeInstanceOf<ExportSaveResult.Saved>()

                        saved.files.single().file.name shouldBe "$title.$extension"
                    }
                }
            }
        }

        it("#118 TC-44: reports the MIME type the server sent") {
            withServerAndStore { server, store ->
                val uuid = randomString()
                server.on("GET ${ebookPath(uuid)}" to { file(randomFileBytes(), "Content-Type" to "application/pdf") })

                val saved = sut(server.url, store).saveEbook(uuid, randomString(), "epub", expectedBytes = 0)
                    .shouldBeInstanceOf<ExportSaveResult.Saved>()

                saved.files.single().mimeType shouldBe "application/pdf"
            }
        }

        it("#118 TC-45: infers the MIME type from the extension, for missing and octet-stream headers") {
            val cases = listOf(
                Triple(null, "a.epub", "application/epub+zip"),
                Triple(null, "Dune.kepub.epub", "application/epub+zip"),
                Triple(null, "a.pdf", "application/pdf"),
                Triple(null, "a.m4b", "audio/mp4"),
                Triple(null, "a.m4a", "audio/mp4"),
                Triple(null, "a.mp3", "audio/mpeg"),
                Triple(null, "a.xyz", "application/octet-stream"),
                Triple("application/octet-stream", "a.epub", "application/epub+zip"),
            )
            checkAll(Exhaustive.collection(cases)) { (contentType, name, expected) ->
                exportMimeType(contentType, name) shouldBe expected
            }
        }

        context("when the connection drops") {
            it("#118 TC-46: is unreachable, before the response and mid-body") {
                withServer { server ->
                    val answers = listOf(
                        dropped(),
                        MockResponse.Builder()
                            .code(200)
                            .body(Buffer().write(ByteArray(300 * 1024)))
                            .onResponseBody(SocketEffect.CloseSocket())
                            .build(),
                    )
                    checkAll(Exhaustive.collection(answers)) { answer ->
                        withStore { store ->
                            val uuid = randomString()
                            server.on("GET ${ebookPath(uuid)}" to { answer })

                            sut(server.url, store).saveEbook(uuid, randomString(), "epub", expectedBytes = 0) shouldBe
                                ExportSaveResult.Unreachable
                        }
                    }
                }
            }
        }

        context("when the download would not fit") {
            it("#118 TC-47: refuses before any request, far short and one byte short of the size plus 8 MiB") {
                withServer { server ->
                    val size = randomLong(1L..1_000_000_000L)
                    val cases = listOf(1L, size + 8 * ONE_MIB - 1)
                    checkAll(Exhaustive.collection(cases)) { usable ->
                        withStore(usable) { store ->
                            server.requests.clear()

                            sut(server.url, store).saveEbook(randomString(), randomString(), "epub", size) shouldBe
                                ExportSaveResult.NoSpace

                            server.requests.size shouldBe 0
                        }
                    }
                }
            }
        }

        context("when the download would just fit") {
            it("#118 TC-47: downloads it, for an exact fit and an unknown size") {
                withServer { server ->
                    val size = randomLong(1L..1_000_000_000L)
                    val cases = listOf(size to size + 8 * ONE_MIB, 0L to 1L)
                    checkAll(Exhaustive.collection(cases)) { (expectedBytes, usable) ->
                        withStore(usable) { store ->
                            val uuid = randomString()
                            server.on("GET ${ebookPath(uuid)}" to { file(randomFileBytes()) })

                            sut(server.url, store).saveEbook(uuid, randomString(), "epub", expectedBytes)
                                .shouldBeInstanceOf<ExportSaveResult.Saved>()
                        }
                    }
                }
            }
        }

        context("when the body is larger than one chunk") {
            it("#118 TC-48: writes it byte for byte") {
                withServerAndStore { server, store ->
                    val uuid = randomString()
                    val body = TEST_RANDOM.nextBytes(2 * ONE_MIB + 17)
                    server.on("GET ${ebookPath(uuid)}" to { file(body) })

                    val saved = sut(server.url, store).saveEbook(uuid, randomString(), "epub", expectedBytes = 0)
                        .shouldBeInstanceOf<ExportSaveResult.Saved>()

                    saved.files.single().file.readBytes().contentEquals(body) shouldBe true
                }
            }
        }

        context("when the download is cancelled") {
            it("#118 TC-49: deletes the folder") {
                withServerAndStore { server, store ->
                    val uuid = randomString()
                    server.on(
                        "GET ${ebookPath(uuid)}" to {
                            MockResponse.Builder()
                                .code(200)
                                .body(Buffer().write(ByteArray(ONE_MIB) { 1 }))
                                .throttleBody(16L * 1024, 50, TimeUnit.MILLISECONDS)
                                .build()
                        },
                    )

                    coroutineScope {
                        val job = async(Dispatchers.IO) {
                            sut(server.url, store, dispatcher = Dispatchers.IO)
                                .saveEbook(uuid, randomString(), "epub", expectedBytes = 0)
                        }
                        waitUntil { store.folders.singleOrNull()?.listFiles().orEmpty().isNotEmpty() }
                        job.cancelAndJoin()
                    }

                    store.folders.single().exists() shouldBe false
                }
            }
        }

        it("#118 TC-50: sends the uuid as one encoded path segment") {
            withServerAndStore { server, store ->
                server.respond = { file(randomFileBytes()) }

                sut(server.url, store).saveEbook("a b/c", randomString(), "epub", expectedBytes = 0)

                server.requests.single().url.encodedPath shouldBe "/api/ebooks/a%20b%2Fc/download"
            }
        }

        context("when no server is saved") {
            it("#118 TC-50: returns NoServer") {
                withStore { store ->
                    sut(fileStore = store).saveEbook(randomString(), randomString(), "epub", expectedBytes = 0) shouldBe
                        ExportSaveResult.NoServer
                }
            }
        }
    }

    describe("saving an audiobook") {
        context("when the manifest has several parts") {
            /** Serves each part's own number as its bytes, so a file shows which part it holds. */
            fun RecordingServer.serveParts(uuid: String) {
                respond = { request ->
                    if (request.url.encodedPath == manifestPath(uuid)) {
                        reply(200, MANIFEST_3)
                    } else {
                        file(request.url.queryParameter("part")!!.toByteArray(), "Content-Disposition" to ATTACH_P_M4B)
                    }
                }
            }

            it("#118 TC-51: saves each part in manifest order, numbered by position") {
                withServerAndStore { server, store ->
                    val uuid = randomString()
                    val title = randomString()
                    server.serveParts(uuid)

                    val saved = sut(server.url, store).saveAudiobook(uuid, title, "m4b", expectedBytes = 0)
                        .shouldBeInstanceOf<ExportSaveResult.Saved>()

                    saved.files.map { it.file.name to it.file.readText() } shouldBe listOf(
                        "$title - part 01.m4b" to "1",
                        "$title - part 02.m4b" to "2",
                        "$title - part 03.m4b" to "3",
                    )
                }
            }

            it("#118 TC-51: reads the manifest, then requests each part") {
                withServerAndStore { server, store ->
                    val uuid = randomString()
                    server.serveParts(uuid)

                    sut(server.url, store).saveAudiobook(uuid, randomString(), "m4b", expectedBytes = 0)

                    server.requests.map { it.target() } shouldBe listOf(
                        manifestPath(uuid),
                        "${audioPath(uuid)}?part=1",
                        "${audioPath(uuid)}?part=2",
                        "${audioPath(uuid)}?part=3",
                    )
                }
            }
        }

        context("when the book is one file") {
            it("#118 TC-52: makes one request with no part query, for a single part and an hls book") {
                withServer { server ->
                    val manifests = listOf("""{"mode":"direct","parts":[{"ordinal":1}]}""", """{"mode":"hls"}""")
                    checkAll(Exhaustive.collection(manifests)) { manifest ->
                        withStore { store ->
                            val uuid = randomString()
                            val title = randomString()
                            server.requests.clear()
                            server.on(
                                "GET ${manifestPath(uuid)}" to { reply(200, manifest) },
                                "GET ${audioPath(uuid)}" to { file(randomFileBytes(), "Content-Disposition" to ATTACH_P_M4B) },
                            )

                            val saved = sut(server.url, store).saveAudiobook(uuid, title, "m4b", expectedBytes = 0)
                                .shouldBeInstanceOf<ExportSaveResult.Saved>()

                            saved.files.single().file.name shouldBe "$title.m4b"
                            server.requests.map { it.target() } shouldBe listOf(manifestPath(uuid), audioPath(uuid))
                        }
                    }
                }
            }
        }

        context("when the manifest fails") {
            it("#118 TC-53: maps each status and unreadable body, before any audio request") {
                withServer { server ->
                    val cases = listOf(
                        reply(404) to ExportSaveResult.NotFound,
                        reply(403, "") to ExportSaveResult.Forbidden,
                        reply(500, "boom") to ExportSaveResult.Failed,
                        reply(200, "not json") to ExportSaveResult.Failed,
                    )
                    checkAll(Exhaustive.collection(cases)) { (manifest, expected) ->
                        withStore { store ->
                            val uuid = randomString()
                            server.on("GET ${manifestPath(uuid)}" to { manifest }, "GET ${audioPath(uuid)}" to { file(randomFileBytes()) })

                            sut(server.url, store).saveAudiobook(uuid, randomString(), "m4b", expectedBytes = 0) shouldBe expected

                            server.requests.none { it.url.encodedPath == audioPath(uuid) } shouldBe true
                        }
                    }
                }
            }
        }

        context("when a part fails") {
            /** Serves the manifest and part 1, and answers part 2 with a 404. */
            fun RecordingServer.failSecondPart(uuid: String) {
                respond = { request ->
                    when {
                        request.url.encodedPath == manifestPath(uuid) -> reply(200, MANIFEST_3)
                        request.url.queryParameter("part") == "2" -> reply(404)
                        else -> file(randomFileBytes(), "Content-Disposition" to ATTACH_P_M4B)
                    }
                }
            }

            it("#118 TC-54: returns that part's failure") {
                withServerAndStore { server, store ->
                    val uuid = randomString()
                    server.failSecondPart(uuid)

                    sut(server.url, store).saveAudiobook(uuid, randomString(), "m4b", expectedBytes = 0) shouldBe
                        ExportSaveResult.NotFound
                }
            }

            it("#118 TC-54: never requests the next part") {
                withServerAndStore { server, store ->
                    val uuid = randomString()
                    server.failSecondPart(uuid)

                    sut(server.url, store).saveAudiobook(uuid, randomString(), "m4b", expectedBytes = 0)

                    server.requests.none { it.url.queryParameter("part") == "3" } shouldBe true
                }
            }

            it("#118 TC-54: deletes the parts already saved") {
                withServerAndStore { server, store ->
                    val uuid = randomString()
                    server.failSecondPart(uuid)

                    sut(server.url, store).saveAudiobook(uuid, randomString(), "m4b", expectedBytes = 0)

                    store.folders.single().exists() shouldBe false
                }
            }
        }
    }
})
