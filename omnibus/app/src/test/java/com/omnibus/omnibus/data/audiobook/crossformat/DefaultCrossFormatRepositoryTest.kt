package com.omnibus.omnibus.data.audiobook.crossformat

import com.omnibus.omnibus.data.audiobook.ScriptedServer
import com.omnibus.omnibus.data.audiobook.crossformat.local.CrossFormatDismissStore
import com.omnibus.omnibus.data.audiobook.httpClientFor
import com.omnibus.omnibus.data.audiobook.unreachableUrl
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl

private class TestCrossFormatDismissStore : CrossFormatDismissStore {
    private val clocks = mutableMapOf<String, Long>()

    override suspend fun dismissedClock(bookUuid: String): Long? = clocks[bookUuid]

    override suspend fun store(bookUuid: String, sourceClock: Long) {
        clocks[bookUuid] = sourceClock
    }
}

/** A candidate body with [fields] in its candidate object, which has a clock and a position on its own. */
private fun candidateBody(fields: String = "", state: String = "candidate"): String =
    """{"state":"$state","candidate":{"source_client_updated_at":42,"audio_position_seconds":12.5$fields}}"""

class DefaultCrossFormatRepositoryTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    /** A repository for [baseUrl], or with no server saved when it is null. */
    fun sut(baseUrl: HttpUrl?, dismissStore: CrossFormatDismissStore = TestCrossFormatDismissStore()) =
        DefaultCrossFormatRepository(
            ioDispatcher = Dispatchers.Unconfined,
            omnibusHttpClient = httpClientFor(baseUrl),
            dismissStore = dismissStore,
        )

    describe("reading the ebook's position for audio") {

        // I-RES-7, P-SYNC-7
        it("#166 TC-5: maps a candidate's fields, over the follow value crossed with the source-ahead value (I-RES-7, P-SYNC-AC5)") {
            ScriptedServer().use { server ->
                checkAll(Exhaustive.boolean(), Exhaustive.boolean()) { follow, ahead ->
                    val candidate = randomCandidate().copy(follow = follow, sourceAhead = ahead)
                    server.answer(
                        200,
                        """{"state":"candidate","follow":$follow,"candidate":{
                          "source_client_updated_at":${candidate.sourceClock},
                          "book_file_id":${candidate.bookFileId},
                          "audio_position_seconds":${candidate.positionSeconds},
                          "source_ahead":$ahead}}""",
                    )

                    sut(server.baseUrl).audioResume(randomString()) shouldBe candidate
                }
            }
        }

        // I-RES-7, P-SYNC-7
        it("#166 TC-5: asks for the book's audio target (I-RES-7)") {
            ScriptedServer().use { server ->
                val book = randomString()
                server.answer(200, candidateBody())

                sut(server.baseUrl).audioResume(book)

                val request = server.takeRequest()
                request.url.encodedPath shouldBe "/api/books/$book/cross-format-resume"
                request.url.queryParameter("target") shouldBe "audio"
            }
        }

        // P-SYNC-AC5, R6
        it("#166 TC-7: treats an absent or null source_ahead as ahead (P-SYNC-AC5)") {
            ScriptedServer().use { server ->
                checkAll(Exhaustive.of("", ""","source_ahead":null""")) { field ->
                    server.answer(200, candidateBody(field))

                    sut(server.baseUrl).audioResume(randomString())
                        .shouldBeInstanceOf<CrossFormatAudioResult.Candidate>().sourceAhead shouldBe true
                }
            }
        }

        // R4
        it("#166 TC-11: leaves the file to the holder, for a null or absent book_file_id (I-RES-7)") {
            ScriptedServer().use { server ->
                checkAll(Exhaustive.of("", ""","book_file_id":null""")) { field ->
                    server.answer(200, candidateBody(field))

                    sut(server.baseUrl).audioResume(randomString())
                        .shouldBeInstanceOf<CrossFormatAudioResult.Candidate>().bookFileId.shouldBeNull()
                }
            }
        }

        // I-RES-7, R4: every state other than candidate is no offer, including one the app doesn't know yet
        it("#166 TC-8, TC-9: answers NoCandidate for each state other than candidate (I-RES-7)") {
            ScriptedServer().use { server ->
                checkAll(Exhaustive.of("not_linked", "link_stale", "nothing_newer", "aligned", "something_new")) { state ->
                    server.answer(200, candidateBody(state = state))

                    sut(server.baseUrl).audioResume(randomString()) shouldBe CrossFormatAudioResult.NoCandidate
                }
            }
        }

        // R4
        it("#166 TC-10: answers NoCandidate for each candidate with no position (I-RES-7)") {
            ScriptedServer().use { server ->
                checkAll(
                    Exhaustive.of(
                        """{"state":"candidate","candidate":{"source_client_updated_at":42,"audio_position_seconds":null}}""",
                        """{"state":"candidate","candidate":{"source_client_updated_at":42}}""",
                        """{"state":"candidate"}""",
                    ),
                ) { body ->
                    server.answer(200, body)

                    sut(server.baseUrl).audioResume(randomString()) shouldBe CrossFormatAudioResult.NoCandidate
                }
            }
        }

        // R4: never thrown
        it("#166 TC-12, TC-13: answers Failed for each HTTP error or unreadable body (I-RES-7)") {
            ScriptedServer().use { server ->
                checkAll(
                    Exhaustive.of(404 to "", 500 to "", 200 to "not json", 200 to "", 200 to "[]", 200 to "\"x\""),
                ) { (code, body) ->
                    server.answer(code, body)

                    sut(server.baseUrl).audioResume(randomString()) shouldBe CrossFormatAudioResult.Failed
                }
            }
        }

        // R4: never thrown
        it("#166 TC-14, TC-15: answers Failed for a missing or unreachable server (I-RES-7)") {
            checkAll(Exhaustive.of(null, unreachableUrl())) { baseUrl ->
                sut(baseUrl).audioResume(randomString()) shouldBe CrossFormatAudioResult.Failed
            }
        }
    }

    describe("remembering a dismissed offer") {

        // R8
        it("#166 TC-84: reads back the clock it stored for the book (R8)") {
            val repository = sut(baseUrl = null)
            val book = randomString()
            val clock = randomLong()

            repository.storeDismissedClock(book, clock)

            repository.dismissedClock(book) shouldBe clock
        }
    }
})
