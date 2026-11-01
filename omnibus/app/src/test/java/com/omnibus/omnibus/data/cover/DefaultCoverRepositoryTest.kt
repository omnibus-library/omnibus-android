package com.omnibus.omnibus.data.cover

import coil3.Image
import coil3.memory.MemoryCache
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.FakeClientInfo
import com.omnibus.omnibus.data.cover.local.CachedCover
import com.omnibus.omnibus.data.cover.local.FakeCoverDiskStore
import com.omnibus.omnibus.data.network.FakeAuthTokenProvider
import com.omnibus.omnibus.data.network.FakeServerBaseUrlProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okio.ByteString.Companion.encodeUtf8
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class DefaultCoverRepositoryTest : DescribeSpec({

    val window = CoverCacheKeys.REVALIDATE_AFTER_MILLIS
    val startMillis = 1_000_000_000L

    lateinit var server: MockWebServer
    lateinit var diskStore: FakeCoverDiskStore
    lateinit var memoryCache: MemoryCache
    lateinit var tokenProvider: FakeAuthTokenProvider
    lateinit var baseUrlProvider: FakeServerBaseUrlProvider
    var now = startMillis
    lateinit var repository: DefaultCoverRepository

    fun enqueue(code: Int, body: String = "", etag: String? = null) {
        server.enqueue(
            MockResponse.Builder().code(code).body(body).apply { etag?.let { addHeader("ETag", it) } }.build(),
        )
    }

    fun seed(uuid: String, size: CoverSize, body: String, etag: String?, storedAt: Long = now) {
        diskStore.entries[CoverCacheKeys.diskKey(uuid, size)] =
            CachedCover(body.encodeUtf8(), etag, storedAt)
    }

    fun cached(uuid: String, size: CoverSize) = diskStore.entries[CoverCacheKeys.diskKey(uuid, size)]

    fun found(body: String, size: CoverSize, fromCache: Boolean) =
        CoverImageResult.Found(body.encodeUtf8(), size, fromCache)

    /**
     * Fetches [uuid] at [size] while the server holds its 200 [body] response, runs [whileInFlight]
     * once the request has reached the server, then releases the response and returns the result.
     */
    suspend fun fetchHeldOpen(uuid: String, size: CoverSize, body: String, whileInFlight: suspend () -> Unit) =
        coroutineScope {
            val arrived = CountDownLatch(1)
            val release = CountDownLatch(1)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    arrived.countDown()
                    release.await(10, TimeUnit.SECONDS)
                    return MockResponse.Builder().code(200).body(body).build()
                }
            }
            val fetch = async(Dispatchers.IO) { repository.thumbnail(uuid, size) }
            withContext(Dispatchers.IO) { arrived.await(10, TimeUnit.SECONDS) } shouldBe true
            whileInFlight()
            release.countDown()
            fetch.await()
        }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        server = MockWebServer().apply { start() }
        diskStore = FakeCoverDiskStore()
        memoryCache = MemoryCache.Builder().maxSizeBytes(1024 * 1024).build()
        tokenProvider = FakeAuthTokenProvider(token = "secret")
        baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = server.url("/"))
        now = startMillis
        repository = DefaultCoverRepository(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = OmnibusHttpClient(
                baseUrlProvider = baseUrlProvider,
                authTokenProvider = tokenProvider,
                omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
                authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
            ),
            diskStore = diskStore,
            memoryCache = memoryCache,
            timeSource = TimeSource { now },
        )
    }

    afterEach { server.close() }

    describe("thumbnail with nothing cached") {

        it("TC-147: fetches the requested size with the bearer token and image Accept header") {
            enqueue(200, "md-bytes", etag = "\"v1\"")

            repository.thumbnail("book", CoverSize.Md) shouldBe found("md-bytes", CoverSize.Md, fromCache = false)

            val request = server.takeRequest()
            request.url.encodedPath shouldBe "/api/thumbs/book/md"
            request.headers["Authorization"] shouldBe "Bearer secret"
            request.headers["Accept"]!!.startsWith("image/") shouldBe true
            request.headers["If-None-Match"].shouldBeNull()
        }

        it("TC-148: stores the bytes with their validator and fetch time") {
            enqueue(200, "md-bytes", etag = "\"v1\"")

            repository.thumbnail("book", CoverSize.Md)

            cached("book", CoverSize.Md) shouldBe CachedCover("md-bytes".encodeUtf8(), "\"v1\"", startMillis)
        }

        it("TC-149: stores a response without a validator as validator-less") {
            enqueue(200, "md-bytes")

            repository.thumbnail("book", CoverSize.Md)

            cached("book", CoverSize.Md)!!.etag.shouldBeNull()
        }

        it("TC-150: fails when no server is saved") {
            baseUrlProvider.baseUrl = null

            repository.thumbnail("book", CoverSize.Md) shouldBe CoverImageResult.Error
            server.requestCount shouldBe 0
        }

        it("TC-151: fails on a server error") {
            enqueue(500)

            repository.thumbnail("book", CoverSize.Md) shouldBe CoverImageResult.Error
            server.requestCount shouldBe 1
        }

        it("TC-152: ends the session on a 401") {
            enqueue(401)

            repository.thumbnail("book", CoverSize.Md) shouldBe CoverImageResult.Error
            tokenProvider.invalidateCalls shouldBe 1
        }

        it("TC-153: fails when the server can't be reached") {
            server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())

            repository.thumbnail("book", CoverSize.Md) shouldBe CoverImageResult.Error
        }

        it("TC-154: still returns the bytes when they can't be cached") {
            diskStore.writeFailure = IOException("disk full")
            enqueue(200, "md-bytes")

            repository.thumbnail("book", CoverSize.Md) shouldBe found("md-bytes", CoverSize.Md, fromCache = false)
        }
    }

    describe("thumbnail when the cache misbehaves") {

        it("TC-175: treats a 304 with no cached copy as a failure and stores nothing") {
            enqueue(304)

            repository.thumbnail("book", CoverSize.Md) shouldBe CoverImageResult.Error

            server.requestCount shouldBe 1
            diskStore.entries.keys.toList().shouldBeEmpty()
        }

        it("TC-176: loads from the server when the cache can't be read") {
            diskStore.readFailure = IOException("corrupt entry")
            enqueue(200, "md-bytes", etag = "\"v1\"")

            repository.thumbnail("book", CoverSize.Md) shouldBe found("md-bytes", CoverSize.Md, fromCache = false)
        }

        it("TC-177: reports an error, rather than throwing, when the cache can't be read and the server is down") {
            diskStore.readFailure = IOException("corrupt entry")
            enqueue(500)

            repository.thumbnail("book", CoverSize.Md) shouldBe CoverImageResult.Error
        }

        it("TC-178: reports NotFound when every size is gone, even if one was cached and stale") {
            seed("book", CoverSize.Md, "old", etag = "\"v1\"", storedAt = now - window)
            repeat(3) { enqueue(404) }

            repository.thumbnail("book", CoverSize.Md) shouldBe CoverImageResult.NotFound

            cached("book", CoverSize.Md).shouldBeNull()
        }

        it("TC-180: rethrows cancellation from the cache write instead of swallowing it") {
            diskStore.writeFailure = CancellationException("cancelled")
            enqueue(200, "md-bytes")

            shouldThrow<CancellationException> {
                repository.thumbnail("book", CoverSize.Md)
            }
        }
    }

    describe("thumbnail size fall-back") {

        it("TC-155: tries the other sizes largest first when the requested one is missing") {
            enqueue(404)
            enqueue(404)
            enqueue(200, "sm-bytes")

            repository.thumbnail("book", CoverSize.Md) shouldBe found("sm-bytes", CoverSize.Sm, fromCache = false)

            List(3) { server.takeRequest().url.encodedPath } shouldContainExactly
                listOf("/api/thumbs/book/md", "/api/thumbs/book/lg", "/api/thumbs/book/sm")
        }

        it("TC-156: serves the first fall-back that exists") {
            enqueue(404)
            enqueue(200, "lg-bytes")

            repository.thumbnail("book", CoverSize.Md) shouldBe found("lg-bytes", CoverSize.Lg, fromCache = false)
            server.requestCount shouldBe 2
        }

        it("TC-157: falls back from the smallest size to the largest first") {
            enqueue(404)
            enqueue(404)
            enqueue(200, "md-bytes")

            repository.thumbnail("book", CoverSize.Sm)

            List(3) { server.takeRequest().url.encodedPath } shouldContainExactly
                listOf("/api/thumbs/book/sm", "/api/thumbs/book/lg", "/api/thumbs/book/md")
        }

        it("TC-158: reports NotFound when no size exists") {
            repeat(3) { enqueue(404) }

            repository.thumbnail("book", CoverSize.Md) shouldBe CoverImageResult.NotFound
        }

        it("TC-159: serves a cached other size, without asking again, when the server is down") {
            seed("book", CoverSize.Sm, "sm-bytes", etag = "\"s\"")
            enqueue(500)

            repository.thumbnail("book", CoverSize.Md) shouldBe found("sm-bytes", CoverSize.Sm, fromCache = true)
            server.requestCount shouldBe 1
        }

        it("TC-160: forgets a cached size the server says is gone") {
            seed("book", CoverSize.Md, "old", etag = "\"v1\"", storedAt = now - window)
            enqueue(404)
            enqueue(200, "lg-bytes")

            repository.thumbnail("book", CoverSize.Md) shouldBe found("lg-bytes", CoverSize.Lg, fromCache = false)

            cached("book", CoverSize.Md).shouldBeNull()
        }
    }

    describe("thumbnail with a cached copy") {

        it("TC-161: serves a fresh copy without a request") {
            seed("book", CoverSize.Md, "cached", etag = "\"v1\"", storedAt = now - (window - 1))

            repository.thumbnail("book", CoverSize.Md) shouldBe found("cached", CoverSize.Md, fromCache = true)
            server.requestCount shouldBe 0
        }

        it("TC-162: serves a fresh validator-less copy without a request") {
            seed("book", CoverSize.Md, "cached", etag = null, storedAt = now)

            repository.thumbnail("book", CoverSize.Md) shouldBe found("cached", CoverSize.Md, fromCache = true)
            server.requestCount shouldBe 0
        }

        describe("older than five minutes") {

            it("TC-163: revalidates with If-None-Match and reuses the cached bytes on a 304") {
                seed("book", CoverSize.Md, "cached", etag = "\"v1\"", storedAt = now - window)
                enqueue(304)

                repository.thumbnail("book", CoverSize.Md) shouldBe found("cached", CoverSize.Md, fromCache = true)

                val request = server.takeRequest()
                request.headers["If-None-Match"] shouldBe "\"v1\""
                request.headers["Authorization"] shouldBe "Bearer secret"
            }

            it("TC-164: restarts the freshness window after a 304") {
                seed("book", CoverSize.Md, "cached", etag = "\"v1\"", storedAt = now - window)
                enqueue(304)

                repository.thumbnail("book", CoverSize.Md)
                repository.thumbnail("book", CoverSize.Md)

                server.requestCount shouldBe 1
                cached("book", CoverSize.Md) shouldBe CachedCover("cached".encodeUtf8(), "\"v1\"", now)
            }

            it("TC-165: replaces the copy when the server sends a new one") {
                seed("book", CoverSize.Md, "cached", etag = "\"v1\"", storedAt = now - window)
                enqueue(200, "fresh", etag = "\"v2\"")

                repository.thumbnail("book", CoverSize.Md) shouldBe found("fresh", CoverSize.Md, fromCache = false)

                cached("book", CoverSize.Md) shouldBe CachedCover("fresh".encodeUtf8(), "\"v2\"", now)
            }

            it("TC-166: fetches once, unconditionally, when the copy has no validator, and stores one") {
                seed("book", CoverSize.Md, "cached", etag = null, storedAt = now - window)
                enqueue(200, "fresh", etag = "\"v1\"")

                repository.thumbnail("book", CoverSize.Md) shouldBe found("fresh", CoverSize.Md, fromCache = false)

                server.takeRequest().headers["If-None-Match"].shouldBeNull()
                cached("book", CoverSize.Md)!!.etag shouldBe "\"v1\""

                now += window
                enqueue(304)
                repository.thumbnail("book", CoverSize.Md)
                server.takeRequest().headers["If-None-Match"] shouldBe "\"v1\""
            }

            it("TC-167: serves the stale copy when the server can't be reached") {
                seed("book", CoverSize.Md, "cached", etag = "\"v1\"", storedAt = now - window)
                server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())

                repository.thumbnail("book", CoverSize.Md) shouldBe found("cached", CoverSize.Md, fromCache = true)
            }

            it("TC-168: serves the stale copy on a server error") {
                seed("book", CoverSize.Md, "cached", etag = "\"v1\"", storedAt = now - window)
                enqueue(503)

                repository.thumbnail("book", CoverSize.Md) shouldBe found("cached", CoverSize.Md, fromCache = true)
            }

            it("TC-169: serves the stale copy when no server is saved") {
                seed("book", CoverSize.Md, "cached", etag = "\"v1\"", storedAt = now - window)
                baseUrlProvider.baseUrl = null

                repository.thumbnail("book", CoverSize.Md) shouldBe found("cached", CoverSize.Md, fromCache = true)
            }
        }
    }

    describe("invalidate") {

        it("TC-170: drops every size from the disk cache") {
            CoverSize.entries.forEach { seed("book", it, "bytes", etag = "\"v\"") }
            seed("other", CoverSize.Md, "keep", etag = "\"v\"")

            repository.invalidate("book")

            CoverSize.entries.forEach { cached("book", it).shouldBeNull() }
            cached("other", CoverSize.Md).shouldNotBeNull()
        }

        it("TC-171: drops the decoded copies of every size from the memory cache") {
            val image = mockk<Image>(relaxed = true)
            val keys = CoverSize.entries.map {
                MemoryCache.Key(CoverCacheKeys.memoryKey(CoverThumbnail("book", it), now))
            }
            val otherKey = MemoryCache.Key(CoverCacheKeys.memoryKey(CoverThumbnail("other", CoverSize.Md), now))
            (keys + otherKey).forEach { memoryCache[it] = MemoryCache.Value(image) }

            repository.invalidate("book")

            keys.forEach { memoryCache[it].shouldBeNull() }
            memoryCache[otherKey].shouldNotBeNull()
        }

        it("TC-172: refetches the cover afterwards") {
            seed("book", CoverSize.Md, "old", etag = "\"v1\"")
            repository.invalidate("book")
            enqueue(200, "new", etag = "\"v2\"")

            repository.thumbnail("book", CoverSize.Md) shouldBe found("new", CoverSize.Md, fromCache = false)
        }

        it("TC-173: refuses to store a fetch that was already in flight, but still returns its bytes") {
            val result = fetchHeldOpen("book", CoverSize.Md, "superseded") { repository.invalidate("book") }

            result shouldBe found("superseded", CoverSize.Md, fromCache = false)
            cached("book", CoverSize.Md).shouldBeNull()
        }

        it("TC-205: doesn't throw when a disk removal fails, still tries every size and clears memory") {
            CoverSize.entries.forEach { seed("book", it, "bytes", etag = "\"v\"") }
            diskStore.removeFailure = IOException("journal locked")
            val image = mockk<Image>(relaxed = true)
            val keys = CoverSize.entries.map {
                MemoryCache.Key(CoverCacheKeys.memoryKey(CoverThumbnail("book", it), now))
            }
            keys.forEach { memoryCache[it] = MemoryCache.Value(image) }

            shouldNotThrowAny { repository.invalidate("book") }

            diskStore.removedKeys shouldContainExactlyInAnyOrder
                CoverSize.entries.map { CoverCacheKeys.diskKey("book", it) }
            keys.forEach { memoryCache[it].shouldBeNull() }
        }
    }

    describe("clear") {

        it("TC-174: empties the disk and memory caches") {
            seed("book", CoverSize.Md, "bytes", etag = "\"v\"")
            val key = MemoryCache.Key("k")
            memoryCache[key] = MemoryCache.Value(mockk<Image>(relaxed = true))

            repository.clear()

            diskStore.entries.keys.toList().shouldBeEmpty()
            memoryCache[key].shouldBeNull()
        }

        it("TC-206: doesn't throw when the disk clear fails, and still clears memory") {
            seed("book", CoverSize.Md, "bytes", etag = "\"v\"")
            diskStore.clearFailure = IOException("journal locked")
            val key = MemoryCache.Key("k")
            memoryCache[key] = MemoryCache.Value(mockk<Image>(relaxed = true))

            shouldNotThrowAny { repository.clear() }

            memoryCache[key].shouldBeNull()
        }

        it("TC-179: refuses to store a fetch that was already in flight, but still returns its bytes") {
            val result = fetchHeldOpen("book", CoverSize.Md, "superseded") { repository.clear() }

            result shouldBe found("superseded", CoverSize.Md, fromCache = false)
            cached("book", CoverSize.Md).shouldBeNull()
        }
    }
})
