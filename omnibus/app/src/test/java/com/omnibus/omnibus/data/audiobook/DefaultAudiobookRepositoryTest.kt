package com.omnibus.omnibus.data.audiobook

import com.omnibus.omnibus.data.audiobook.playback.Chapter
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl

/** A `direct` body with one part and the given book file id. */
private fun directBody(bookFileId: Long = randomLong(1L..1_000L)): String =
    """{"mode":"direct","book_file_id":$bookFileId,"total_duration_seconds":100.0,"parts":[
        {"ordinal":1,"url":"/api/parts/1","duration_seconds":100.0,"mime":"audio/mpeg"}]}"""

private fun AudiobookManifestResult.manifest(): AudiobookManifest =
    shouldBeInstanceOf<AudiobookManifestResult.Success>().manifest

private fun AudiobookManifestResult.direct(): AudiobookManifest.Direct =
    manifest().shouldBeInstanceOf<AudiobookManifest.Direct>()

class DefaultAudiobookRepositoryTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    /** A repository over [baseUrl], or with no server saved when it is null. */
    fun sut(baseUrl: HttpUrl?, token: String = randomString()) =
        DefaultAudiobookRepository(ioDispatcher = Dispatchers.Unconfined, omnibusHttpClient = httpClientFor(baseUrl, token))

    describe("requesting a manifest") {

        context("when a file id is given") {
            // TC-4
            it("asks the server for that file (I-LOAD-2)") {
                ScriptedServer().use { server ->
                    val fileId = randomLong()
                    server.answer(200, directBody())

                    sut(server.baseUrl).getManifest(randomString(), fileId)

                    server.takeRequest().url.queryParameter("file_id") shouldBe fileId.toString()
                }
            }
        }

        context("when the file comes back 404") {
            // TC-5
            it("asks once more without the file id (I-LOAD-2)") {
                ScriptedServer().use { server ->
                    server.answer(404)
                    server.answer(200, directBody())

                    sut(server.baseUrl).getManifest(randomString(), randomLong())

                    server.takeRequest()
                    server.takeRequest().url.queryParameter("file_id").shouldBeNull()
                }
            }

            // TC-5
            it("answers with the manifest of the retry (I-LOAD-2)") {
                ScriptedServer().use { server ->
                    server.answer(404)
                    server.answer(200, directBody())

                    sut(server.baseUrl).getManifest(randomString(), randomLong())
                        .shouldBeInstanceOf<AudiobookManifestResult.Success>()
                }
            }

            context("when the retry is a 404 too") {
                // TC-7
                it("fails with the retry's answer (I-LOAD-2)") {
                    ScriptedServer().use { server ->
                        server.answer(404)
                        server.answer(404, """{"error":"gone"}""")

                        sut(server.baseUrl).getManifest(randomString(), randomLong()) shouldBe
                            AudiobookManifestResult.Failure(AudiobookManifestError.Http(404, "gone"))
                    }
                }
            }
        }

        context("when no file id was given") {
            // TC-6
            it("does not retry a 404 (I-LOAD-2)") {
                ScriptedServer().use { server ->
                    server.answer(404)

                    sut(server.baseUrl).getManifest(randomString(), null)

                    server.requestCount shouldBe 1
                }
            }
        }

        // TC-13
        it("carries the audio fetch headers in the result (AND-STK-3)") {
            ScriptedServer().use { server ->
                val token = randomString()
                server.answer(200, directBody())

                val manifest = sut(server.baseUrl, token).getManifest(randomString(), null).manifest()

                manifest.requestHeaders shouldBe mapOf("Authorization" to "Bearer $token", "Accept" to "*/*")
            }
        }
    }

    describe("reading a direct manifest") {

        // TC-8
        it("lists the parts by ordinal with absolute URLs (I-LOAD-3)") {
            ScriptedServer().use { server ->
                server.answer(
                    200,
                    """{"mode":"direct","book_file_id":7,"total_duration_seconds":60.0,"parts":[
                      {"ordinal":3,"url":"/api/parts/3?file_id=7","duration_seconds":30.0,"mime":"audio/flac"},
                      {"ordinal":1,"url":"/api/parts/1?file_id=7","duration_seconds":10.0,"mime":"audio/mpeg"},
                      {"ordinal":2,"url":"/api/parts/2?file_id=7","duration_seconds":20.0,"mime":"audio/ogg"}]}""",
                )

                val parts = sut(server.baseUrl).getManifest(randomString(), null).direct().parts

                parts shouldContainExactly listOf(
                    AudioPart(1, server.url("/api/parts/1?file_id=7").toString(), 10.0, "audio/mpeg"),
                    AudioPart(2, server.url("/api/parts/2?file_id=7").toString(), 20.0, "audio/ogg"),
                    AudioPart(3, server.url("/api/parts/3?file_id=7").toString(), 30.0, "audio/flac"),
                )
            }
        }

        context("when the base URL has a path prefix") {
            // TC-10
            it("keeps the prefix in the part URLs (I-LOAD-3)") {
                ScriptedServer().use { server ->
                    server.answer(200, directBody())

                    val parts = sut(server.url("/omni/")).getManifest(randomString(), null).direct().parts

                    parts.single().url shouldBe server.url("/omni/api/parts/1").toString()
                }
            }
        }

        context("when the total duration is 0") {
            // TC-11
            it("totals the part durations (I-LOAD-3)") {
                ScriptedServer().use { server ->
                    server.answer(
                        200,
                        """{"mode":"direct","total_duration_seconds":0,"parts":[
                          {"ordinal":1,"url":"/api/parts/1","duration_seconds":100.5,"mime":"audio/mpeg"},
                          {"ordinal":2,"url":"/api/parts/2","duration_seconds":200.0,"mime":"audio/mpeg"}]}""",
                    )

                    sut(server.baseUrl).getManifest(randomString(), null).direct().totalDurationSeconds shouldBe 300.5
                }
            }
        }

        // TC-17
        it("orders the chapters with the last one running to the end (I-TL-2, I-TL-3)") {
            ScriptedServer().use { server ->
                server.answer(
                    200,
                    """{"mode":"direct","book_file_id":7,"total_duration_seconds":1000.0,
                      "parts":[{"ordinal":1,"url":"/api/parts/1","duration_seconds":1000.0,"mime":"audio/mpeg"}],
                      "chapters":[
                        {"ordinal":2,"title":"B","start_seconds":600.0,"duration_seconds":0.0},
                        {"ordinal":1,"title":"A","start_seconds":0.0,"duration_seconds":600.0}]}""",
                )

                sut(server.baseUrl).getManifest(randomString(), null).direct().chapters shouldBe listOf(
                    Chapter(1, "A", 0.0, 600.0),
                    Chapter(2, "B", 600.0, 400.0),
                )
            }
        }
    }

    describe("reading an hls manifest") {

        // TC-9
        it("maps it to an absolute playlist URL (I-LOAD-3)") {
            ScriptedServer().use { server ->
                server.answer(200, """{"mode":"hls","book_file_id":7,"playlist_url":"/api/playlist.m3u8"}""")

                val manifest = sut(server.baseUrl).getManifest(randomString(), null).manifest()

                manifest shouldBe AudiobookManifest.Hls(7L, server.url("/api/playlist.m3u8").toString(), manifest.requestHeaders)
            }
        }
    }

    describe("the manifest's file id") {

        // TC-12
        it("is the server's id, else the requested id, else 0, for each pair of ids") {
            ScriptedServer().use { server ->
                val served = randomLong(1L..1_000L)
                val requested = randomLong(1L..1_000L)
                checkAll(
                    Exhaustive.of(
                        Triple(served, requested, served),
                        Triple(0L, requested, requested),
                        Triple(0L, null, 0L),
                    ),
                ) { (servedId, requestedId, expected) ->
                    server.answer(200, directBody(servedId))

                    sut(server.baseUrl).getManifest(randomString(), requestedId).manifest().bookFileId shouldBe expected
                }
            }
        }
    }

    describe("failing to read a manifest") {

        context("when no server is saved") {
            // TC-14
            it("answers NoServer") {
                sut(baseUrl = null).getManifest(randomString(), null) shouldBe
                    AudiobookManifestResult.Failure(AudiobookManifestError.NoServer)
            }
        }

        // TC-14
        it("maps each kind of failed response to its error, without throwing") {
            ScriptedServer().use { server ->
                checkAll(
                    Exhaustive.of(
                        reply(401) to AudiobookManifestError.SessionExpired,
                        droppedConnection() to AudiobookManifestError.Unreachable,
                        reply(200, "{bad") to AudiobookManifestError.UnexpectedResponse,
                    ),
                ) { (response, error) ->
                    server.answer(response)

                    sut(server.baseUrl).getManifest(randomString(), null) shouldBe AudiobookManifestResult.Failure(error)
                }
            }
        }

        // TC-15
        it("carries the server's message for each shape of error body") {
            ScriptedServer().use { server ->
                checkAll(
                    Exhaustive.of(
                        """{"error":"a"}""" to "a",
                        """{"message":"b"}""" to "b",
                        """{"detail":"c"}""" to "c",
                        "  boom \n" to "boom",
                        "" to null,
                    ),
                ) { (body, message) ->
                    server.answer(500, body)

                    sut(server.baseUrl).getManifest(randomString(), null) shouldBe
                        AudiobookManifestResult.Failure(AudiobookManifestError.Http(500, message))
                }
            }
        }

        // TC-16
        it("rejects each kind of unplayable manifest") {
            ScriptedServer().use { server ->
                checkAll(
                    Exhaustive.of(
                        """{"mode":"direct","book_file_id":7,"parts":[]}""" to AudiobookManifestError.NoAudioParts,
                        """{"mode":"zip","book_file_id":7}""" to AudiobookManifestError.InvalidManifest,
                        """{"mode":"hls","playlist_url":null}""" to AudiobookManifestError.InvalidManifest,
                        """{"mode":"hls","playlist_url":"  "}""" to AudiobookManifestError.InvalidManifest,
                    ),
                ) { (body, error) ->
                    server.answer(200, body)

                    sut(server.baseUrl).getManifest(randomString(), null) shouldBe AudiobookManifestResult.Failure(error)
                }
            }
        }
    }
})
