package com.omnibus.omnibus.data.readstatus

import app.cash.turbine.test
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
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
import kotlinx.serialization.json.Json
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

/** This record as the server writes it, with its times in seconds. */
private fun ReadStatusRecord.toBody(): String = listOfNotNull(
    "\"status\":\"${status.name.lowercase()}\"",
    updatedAtMillis?.let { "\"updated_at\":${it / 1_000L}" },
    finishedAtMillis?.let { "\"finished_at\":${it / 1_000L}" },
).joinToString(prefix = "{", postfix = "}", separator = ",")

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

private fun reply(record: ReadStatusRecord): MockResponse = reply(200, record.toBody())

/** A connection dropped before any response, which the client reports as an IOException. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** A random record of [status], so a test states only the status it depends on. */
private fun recordOf(status: ReadStatus): ReadStatusRecord = randomReadStatusRecord().copy(status = status)

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
private suspend fun ReadStatusRepository.awaitState(uuid: String, expected: ReadStatusState) {
    withTimeout(5_000) { getReadStatusFlow(uuid).first { it == expected } }
}

class DefaultReadStatusRepositoryTest : DescribeSpec({

    // By default everything runs unconfined on the calling thread, so the state has settled when a call returns.
    fun sut(
        baseUrl: HttpUrl? = null,
        now: Long = randomLong(),
        applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        ioDispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ): DefaultReadStatusRepository = DefaultReadStatusRepository(
        ioDispatcher = ioDispatcher,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = TestAuthTokenProvider(),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        timeSource = TimeSource { now },
        applicationScope = applicationScope,
    )

    /** A [sut] that runs HTTP on real threads, which lets a test hold a request open. */
    fun TestServer.threadedSut(now: Long = randomLong()): DefaultReadStatusRepository =
        sut(url, now, scope, Dispatchers.IO)

    /** Reads [record] for [uuid], so its state is Known, and takes that request from the server's queue. */
    suspend fun TestServer.load(repository: ReadStatusRepository, uuid: String, record: ReadStatusRecord) {
        script.answer("GET", reply(record))
        repository.fetchStatus(uuid)
        nextRequest()
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading the status") {

        context("when the server has a record") {

            it("TC-1, TC-2: maps its times from seconds to milliseconds") {
                withServer {
                    val record = randomReadStatusRecord().copy(
                        updatedAtMillis = randomLong(0L..2_000_000_000L) * 1_000L,
                        finishedAtMillis = randomLong(0L..2_000_000_000L) * 1_000L,
                    )
                    script.answer("GET", reply(record))

                    sut(url).fetchStatus(randomString()) shouldBe ReadStatusState.Known(record)
                }
            }
        }

        context("when the server has no record") {

            it("TC-3: reads as a known Unread") {
                withServer {
                    script.answer("GET", reply(200, "null"))

                    sut(url).fetchStatus(randomString()) shouldBe
                        ReadStatusState.Known(ReadStatusRecord(ReadStatus.Unread, null, null))
                }
            }
        }

        it("TC-4: reads a token regardless of its case, reading anything else as Unread") {
            withServer {
                checkAll(
                    Exhaustive.of(
                        """{"status":"abandoned"}""" to ReadStatus.Unread,
                        "{}" to ReadStatus.Unread,
                        """{"status":"FINISHED"}""" to ReadStatus.Finished,
                    ),
                ) { (body, status) ->
                    script.answer("GET", reply(200, body))

                    sut(url).fetchStatus(randomString()) shouldBe
                        ReadStatusState.Known(ReadStatusRecord(status, null, null))
                }
            }
        }

        it("TC-5, TC-6, TC-8: is Failed regardless of how the read goes wrong") {
            withServer {
                checkAll(
                    Exhaustive.of(
                        reply(401),
                        reply(404),
                        reply(500),
                        dropped(),
                        reply(200, "not json"),
                        reply(200, "[1,2]"),
                        reply(200, """{"status":5}"""),
                    ),
                ) { response ->
                    script.answer("GET", response)

                    sut(url).fetchStatus(randomString()) shouldBe ReadStatusState.Failed
                }
            }
        }

        context("when no server is saved") {

            it("TC-7: is Failed") {
                sut().fetchStatus(randomString()) shouldBe ReadStatusState.Failed
            }
        }

        context("when the uuid is blank") {

            it("TC-9: fails without asking the server, regardless of the blank") {
                withServer {
                    checkAll(Exhaustive.of("", "   ")) { uuid ->
                        sut(url).fetchStatus(uuid) shouldBe ReadStatusState.Failed

                        server.requestCount shouldBe 0
                    }
                }
            }
        }

        it("TC-10: asks for the book with its uuid as one encoded path segment") {
            withServer {
                script.answer("GET", reply(randomReadStatusRecord()))

                sut(url).fetchStatus("book one")

                nextRequest().url.encodedPath shouldBe "/api/read-status/book%20one"
            }
        }

        context("when the read fails after an earlier answer") {

            it("TC-13: keeps the earlier answer as the state") {
                withServer {
                    val uuid = randomString()
                    val record = randomReadStatusRecord()
                    val repository = sut(url)
                    load(repository, uuid, record)
                    script.answer("GET", reply(500))

                    repository.refresh(uuid)

                    repository.getReadStatusFlow(uuid).first() shouldBe ReadStatusState.Known(record)
                }
            }
        }

        context("when a read is in flight") {

            it("TC-12: shares it with another caller") {
                withServer {
                    val uuid = randomString()
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(randomReadStatusRecord()), gate)
                    val repository = threadedSut()

                    val first = scope.async(start = CoroutineStart.UNDISPATCHED) { repository.fetchStatus(uuid) }
                    nextRequest()
                    val second = scope.async(start = CoroutineStart.UNDISPATCHED) { repository.fetchStatus(uuid) }
                    gate.countDown()
                    listOf(first, second).awaitAll()

                    server.requestCount shouldBe 1
                }
            }
        }

        context("when the last read has completed") {

            it("TC-12: reads again on a refresh") {
                withServer {
                    val uuid = randomString()
                    val latest = randomReadStatusRecord()
                    val repository = sut(url)
                    load(repository, uuid, randomReadStatusRecord())
                    script.answer("GET", reply(latest))

                    repository.refresh(uuid)

                    repository.getReadStatusFlow(uuid).first() shouldBe ReadStatusState.Known(latest)
                }
            }
        }
    }

    describe("observing the status") {

        context("when nothing is known") {

            it("TC-11: emits Unknown, then the server's answer") {
                withServer {
                    val record = randomReadStatusRecord()
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(record), gate)

                    threadedSut().getReadStatusFlow(randomString()).test {
                        awaitItem() shouldBe ReadStatusState.Unknown
                        gate.countDown()
                        awaitItem() shouldBe ReadStatusState.Known(record)
                    }
                }
            }
        }

        context("when the status is already known") {

            it("TC-11: reads nothing more from the server") {
                withServer {
                    val uuid = randomString()
                    val repository = sut(url)
                    load(repository, uuid, randomReadStatusRecord())

                    repository.getReadStatusFlow(uuid).first()

                    server.requestCount shouldBe 1
                }
            }
        }

        context("when the first read fails") {

            it("TC-14: moves to Failed, then recovers on a refresh") {
                withServer {
                    val uuid = randomString()
                    val record = randomReadStatusRecord()
                    val gate = CountDownLatch(1)
                    script.answer("GET", reply(500), gate)
                    val repository = threadedSut()

                    repository.getReadStatusFlow(uuid).test {
                        awaitItem() shouldBe ReadStatusState.Unknown
                        gate.countDown()
                        awaitItem() shouldBe ReadStatusState.Failed

                        script.answer("GET", reply(record))
                        repository.refresh(uuid)
                        awaitItem() shouldBe ReadStatusState.Known(record)
                    }
                }
            }
        }
    }

    describe("setting the status") {

        it("TC-15: sends the lowercase token for the book as JSON, regardless of the status") {
            withServer {
                checkAll(Exhaustive.enum<ReadStatus>()) { status ->
                    val uuid = randomString()
                    script.answer("PUT", reply(recordOf(status)))

                    sut(url).setStatus(uuid, status)

                    val request = nextRequest()
                    request.method shouldBe "PUT"
                    request.url.encodedPath shouldBe "/api/read-status"
                    request.headers["Content-Type"]!!.shouldStartWith("application/json")
                    Json.parseToJsonElement(request.body!!.utf8()) shouldBe
                        Json.parseToJsonElement("""{"book_uuid":"$uuid","status":"${status.name.lowercase()}"}""")
                }
            }
        }

        context("when the uuid is blank") {

            it("TC-16: fails with BookNotFound without asking the server") {
                withServer {
                    sut(url).setStatus("", randomEnum()) shouldBe ReadStatusWriteResult.Failed(ReadStatusError.BookNotFound)

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when the server is still answering") {

            it("TC-17: shows the new status with the time now") {
                withServer {
                    val uuid = randomString()
                    val now = randomLong()
                    val gate = CountDownLatch(1)
                    val repository = threadedSut(now)
                    load(repository, uuid, recordOf(ReadStatus.Unread))
                    script.answer("PUT", reply(recordOf(ReadStatus.Reading)), gate)

                    val write = scope.async { repository.setStatus(uuid, ReadStatus.Reading) }

                    repository.awaitState(
                        uuid,
                        ReadStatusState.Known(ReadStatusRecord(ReadStatus.Reading, now, null)),
                    )
                    gate.countDown()
                    write.await()
                }
            }

            context("when moving out of Finished") {

                it("TC-20: clears the finish time regardless of the new status") {
                    withServer {
                        checkAll(Exhaustive.of(ReadStatus.Reading, ReadStatus.Unread)) { status ->
                            val uuid = randomString()
                            val now = randomLong()
                            val gate = CountDownLatch(1)
                            val repository = threadedSut(now)
                            load(repository, uuid, recordOf(ReadStatus.Finished).copy(finishedAtMillis = randomLong()))
                            script.answer("PUT", reply(recordOf(status)), gate)

                            val write = scope.async { repository.setStatus(uuid, status) }

                            repository.awaitState(uuid, ReadStatusState.Known(ReadStatusRecord(status, now, null)))
                            gate.countDown()
                            write.await()
                            nextRequest()
                        }
                    }
                }
            }

            context("when moving into Finished") {

                it("TC-21: takes the time now as the finish time") {
                    withServer {
                        val uuid = randomString()
                        val now = randomLong()
                        val gate = CountDownLatch(1)
                        val repository = threadedSut(now)
                        load(repository, uuid, recordOf(ReadStatus.Unread))
                        script.answer("PUT", reply(recordOf(ReadStatus.Finished)), gate)

                        val write = scope.async { repository.setStatus(uuid, ReadStatus.Finished) }

                        repository.awaitState(
                            uuid,
                            ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, now, now)),
                        )
                        gate.countDown()
                        write.await()
                    }
                }
            }
        }

        context("when the server accepts the write") {

            it("TC-18: replaces the shown status with the server's record") {
                withServer {
                    val uuid = randomString()
                    val record = randomReadStatusRecord()
                    script.answer("PUT", reply(record))
                    val repository = sut(url)

                    repository.setStatus(uuid, record.status)

                    repository.getReadStatusFlow(uuid).first() shouldBe ReadStatusState.Known(record)
                }
            }
        }

        context("when the status is already the known one") {

            it("TC-22: answers Unchanged without asking the server, regardless of the status") {
                withServer {
                    checkAll(Exhaustive.enum<ReadStatus>()) { status ->
                        val uuid = randomString()
                        val repository = sut(url)
                        load(repository, uuid, recordOf(status))
                        val requests = server.requestCount

                        repository.setStatus(uuid, status) shouldBe ReadStatusWriteResult.Unchanged

                        server.requestCount shouldBe requests
                    }
                }
            }
        }

        context("when nothing is known yet") {

            it("TC-23: sends the write regardless of the status") {
                withServer {
                    checkAll(Exhaustive.enum<ReadStatus>()) { status ->
                        val record = recordOf(status)
                        script.answer("PUT", reply(record))

                        sut(url).setStatus(randomString(), status) shouldBe ReadStatusWriteResult.Saved(record)
                    }
                }
            }
        }

        context("when the write fails") {

            it("TC-24: reports each failure as its own error") {
                withServer {
                    checkAll(
                        Exhaustive.of(
                            dropped() to ReadStatusError.Unreachable,
                            reply(400) to ReadStatusError.Http(400),
                            reply(401) to ReadStatusError.SessionExpired,
                            reply(404) to ReadStatusError.BookNotFound,
                            reply(500) to ReadStatusError.Http(500),
                            reply(200, "garbage") to ReadStatusError.UnexpectedResponse,
                            reply(200, "null") to ReadStatusError.UnexpectedResponse,
                        ),
                    ) { (response, error) ->
                        script.answer("PUT", response)

                        sut(url).setStatus(randomString(), randomEnum()) shouldBe ReadStatusWriteResult.Failed(error)
                    }
                }
            }

            it("TC-25: shows the new status, then restores the exact previous record") {
                withServer {
                    val uuid = randomString()
                    val now = randomLong()
                    val previous = recordOf(ReadStatus.Finished)
                    val repository = sut(url, now)
                    load(repository, uuid, previous)
                    script.answer("PUT", reply(500))

                    repository.getReadStatusFlow(uuid).test {
                        awaitItem() shouldBe ReadStatusState.Known(previous)

                        repository.setStatus(uuid, ReadStatus.Unread)

                        awaitItem() shouldBe ReadStatusState.Known(ReadStatusRecord(ReadStatus.Unread, now, null))
                        awaitItem() shouldBe ReadStatusState.Known(previous)
                    }
                }
            }
        }

        context("when no server is saved") {

            it("TC-24: fails with NoServer") {
                sut().setStatus(randomString(), randomEnum()) shouldBe
                    ReadStatusWriteResult.Failed(ReadStatusError.NoServer)
            }
        }

        context("when a newer write is pending") {

            context("when the older write succeeds") {

                it("TC-26: keeps the newer write's value") {
                    withServer {
                        val uuid = randomString()
                        val now = randomLong()
                        val optimistic = ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, now, now))
                        val gateA = CountDownLatch(1)
                        val gateB = CountDownLatch(1)
                        val repository = threadedSut(now)
                        load(repository, uuid, recordOf(ReadStatus.Unread))
                        script.answer("PUT", reply(recordOf(ReadStatus.Reading)), gateA)
                        script.answer("PUT", reply(recordOf(ReadStatus.Finished)), gateB)

                        val a = scope.async { repository.setStatus(uuid, ReadStatus.Reading) }
                        nextRequest()
                        val b = scope.async { repository.setStatus(uuid, ReadStatus.Finished) }
                        repository.awaitState(uuid, optimistic)
                        gateA.countDown()
                        a.await()

                        repository.getReadStatusFlow(uuid).first() shouldBe optimistic
                        gateB.countDown()
                        b.await()
                    }
                }
            }

            context("when the older write fails") {

                it("TC-27: keeps the newer write's value") {
                    withServer {
                        val uuid = randomString()
                        val now = randomLong()
                        val optimistic = ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, now, now))
                        val gateA = CountDownLatch(1)
                        val gateB = CountDownLatch(1)
                        val repository = threadedSut(now)
                        load(repository, uuid, recordOf(ReadStatus.Unread))
                        script.answer("PUT", reply(500), gateA)
                        script.answer("PUT", reply(recordOf(ReadStatus.Finished)), gateB)

                        val a = scope.async { repository.setStatus(uuid, ReadStatus.Reading) }
                        nextRequest()
                        val b = scope.async { repository.setStatus(uuid, ReadStatus.Finished) }
                        repository.awaitState(uuid, optimistic)
                        gateA.countDown()
                        a.await()

                        repository.getReadStatusFlow(uuid).first() shouldBe optimistic
                        gateB.countDown()
                        b.await()
                    }
                }
            }

            it("TC-28: rolls back to the older write's record once the newer one fails") {
                withServer {
                    val uuid = randomString()
                    val confirmed = recordOf(ReadStatus.Reading)
                    val gateA = CountDownLatch(1)
                    val repository = threadedSut()
                    load(repository, uuid, recordOf(ReadStatus.Unread))
                    script.answer("PUT", reply(confirmed), gateA)
                    script.answer("PUT", reply(500))

                    val a = scope.async { repository.setStatus(uuid, ReadStatus.Reading) }
                    nextRequest()
                    val b = scope.async { repository.setStatus(uuid, ReadStatus.Finished) }
                    gateA.countDown()
                    listOf(a, b).awaitAll()

                    repository.getReadStatusFlow(uuid).first() shouldBe ReadStatusState.Known(confirmed)
                }
            }

            it("TC-29: holds the newer write back until the older one is answered") {
                withServer {
                    val uuid = randomString()
                    val gateA = CountDownLatch(1)
                    val repository = threadedSut()
                    script.answer("PUT", reply(recordOf(ReadStatus.Reading)), gateA)
                    script.answer("PUT", reply(recordOf(ReadStatus.Finished)))

                    val a = scope.async { repository.setStatus(uuid, ReadStatus.Reading) }
                    nextRequest()
                    val b = scope.async { repository.setStatus(uuid, ReadStatus.Finished) }

                    // There is no event for "a request was not sent", so this waits a bounded time for one to arrive.
                    server.takeRequest(300, TimeUnit.MILLISECONDS).shouldBeNull()
                    gateA.countDown()
                    listOf(a, b).awaitAll()
                }
            }
        }

        context("when a read started before the write") {

            it("TC-30: keeps the write's record as the state") {
                withServer {
                    val uuid = randomString()
                    val written = recordOf(ReadStatus.Finished)
                    val gate = CountDownLatch(1)
                    val repository = threadedSut()
                    script.answer("GET", reply(recordOf(ReadStatus.Unread)), gate)
                    script.answer("PUT", reply(written))

                    val fetch = scope.async { repository.fetchStatus(uuid) }
                    nextRequest()
                    repository.setStatus(uuid, ReadStatus.Finished)
                    gate.countDown()
                    fetch.await()

                    repository.getReadStatusFlow(uuid).first() shouldBe ReadStatusState.Known(written)
                }
            }
        }

        context("when a read lands during the write") {

            it("TC-103: keeps showing the write's value") {
                withServer {
                    val uuid = randomString()
                    val now = randomLong()
                    val optimistic = ReadStatusState.Known(ReadStatusRecord(ReadStatus.Reading, now, null))
                    val readGate = CountDownLatch(1)
                    val writeGate = CountDownLatch(1)
                    val repository = threadedSut(now)
                    script.answer("GET", reply(recordOf(ReadStatus.Finished)), readGate)
                    script.answer("PUT", reply(500), writeGate)

                    val fetch = scope.async { repository.fetchStatus(uuid) }
                    nextRequest()
                    val write = scope.async { repository.setStatus(uuid, ReadStatus.Reading) }
                    nextRequest()
                    readGate.countDown()
                    fetch.await()

                    repository.getReadStatusFlow(uuid).first() shouldBe optimistic
                    writeGate.countDown()
                    write.await()
                }
            }

            it("TC-103: falls back to the read's record once the write fails") {
                withServer {
                    val uuid = randomString()
                    val read = recordOf(ReadStatus.Finished)
                    val readGate = CountDownLatch(1)
                    val writeGate = CountDownLatch(1)
                    val repository = threadedSut()
                    script.answer("GET", reply(read), readGate)
                    script.answer("PUT", reply(500), writeGate)

                    val fetch = scope.async { repository.fetchStatus(uuid) }
                    nextRequest()
                    val write = scope.async { repository.setStatus(uuid, ReadStatus.Reading) }
                    nextRequest()
                    readGate.countDown()
                    fetch.await()
                    writeGate.countDown()
                    write.await()

                    repository.getReadStatusFlow(uuid).first() shouldBe ReadStatusState.Known(read)
                }
            }
        }

        context("when the caller is cancelled") {

            it("TC-31: lets the write finish") {
                withServer {
                    val uuid = randomString()
                    val record = recordOf(ReadStatus.Finished)
                    val gate = CountDownLatch(1)
                    val repository = threadedSut()
                    script.answer("PUT", reply(record), gate)

                    val caller = scope.launch { repository.setStatus(uuid, ReadStatus.Finished) }
                    nextRequest()
                    caller.cancelAndJoin()
                    gate.countDown()

                    repository.awaitState(uuid, ReadStatusState.Known(record))
                }
            }
        }

        context("when another book has a known status") {

            it("TC-32: leaves that book's status alone") {
                withServer {
                    val other = randomString()
                    val record = randomReadStatusRecord()
                    val repository = sut(url)
                    load(repository, other, record)
                    script.answer("PUT", reply(randomReadStatusRecord()))

                    repository.setStatus(randomString(), randomEnum())

                    repository.getReadStatusFlow(other).first() shouldBe ReadStatusState.Known(record)
                }
            }
        }
    }
})
