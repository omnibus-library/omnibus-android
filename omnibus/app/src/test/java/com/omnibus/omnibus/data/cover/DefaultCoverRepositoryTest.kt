package com.omnibus.omnibus.data.cover

import coil3.Image
import coil3.memory.MemoryCache
import com.omnibus.omnibus.data.auth.remote.AuthClientMetadataInterceptor
import com.omnibus.omnibus.data.auth.remote.TestClientInfo
import com.omnibus.omnibus.data.cover.local.CachedCover
import com.omnibus.omnibus.data.cover.local.CoverDiskStore
import com.omnibus.omnibus.data.cover.local.TestCoverDiskStore
import com.omnibus.omnibus.data.cover.local.randomCachedCover
import com.omnibus.omnibus.data.network.AuthTokenProvider
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.network.OmnibusNetworkInterceptor
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.mockk.mockk
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
import okhttp3.HttpUrl
import okio.ByteString.Companion.encodeUtf8

private const val WINDOW = CoverCacheKeys.REVALIDATE_AFTER_MILLIS

private inline fun <T> withServer(block: (MockWebServer) -> T): T =
    MockWebServer().use { server ->
        server.start()
        block(server)
    }

private fun MockWebServer.enqueueReply(code: Int, body: String = "", etag: String? = null) {
    enqueue(MockResponse.Builder().code(code).body(body).apply { etag?.let { addHeader("ETag", it) } }.build())
}

private fun reply(code: Int): MockResponse = MockResponse.Builder().code(code).build()

private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

private fun randomEtag(): String = "\"${randomString()}\""

private fun newMemoryCache(): MemoryCache = MemoryCache.Builder().maxSizeBytes(1024 * 1024).build()

private fun TestCoverDiskStore.seed(uuid: String, size: CoverSize, cover: CachedCover) {
    entries[CoverCacheKeys.diskKey(uuid, size)] = cover
}

private fun TestCoverDiskStore.cached(uuid: String, size: CoverSize): CachedCover? =
    entries[CoverCacheKeys.diskKey(uuid, size)]

private fun storeWith(uuid: String, size: CoverSize, cover: CachedCover) =
    TestCoverDiskStore().apply { seed(uuid, size, cover) }

/** A cached cover exactly at the revalidation window on [time]'s clock. */
private fun staleCover(time: TestTimeSource) = randomCachedCover().copy(storedAtMillis = time.currentMillis - WINDOW)

private fun found(body: String, size: CoverSize, fromCache: Boolean) =
    CoverImageResult.Found(body.encodeUtf8(), size, fromCache)

private fun foundCached(cover: CachedCover, size: CoverSize) = CoverImageResult.Found(cover.bytes, size, fromCache = true)

/** Puts an entry at [size] of the [uuid] book into [cache], and returns its key. */
private fun MemoryCache.seed(uuid: String, size: CoverSize, nowMillis: Long): MemoryCache.Key {
    val key = MemoryCache.Key(CoverCacheKeys.memoryKey(CoverThumbnail(uuid, size), nowMillis))
    set(key, MemoryCache.Value(mockk<Image>(relaxed = true)))
    return key
}

/**
 * Fetches [uuid] at [size] while [server] holds its 200 [body] response, runs [whileInFlight] once the request has
 * reached the server, then releases the response and returns the result.
 */
