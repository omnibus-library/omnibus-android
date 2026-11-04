package com.omnibus.omnibus.data.audiobook

import com.omnibus.omnibus.data.audiobook.playback.Chapter
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
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import mockwebserver3.SocketEffect
import okhttp3.HttpUrl

private const val UUID = "book-1"
private const val MANIFEST = "/api/audiobooks/book-1/manifest"

private fun reply(code: Int, body: String = ""): MockResponse = MockResponse.Builder().code(code).body(body).build()

/** A response whose connection drops before any byte of it arrives. */
private fun dropped(): MockResponse = MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build()

/** Answers each path's requests from its own queue, in order. A request with nothing queued gets a 500. */
private class ScriptedDispatcher : Dispatcher() {
    private val steps = mutableMapOf<String, ArrayDeque<MockResponse>>()

    fun answer(path: String, response: MockResponse) {
        synchronized(steps) { steps.getOrPut(path) { ArrayDeque() }.addLast(response) }
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.url.encodedPath
        return synchronized(steps) { steps[path]?.removeFirstOrNull() } ?: reply(500, "unscripted $path")
    }
}

/** A `direct` body with one part of [seconds] and the given book file id. */
private fun directBody(bookFileId: Long, seconds: Double = 100.0): String =
    """{"mode":"direct","book_file_id":$bookFileId,"total_duration_seconds":$seconds,"parts":[
        {"ordinal":1,"url":"/api/audiobooks/book-1/parts/1","duration_seconds":$seconds,"mime":"audio/mpeg"}]}"""

/** The absolute URL the server reports for [path], which may carry a query. */
private fun MockWebServer.absolute(path: String): String = url(path).toString()

class DefaultAudiobookRepositoryTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var script: ScriptedDispatcher

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    beforeEach {
        script = ScriptedDispatcher()
        server = MockWebServer().apply {
            dispatcher = script
            start()
        }
    }

    afterEach { server.close() }

    /** A repository over [baseUrl], or with no server when it is null. */
    fun repositoryFor(baseUrl: HttpUrl? = server.url("/")) = DefaultAudiobookRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = OmnibusHttpClient(
            baseUrlProvider = FakeServerBaseUrlProvider(baseUrl = baseUrl),
            authTokenProvider = FakeAuthTokenProvider(token = "secret"),
            omnibusNetworkInterceptor = OmnibusNetworkInterceptor(),
            authClientMetadataInterceptor = AuthClientMetadataInterceptor(FakeClientInfo()),
        ),
    )

    describe("file_id") {

        // TC-4
        it("adds file_id only when given (I-LOAD-2)") {
            script.answer(MANIFEST, reply(200, directBody(7)))
            script.answer(MANIFEST, reply(200, directBody(7)))

            repositoryFor().getManifest(UUID, null)
            server.takeRequest().url.queryParameter("file_id") shouldBe null

            repositoryFor().getManifest(UUID, 7L)
            server.takeRequest().url.queryParameter("file_id") shouldBe "7"
        }
    }

    describe("404 retry") {

        // TC-5
        it("retries once without file_id (I-LOAD-2)") {
            script.answer(MANIFEST, reply(404))
            script.answer(MANIFEST, reply(200, directBody(7)))

            val result = repositoryFor().getManifest(UUID, 7L)

            result.shouldBeInstanceOf<AudiobookManifestResult.Success>()
            server.requestCount shouldBe 2
            server.takeRequest().url.queryParameter("file_id") shouldBe "7"
            server.takeRequest().url.queryParameter("file_id") shouldBe null
        }

        // TC-6
        it("does not retry without a file_id (I-LOAD-2)") {
            script.answer(MANIFEST, reply(404))

            repositoryFor().getManifest(UUID, null) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.Http(404, null))
            server.requestCount shouldBe 1
        }

        // TC-7
        it("a 404 on the retry is a failure (I-LOAD-2)") {
            script.answer(MANIFEST, reply(404, """{"error":"gone"}"""))
            script.answer(MANIFEST, reply(404, """{"error":"gone"}"""))

            repositoryFor().getManifest(UUID, 7L) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.Http(404, "gone"))
            server.requestCount shouldBe 2
        }
    }

    describe("direct mode") {

        // TC-8
        it("sorts parts by ordinal (I-LOAD-3)") {
            script.answer(
                MANIFEST,
                reply(
                    200,
                    """{"mode":"direct","book_file_id":7,"total_duration_seconds":60.0,"parts":[
                      {"ordinal":3,"url":"/api/audiobooks/book-1/parts/3?file_id=7","duration_seconds":30.0,"mime":"audio/flac"},
                      {"ordinal":1,"url":"/api/audiobooks/book-1/parts/1?file_id=7","duration_seconds":10.0,"mime":"audio/mpeg"},
                      {"ordinal":2,"url":"/api/audiobooks/book-1/parts/2?file_id=7","duration_seconds":20.0,"mime":"audio/ogg"}]}""",
                ),
            )

            val manifest = repositoryFor().getManifest(UUID, 7L)
                .shouldBeInstanceOf<AudiobookManifestResult.Success>().manifest
                .shouldBeInstanceOf<AudiobookManifest.Direct>()

            manifest.parts shouldBe listOf(
                AudioPart(1, server.absolute("/api/audiobooks/book-1/parts/1?file_id=7"), 10.0, "audio/mpeg"),
                AudioPart(2, server.absolute("/api/audiobooks/book-1/parts/2?file_id=7"), 20.0, "audio/ogg"),
                AudioPart(3, server.absolute("/api/audiobooks/book-1/parts/3?file_id=7"), 30.0, "audio/flac"),
            )
        }
    }

    describe("hls mode") {

        // TC-9
        it("maps hls to a playlist URL with no chapters (I-LOAD-3)") {
            script.answer(
                MANIFEST,
                reply(200, """{"mode":"hls","book_file_id":7,"playlist_url":"/api/audiobooks/book-1/playlist.m3u8"}"""),
            )

            val manifest = repositoryFor().getManifest(UUID, 7L)
                .shouldBeInstanceOf<AudiobookManifestResult.Success>().manifest
                .shouldBeInstanceOf<AudiobookManifest.Hls>()

            manifest.playlistUrl shouldBe server.absolute("/api/audiobooks/book-1/playlist.m3u8")
            manifest.bookFileId shouldBe 7L
        }
    }

    describe("url resolution") {

        // TC-10
        it("keeps the base URL path prefix (I-LOAD-3)") {
            val base = server.url("/omni/")
            script.answer("/omni/api/audiobooks/book-1/manifest", reply(200, directBody(7)))
            script.answer(
                "/omni/api/audiobooks/book-1/manifest",
                reply(200, """{"mode":"hls","book_file_id":7,"playlist_url":"/api/audiobooks/book-1/playlist.m3u8"}"""),
            )

            val direct = repositoryFor(base).getManifest(UUID, null)
                .shouldBeInstanceOf<AudiobookManifestResult.Success>().manifest
                .shouldBeInstanceOf<AudiobookManifest.Direct>()
            direct.parts.single().url shouldBe "http://${base.host}:${base.port}/omni/api/audiobooks/book-1/parts/1"

            val hls = repositoryFor(base).getManifest(UUID, null)
                .shouldBeInstanceOf<AudiobookManifestResult.Success>().manifest
                .shouldBeInstanceOf<AudiobookManifest.Hls>()
            hls.playlistUrl shouldBe "http://${base.host}:${base.port}/omni/api/audiobooks/book-1/playlist.m3u8"
        }

        // TC-10
        it("strips the leading slash (I-LOAD-3)") {
            script.answer(
                MANIFEST,
                reply(
                    200,
                    """{"mode":"direct","book_file_id":7,"parts":[
                      {"ordinal":1,"url":"/api/audiobooks/book-1/parts/1?file_id=7","duration_seconds":5.0,"mime":"audio/mpeg"}]}""",
                ),
            )

            val manifest = repositoryFor().getManifest(UUID, 7L)
                .shouldBeInstanceOf<AudiobookManifestResult.Success>().manifest
                .shouldBeInstanceOf<AudiobookManifest.Direct>()

            manifest.parts.single().url shouldBe server.absolute("/api/audiobooks/book-1/parts/1?file_id=7")
        }
    }

    describe("total duration") {

        // TC-11
        it("falls back to the sum of part durations when 0 (I-LOAD-3)") {
            val parts = """"parts":[
                {"ordinal":1,"url":"/api/audiobooks/book-1/parts/1","duration_seconds":100.5,"mime":"audio/mpeg"},
                {"ordinal":2,"url":"/api/audiobooks/book-1/parts/2","duration_seconds":200.0,"mime":"audio/mpeg"}]"""
            script.answer(MANIFEST, reply(200, """{"mode":"direct","total_duration_seconds":0,$parts}"""))
            script.answer(MANIFEST, reply(200, """{"mode":"direct","total_duration_seconds":999,$parts}"""))

            (repositoryFor().getManifest(UUID, null) as AudiobookManifestResult.Success).manifest
                .shouldBeInstanceOf<AudiobookManifest.Direct>().totalDurationSeconds shouldBe 300.5
            (repositoryFor().getManifest(UUID, null) as AudiobookManifestResult.Success).manifest
                .shouldBeInstanceOf<AudiobookManifest.Direct>().totalDurationSeconds shouldBe 999.0
        }
    }

    describe("bookFileId") {

        // TC-12
        it("uses the manifest id, else the requested id") {
            script.answer(MANIFEST, reply(200, directBody(55)))
            script.answer(MANIFEST, reply(200, directBody(0)))
            script.answer(MANIFEST, reply(200, directBody(0)))

            (repositoryFor().getManifest(UUID, 7L) as AudiobookManifestResult.Success).manifest.bookFileId shouldBe 55L
            (repositoryFor().getManifest(UUID, 7L) as AudiobookManifestResult.Success).manifest.bookFileId shouldBe 7L
            (repositoryFor().getManifest(UUID, null) as AudiobookManifestResult.Success).manifest.bookFileId shouldBe 0L
        }
    }

    describe("headers") {

        // TC-13
        it("carries the audio fetch headers (AND-STK-3)") {
            script.answer(MANIFEST, reply(200, directBody(7)))

            val result = repositoryFor().getManifest(UUID, 7L) as AudiobookManifestResult.Success

            result.manifest.requestHeaders shouldBe mapOf("Authorization" to "Bearer secret", "Accept" to "*/*")
            server.takeRequest().headers["Authorization"] shouldBe "Bearer secret"
        }
    }

    describe("typed errors") {

        // TC-14
        it("maps each failure without throwing") {
            repositoryFor(baseUrl = null).getManifest(UUID, null) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.NoServer)

            script.answer(MANIFEST, reply(401))
            repositoryFor().getManifest(UUID, null) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.SessionExpired)

            script.answer(MANIFEST, dropped())
            repositoryFor().getManifest(UUID, null) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.Unreachable)

            script.answer(MANIFEST, reply(200, "{bad"))
            repositoryFor().getManifest(UUID, null) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.UnexpectedResponse)

            script.answer(MANIFEST, reply(500, "boom"))
            repositoryFor().getManifest(UUID, null) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.Http(500, "boom"))
        }
    }

    describe("server message") {

        // TC-15
        it("extracts error, message, detail") {
            val cases = listOf(
                """{"error":"a"}""" to "a",
                """{"message":"b"}""" to "b",
                """{"detail":"c"}""" to "c",
            )
            cases.forEach { (body, message) ->
                script.answer(MANIFEST, reply(500, body))
                repositoryFor().getManifest(UUID, null) shouldBe
                    AudiobookManifestResult.Failure(AudiobookManifestError.Http(500, message))
            }
        }

        // TC-15
        it("falls back to the trimmed body capped at 300") {
            val cases = listOf(
                "  boom \n" to "boom",
                "x".repeat(500) to "x".repeat(300),
                """{"error":5}""" to """{"error":5}""",
            )
            cases.forEach { (body, message) ->
                script.answer(MANIFEST, reply(500, body))
                repositoryFor().getManifest(UUID, null) shouldBe
                    AudiobookManifestResult.Failure(AudiobookManifestError.Http(500, message))
            }
        }

        // TC-15
        it("is null for an empty body") {
            script.answer(MANIFEST, reply(500, ""))

            repositoryFor().getManifest(UUID, null) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.Http(500, null))
        }
    }

    describe("manifest content errors") {

        // TC-16
        it("empty parts is NoAudioParts") {
            script.answer(MANIFEST, reply(200, """{"mode":"direct","book_file_id":7,"parts":[]}"""))

            repositoryFor().getManifest(UUID, 7L) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.NoAudioParts)
        }

        // TC-16
        it("unknown mode is an invalid manifest") {
            script.answer(MANIFEST, reply(200, """{"mode":"zip","book_file_id":7}"""))

            repositoryFor().getManifest(UUID, 7L) shouldBe
                AudiobookManifestResult.Failure(AudiobookManifestError.InvalidManifest)
        }

        // TC-16
        it("hls without a playlist is an invalid manifest") {
            script.answer(MANIFEST, reply(200, """{"mode":"hls","playlist_url":null}"""))
            script.answer(MANIFEST, reply(200, """{"mode":"hls","playlist_url":"  "}"""))

            repeat(2) {
                repositoryFor().getManifest(UUID, 7L) shouldBe
                    AudiobookManifestResult.Failure(AudiobookManifestError.InvalidManifest)
            }
        }
    }

    describe("chapters") {

        // TC-17
        it("resolves chapters (I-TL-2, I-TL-3)") {
            script.answer(
                MANIFEST,
                reply(
                    200,
                    """{"mode":"direct","book_file_id":7,"total_duration_seconds":1000.0,
                      "parts":[{"ordinal":1,"url":"/api/audiobooks/book-1/parts/1","duration_seconds":1000.0,"mime":"audio/mpeg"}],
                      "chapters":[
                        {"ordinal":2,"title":"B","start_seconds":600.0,"duration_seconds":0.0},
                        {"ordinal":1,"title":"A","start_seconds":0.0,"duration_seconds":600.0}]}""",
                ),
            )

            val manifest = (repositoryFor().getManifest(UUID, 7L) as AudiobookManifestResult.Success).manifest
                .shouldBeInstanceOf<AudiobookManifest.Direct>()

            manifest.chapters shouldBe listOf(
                Chapter(1, "A", 0.0, 600.0),
                Chapter(2, "B", 600.0, 400.0),
            )
        }
    }
})
