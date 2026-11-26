package com.omnibus.omnibus.data.libraries.remote

import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.data.audiobook.position.randomAudioPosition
import com.omnibus.omnibus.data.audiobook.position.randomListeningSessionReport
import com.omnibus.omnibus.data.audiobook.position.remote.AudioProgressJson
import com.omnibus.omnibus.data.audiobook.position.remote.AudioProgressUpdateRequestDto
import com.omnibus.omnibus.data.audiobook.position.remote.ListeningSessionReportDto
import com.omnibus.omnibus.data.network.TestAuthTokenProvider
import com.omnibus.omnibus.data.network.TestServerBaseUrlProvider
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withScriptedServer
import com.omnibus.omnibus.testing.dropped
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl.Companion.toHttpUrl

class DefaultInactiveLibraryApiTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    /**
     * An API whose client is saved for the active library's server and signed in with the active token, so a call must
     * use the server and token it is given.
     */
    fun sut(tokens: TestAuthTokenProvider = TestAuthTokenProvider(token = randomString())) = DefaultInactiveLibraryApi(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = testOmnibusHttpClient(
            baseUrlProvider = TestServerBaseUrlProvider("https://active.example".toHttpUrl()),
            authTokenProvider = tokens,
        ),
    )

    describe("sending to another library's server") {

        // R7: the call carries the token it was given, not the active library's
        it("#373 TC-7: sends the position to the progress path with the given token only") {
            withScriptedServer {
                val token = randomString()
                script.answer(200, "{}")

                sut().sendPosition(url, token, randomAudioPosition())

                val request = nextRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/progress"
                request.headers.values("Authorization") shouldBe listOf("Bearer $token")
            }
        }

        it("#373 TC-7: sends the position as its progress JSON") {
            withScriptedServer {
                val position = randomAudioPosition()
                script.answer(200, "{}")

                sut().sendPosition(url, randomString(), position)

                nextRequest().body?.utf8() shouldBe
                    AudioProgressJson.encodeToString(AudioProgressUpdateRequestDto.from(position))
            }
        }

        it("#373 TC-7: sends the sessions as a JSON array to the sessions path with the given token only") {
            withScriptedServer {
                val token = randomString()
                val batch: List<ListeningSessionReport> = List(2) { randomListeningSessionReport() }
                script.answer(200, "{}")

                sut().sendSessions(url, token, batch)

                val request = nextRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/progress/sessions"
                request.headers.values("Authorization") shouldBe listOf("Bearer $token")
                request.body?.utf8() shouldBe
                    AudioProgressJson.encodeToString(batch.map { ListeningSessionReportDto.from(it) })
            }
        }

        it("#373 TC-7: puts the rate to the book's rate path with the given token only") {
            withScriptedServer {
                val token = randomString()
                val book = randomString()
                val rate = randomDouble(0.5, 3.0)
                script.answer(200, "{}")

                sut().putRate(url, token, book, rate)

                val request = nextRequest()
                request.method shouldBe "PUT"
                request.url.encodedPath shouldBe "/api/audiobooks/$book/playback-rate"
                request.headers.values("Authorization") shouldBe listOf("Bearer $token")
                request.body?.utf8() shouldBe """{"playback_rate":$rate}"""
            }
        }

        it("#373 TC-7: posts an empty logout to the logout path with the given token only") {
            withScriptedServer {
                val token = randomString()
                script.answer(200, "{}")

                sut().logout(url, token)

                val request = nextRequest()
                request.method shouldBe "POST"
                request.url.encodedPath shouldBe "/api/auth/logout"
                request.headers.values("Authorization") shouldBe listOf("Bearer $token")
                request.body?.utf8().orEmpty() shouldBe ""
            }
        }

        it("#373 TC-7: answers true only for a 2xx") {
            withScriptedServer {
                checkAll(Exhaustive.of(200 to true, 204 to true, 299 to true, 300 to false, 400 to false)) { (code, sent) ->
                    script.answer(code)

                    sut().sendPosition(url, randomString(), randomAudioPosition()) shouldBe sent
                }
            }
        }
    }

    describe("a send that isn't accepted") {

        // R9, R6: each refusal is false, never a throw
        it("#373 TC-8: answers false for each refusal of a position, sessions or rate") {
            withScriptedServer {
                checkAll(Exhaustive.of(401, 404, 500)) { code ->
                    script.answer(code)

                    sut().sendPosition(url, randomString(), randomAudioPosition()) shouldBe false
                }
            }
        }

        it("#373 TC-8: answers false for a dropped connection") {
            withScriptedServer {
                script.answer(dropped())

                sut().putRate(url, randomString(), randomString(), randomDouble(0.5, 3.0)) shouldBe false
            }
        }

        it("#373 TC-8: answers false for a server that can't be reached") {
            sut().sendSessions(unreachableUrl(), randomString(), listOf(randomListeningSessionReport())) shouldBe false
        }

        // R7: a 401 from the removed library's server never clears the active library's token
        it("#373 TC-8: leaves the stored token alone on a 401 from a send") {
            withScriptedServer {
                val tokens = TestAuthTokenProvider(token = randomString())
                script.answer(401)

                sut(tokens).sendPosition(url, randomString(), randomAudioPosition())

                tokens.invalidateCalls shouldBe 0
            }
        }

        it("#373 TC-8: leaves the stored token alone on a 401 from a logout") {
            withScriptedServer {
                val tokens = TestAuthTokenProvider(token = randomString())
                script.answer(401)

                sut(tokens).logout(url, randomString())

                tokens.invalidateCalls shouldBe 0
            }
        }

        // R9: the logout's answer never matters
        it("#373 TC-8: returns normally from a logout that is refused") {
            withScriptedServer {
                checkAll(Exhaustive.of(401, 404, 500)) { code ->
                    script.answer(code)

                    sut().logout(url, randomString())
                }
            }
        }

        it("#373 TC-8: returns normally from a logout to a server that can't be reached") {
            sut().logout(unreachableUrl(), randomString())
        }
    }
})