private suspend fun fetchHeldOpen(
    server: MockWebServer,
    repository: CoverRepository,
    uuid: String,
    size: CoverSize,
    body: String,
    whileInFlight: suspend () -> Unit,
): CoverImageResult = coroutineScope {
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

class DefaultCoverRepositoryTest : DescribeSpec({

    fun sut(
        baseUrl: HttpUrl? = null,
        diskStore: CoverDiskStore = TestCoverDiskStore(),
        memoryCache: MemoryCache = newMemoryCache(),
        timeSource: TimeSource = TestTimeSource(randomLong()),
        authTokenProvider: AuthTokenProvider = TestAuthTokenProvider(),
    ): DefaultCoverRepository = DefaultCoverRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider(baseUrl),
            authTokenProvider = authTokenProvider,
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(TestClientInfo()),
        ),
        diskStore = diskStore,
        memoryCache = memoryCache,
        timeSource = timeSource,
    )

    suspend fun thumbnailAnswering(response: MockResponse): CoverImageResult = withServer { server ->
        server.enqueue(response)
        sut(server.url("/")).thumbnail(randomString(), randomEnum<CoverSize>())
    }

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("loading a thumbnail") {
        context("when nothing is cached") {
            it("TC-147: returns the requested size from the server") {
                withServer { server ->
                    val uuid = randomString()
                    val size = randomEnum<CoverSize>()
                    val body = randomString()
                    server.enqueueReply(200, body)

                    sut(server.url("/")).thumbnail(uuid, size) shouldBe found(body, size, fromCache = false)

                    server.takeRequest().url.encodedPath shouldBe "/api/thumbs/$uuid/${size.apiName}"
                }
            }

            it("TC-148: stores the bytes with their validator and fetch time") {
                withServer { server ->
                    val uuid = randomString()
                    val size = randomEnum<CoverSize>()
                    val body = randomString()
                    val etag = randomEtag()
                    val time = TestTimeSource(randomLong())
                    val diskStore = TestCoverDiskStore()
                    server.enqueueReply(200, body, etag)

                    sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, size)

                    diskStore.cached(uuid, size) shouldBe CachedCover(body.encodeUtf8(), etag, time.currentMillis)
                }
            }

            context("when no server is saved") {
                it("TC-150: fails") {
                    sut().thumbnail(randomString(), randomEnum<CoverSize>()) shouldBe CoverImageResult.Error
                }
            }

            context("when the server errors") {
                it("TC-151: fails") {
                    thumbnailAnswering(reply(500)) shouldBe CoverImageResult.Error
                }
            }

            context("when the server can't be reached") {
                it("TC-153: fails") {
                    thumbnailAnswering(dropped()) shouldBe CoverImageResult.Error
                }
            }

            context("when the server answers 304") {
                it("TC-175: fails") {
                    thumbnailAnswering(reply(304)) shouldBe CoverImageResult.Error
                }
            }
        }

        context("when the bytes can't be cached") {
            it("TC-154: still returns them") {
                withServer { server ->
                    val size = randomEnum<CoverSize>()
                    val body = randomString()
                    val diskStore = TestCoverDiskStore().apply { writeFailure = IOException("disk full") }
                    server.enqueueReply(200, body)

                    sut(server.url("/"), diskStore).thumbnail(randomString(), size) shouldBe
                        found(body, size, fromCache = false)
                }
            }
        }

        context("when the cache can't be read") {
            it("TC-176: loads from the server") {
                withServer { server ->
                    val size = randomEnum<CoverSize>()
                    val body = randomString()
                    val diskStore = TestCoverDiskStore().apply { readFailure = IOException("corrupt entry") }
                    server.enqueueReply(200, body)

                    sut(server.url("/"), diskStore).thumbnail(randomString(), size) shouldBe
                        found(body, size, fromCache = false)
                }
            }
        }

        context("when the cache write is cancelled") {
            it("TC-180: rethrows the cancellation") {
                withServer { server ->
                    val diskStore = TestCoverDiskStore().apply { writeFailure = CancellationException("cancelled") }
                    server.enqueueReply(200, randomString())

                    shouldThrow<CancellationException> {
                        sut(server.url("/"), diskStore).thumbnail(randomString(), randomEnum<CoverSize>())
                    }
                }
            }
        }

        context("when the requested size is missing") {
            it("TC-155: tries the other sizes largest first, for each requested size") {
                withServer { server ->
                    val orders = listOf(
                        CoverSize.Sm to listOf("sm", "lg", "md"),
                        CoverSize.Md to listOf("md", "lg", "sm"),
                        CoverSize.Lg to listOf("lg", "md", "sm"),
                    )
                    checkAll(Exhaustive.collection(orders)) { (requested, order) ->
                        val uuid = randomString()
                        repeat(3) { server.enqueueReply(404) }

                        sut(server.url("/")).thumbnail(uuid, requested)

                        List(3) { server.takeRequest().url.encodedPath } shouldBe
                            order.map { "/api/thumbs/$uuid/$it" }
                    }
                }
            }

            it("TC-156: serves the first fall-back that exists") {
                withServer { server ->
                    val body = randomString()
                    server.enqueueReply(404)
                    server.enqueueReply(200, body)

                    sut(server.url("/")).thumbnail(randomString(), CoverSize.Md) shouldBe
                        found(body, CoverSize.Lg, fromCache = false)
                }
            }

            context("when no size exists") {
                context("when one size was stale in the cache") {
                    it("TC-178: reports NotFound") {
                        withServer { server ->
                            val uuid = randomString()
                            val size = randomEnum<CoverSize>()
                            val time = TestTimeSource(randomLong())
                            val diskStore = storeWith(uuid, size, staleCover(time))
                            repeat(3) { server.enqueueReply(404) }

                            sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, size) shouldBe
                                CoverImageResult.NotFound
                        }
                    }
                }
            }

            context("when the requested size was stale in the cache") {
                it("TC-160: forgets it") {
                    withServer { server ->
                        val uuid = randomString()
                        val time = TestTimeSource(randomLong())
                        val diskStore = storeWith(uuid, CoverSize.Md, staleCover(time))
                        server.enqueueReply(404)
                        server.enqueueReply(200, randomString())

                        sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, CoverSize.Md)

                        diskStore.cached(uuid, CoverSize.Md).shouldBeNull()
                    }
                }
            }
        }

        context("when the server fails for the requested size") {
            context("when another size is cached") {
                it("TC-159: serves the cached size") {
                    withServer { server ->
                        val uuid = randomString()
                        val time = TestTimeSource(randomLong())
                        val cover = randomCachedCover().copy(storedAtMillis = time.currentMillis)
                        val diskStore = TestCoverDiskStore().apply { seed(uuid, CoverSize.Sm, cover) }
                        server.enqueueReply(500)

                        sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, CoverSize.Md) shouldBe
                            foundCached(cover, CoverSize.Sm)
                    }
                }
            }
        }

        context("when the cached copy is younger than the revalidation window") {
            it("TC-161: serves it without asking the server, with or without a validator") {
                withServer { server ->
                    val etags = listOf(randomEtag(), null)
                    checkAll(Exhaustive.collection(etags)) { etag ->
                        val uuid = randomString()
                        val size = randomEnum<CoverSize>()
                        val time = TestTimeSource(randomLong())
                        val cover = randomCachedCover().copy(etag = etag, storedAtMillis = time.currentMillis - (WINDOW - 1))
                        val diskStore = storeWith(uuid, size, cover)
                        server.dispatcher = object : Dispatcher() {
                            override fun dispatch(request: RecordedRequest): MockResponse =
                                MockResponse.Builder().code(200).body(randomString()).build()
                        }

                        sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, size) shouldBe
                            foundCached(cover, size)
                    }
                }
            }
        }

        context("when the cached copy has reached the revalidation window") {
            context("when the copy has a validator") {
                it("TC-163: revalidates with If-None-Match") {
                    withServer { server ->
                        val uuid = randomString()
                        val size = randomEnum<CoverSize>()
                        val etag = randomEtag()
                        val time = TestTimeSource(randomLong())
                        val diskStore = storeWith(uuid, size, staleCover(time).copy(etag = etag))
                        server.enqueueReply(304)

                        sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, size)

                        server.takeRequest().headers["If-None-Match"] shouldBe etag
                    }
                }

                context("when the server answers 304") {
                    it("TC-163: reuses the cached bytes") {
                        withServer { server ->
                            val uuid = randomString()
                            val size = randomEnum<CoverSize>()
                            val time = TestTimeSource(randomLong())
                            val cover = staleCover(time).copy(etag = randomEtag())
                            val diskStore = storeWith(uuid, size, cover)
                            server.enqueueReply(304)

                            sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, size) shouldBe
                                foundCached(cover, size)
                        }
                    }

                    it("TC-164: restarts the freshness window") {
                        withServer { server ->
                            val uuid = randomString()
                            val size = randomEnum<CoverSize>()
                            val time = TestTimeSource(randomLong())
                            val cover = staleCover(time).copy(etag = randomEtag())
                            val diskStore = storeWith(uuid, size, cover)
                            server.enqueueReply(304)

                            sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, size)

                            diskStore.cached(uuid, size) shouldBe cover.copy(storedAtMillis = time.currentMillis)
                        }
                    }
                }

                context("when the server sends a new copy") {
                    it("TC-165: replaces the cached copy") {
                        withServer { server ->
                            val uuid = randomString()
                            val size = randomEnum<CoverSize>()
                            val body = randomString()
                            val etag = randomEtag()
                            val time = TestTimeSource(randomLong())
                            val diskStore = storeWith(uuid, size, staleCover(time).copy(etag = randomEtag()))
                            server.enqueueReply(200, body, etag)

                            sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, size)

                            diskStore.cached(uuid, size) shouldBe CachedCover(body.encodeUtf8(), etag, time.currentMillis)
                        }
                    }
                }
            }

            context("when the copy has no validator") {
                it("TC-166: fetches unconditionally") {
                    withServer { server ->
                        val uuid = randomString()
                        val size = randomEnum<CoverSize>()
                        val time = TestTimeSource(randomLong())
                        val diskStore = storeWith(uuid, size, staleCover(time).copy(etag = null))
                        server.enqueueReply(200, randomString())

                        sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, size)

                        server.takeRequest().headers["If-None-Match"].shouldBeNull()
                    }
                }
            }

            context("when the server can't answer") {
                it("TC-167: serves the stale copy for a dropped connection and a server error") {
                    withServer { server ->
                        val answers = listOf(dropped(), MockResponse.Builder().code(503).build())
                        checkAll(Exhaustive.collection(answers)) { answer ->
                            val uuid = randomString()
                            val size = randomEnum<CoverSize>()
                            val time = TestTimeSource(randomLong())
                            val cover = staleCover(time)
                            val diskStore = storeWith(uuid, size, cover)
                            server.enqueue(answer)

                            sut(server.url("/"), diskStore, timeSource = time).thumbnail(uuid, size) shouldBe
                                foundCached(cover, size)
                        }
                    }
                }
            }

            context("when no server is saved") {
                it("TC-169: serves the stale copy") {
                    val uuid = randomString()
                    val size = randomEnum<CoverSize>()
                    val time = TestTimeSource(randomLong())
                    val cover = staleCover(time)
                    val diskStore = storeWith(uuid, size, cover)

                    sut(diskStore = diskStore, timeSource = time).thumbnail(uuid, size) shouldBe foundCached(cover, size)
                }
            }
        }
    }

    describe("invalidating a book's covers") {
        it("TC-170: drops every size of that book from the disk cache") {
            val uuid = randomString(8..16)
            val diskStore = TestCoverDiskStore().apply {
                CoverSize.entries.forEach { seed(uuid, it, randomCachedCover()) }
            }

            sut(diskStore = diskStore).invalidate(uuid)

            diskStore.entries.keys.toList().shouldBeEmpty()
        }

        it("TC-171: drops the decoded copies of every size of that book from memory") {
            val uuid = randomString(8..16)
            val memoryCache = newMemoryCache()
            val keys = CoverSize.entries.map { memoryCache.seed(uuid, it, randomLong()) }

            sut(memoryCache = memoryCache).invalidate(uuid)

            keys.map { memoryCache[it] } shouldBe listOf(null, null, null)
        }

        it("TC-171: keeps other books in memory") {
            val memoryCache = newMemoryCache()
            val other = memoryCache.seed(randomString(8..16), randomEnum<CoverSize>(), randomLong())

            sut(memoryCache = memoryCache).invalidate(randomString(8..16))

            memoryCache[other].shouldNotBeNull()
        }

        context("when a fetch is already in flight") {
            it("TC-173: returns its bytes") {
                withServer { server ->
                    val uuid = randomString()
                    val size = randomEnum<CoverSize>()
                    val body = randomString()
                    val repository = sut(server.url("/"))

                    fetchHeldOpen(server, repository, uuid, size, body) { repository.invalidate(uuid) } shouldBe
                        found(body, size, fromCache = false)
                }
            }

            it("TC-173: refuses to store them") {
                withServer { server ->
                    val uuid = randomString()
                    val size = randomEnum<CoverSize>()
                    val diskStore = TestCoverDiskStore()
                    val repository = sut(server.url("/"), diskStore)

                    fetchHeldOpen(server, repository, uuid, size, randomString()) { repository.invalidate(uuid) }

                    diskStore.cached(uuid, size).shouldBeNull()
                }
            }
        }

        context("when a disk removal fails") {
            it("TC-205: still tries every size") {
                val uuid = randomString()
                val diskStore = TestCoverDiskStore().apply { removeFailure = IOException("journal locked") }

                shouldNotThrowAny { sut(diskStore = diskStore).invalidate(uuid) }

                diskStore.removedKeys shouldContainExactlyInAnyOrder
                    CoverSize.entries.map { CoverCacheKeys.diskKey(uuid, it) }
            }

            it("TC-205: still clears memory") {
                val uuid = randomString()
                val diskStore = TestCoverDiskStore().apply { removeFailure = IOException("journal locked") }
                val memoryCache = newMemoryCache()
                val key = memoryCache.seed(uuid, randomEnum<CoverSize>(), randomLong())

                sut(diskStore = diskStore, memoryCache = memoryCache).invalidate(uuid)

                memoryCache[key].shouldBeNull()
            }
        }
    }

    describe("clearing the covers") {
        it("TC-174: empties the disk cache") {
            val diskStore = TestCoverDiskStore().apply {
                seed(randomString(), randomEnum<CoverSize>(), randomCachedCover())
            }

            sut(diskStore = diskStore).clear()

            diskStore.entries.keys.toList().shouldBeEmpty()
        }

        it("TC-174: empties the memory cache") {
            val memoryCache = newMemoryCache()
            val key = memoryCache.seed(randomString(), randomEnum<CoverSize>(), randomLong())

            sut(memoryCache = memoryCache).clear()

            memoryCache[key].shouldBeNull()
        }

        context("when the disk clear fails") {
            it("TC-206: still clears memory") {
                val diskStore = TestCoverDiskStore().apply { clearFailure = IOException("journal locked") }
                val memoryCache = newMemoryCache()
                val key = memoryCache.seed(randomString(), randomEnum<CoverSize>(), randomLong())

                sut(diskStore = diskStore, memoryCache = memoryCache).clear()

                memoryCache[key].shouldBeNull()
            }
        }

        context("when a fetch is already in flight") {
            it("TC-179: refuses to store them") {
                withServer { server ->
                    val uuid = randomString()
                    val size = randomEnum<CoverSize>()
                    val diskStore = TestCoverDiskStore()
                    val repository = sut(server.url("/"), diskStore)

                    fetchHeldOpen(server, repository, uuid, size, randomString()) { repository.clear() }

                    diskStore.cached(uuid, size).shouldBeNull()
                }
            }
        }
    }
})
