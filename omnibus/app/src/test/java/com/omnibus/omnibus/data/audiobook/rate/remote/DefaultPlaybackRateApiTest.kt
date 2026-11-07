package com.omnibus.omnibus.data.audiobook.rate.remote

import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl

class DefaultPlaybackRateApiTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    /** An API for [baseUrl], or with no server saved when it is null. */
    fun sut(baseUrl: HttpUrl?) =
        DefaultPlaybackRateApi(ioDispatcher = Dispatchers.Unconfined, omnibusHttpClient = testOmnibusHttpClient(baseUrl))

    describe("reading a rate") {

        // R13: a separator in the uuid cannot change the path
        it("#168 TC-16b: encodes the uuid as one path segment (I-SPD-1)") {
            withScriptedServer {
                script.answer(200, "null")

                sut(url).fetch("a b/c")

                nextRequest().url.encodedPath shouldBe "/api/audiobooks/a%20b%2Fc/playback-rate"
            }
        }

        // I-SPD-1, R13: unknown keys are ignored, and an integer rate decodes as a double
        it("#168 TC-16, TC-18: answers the rate for each shape of record (I-SPD-1, R13)") {
            withScriptedServer {
                checkAll(
                    Exhaustive.of(
                        """{"book_uuid":"b","playback_rate":1.25,"extra":{},"updated_at":1730000000}""" to 1.25,
                        """{"playback_rate":1}""" to 1.0,
                    ),
                ) { (body, rate) ->
                    script.answer(200, body)

                    sut(url).fetch(randomString()) shouldBe RateFetch.Found(rate)
                }
            }
        }

        context("when the listener never set a rate") {
            // I-SPD-4: the server answers the JSON literal null
            it("#168 TC-17: answers Unset (I-SPD-4)") {
                withScriptedServer {
                    script.answer(200, "null")

                    sut(url).fetch(randomString()) shouldBe RateFetch.Unset
                }
            }
        }

        // R13
        it("#168 TC-19: answers Failed for each HTTP error or unreadable body (R13)") {
            withScriptedServer {
                checkAll(Exhaustive.of(404 to "", 500 to "", 401 to "", 200 to "not json", 200 to """{"book_uuid":"b"}""")) {
                        (code, body) ->
                    script.answer(code, body)

                    sut(url).fetch(randomString()) shouldBe RateFetch.Failed
                }
            }
        }

        // R13
        it("#168 TC-20: answers Failed for a missing or unreachable server (R13)") {
            checkAll(Exhaustive.of(null, unreachableUrl())) { baseUrl ->
                sut(baseUrl).fetch(randomString()) shouldBe RateFetch.Failed
            }
        }
    }

    describe("writing a rate") {

        // R13, I-SPD-1: the body is exactly the one field
        it("#168 TC-21: puts the rate as JSON at the book's rate path (R13)") {
            withScriptedServer {
                val book = randomString()
                val rate = randomDouble(0.5, 3.0)
                script.answer(200, "{}")

                sut(url).put(book, rate)

                val request = nextRequest()
                request.method shouldBe "PUT"
                request.url.encodedPath shouldBe "/api/audiobooks/$book/playback-rate"
                request.headers["Content-Type"]!! shouldStartWith "application/json"
                request.body?.utf8() shouldBe """{"playback_rate":$rate}"""
            }
        }

        // R13, I-SPD-3
        it("#168 TC-21, TC-22, TC-23: classifies each status for the outbox (R13, I-SPD-3)") {
            withScriptedServer {
                checkAll(
                    Exhaustive.of(
                        200 to PutOutcome.Sent,
                        400 to PutOutcome.Drop,
                        404 to PutOutcome.Drop,
                        422 to PutOutcome.Drop,
                        401 to PutOutcome.Keep,
                        408 to PutOutcome.Keep,
                        429 to PutOutcome.Keep,
                        500 to PutOutcome.Keep,
                        503 to PutOutcome.Keep,
                    ),
                ) { (code, outcome) ->
                    script.answer(code)

                    sut(url).put(randomString(), randomDouble(0.5, 3.0)) shouldBe outcome
                }
            }
        }

        // R13, I-SPD-3
        it("#168 TC-23: answers Keep for a missing or unreachable server (R13, I-SPD-3)") {
            checkAll(Exhaustive.of(null, unreachableUrl())) { baseUrl ->
                sut(baseUrl).put(randomString(), randomDouble(0.5, 3.0)) shouldBe PutOutcome.Keep
            }
        }
    }
})
