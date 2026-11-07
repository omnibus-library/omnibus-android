package com.omnibus.omnibus.data.auth.remote

import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private val JSON = "application/json; charset=utf-8".toMediaType()

private fun credentials(): String = """{"username":"${randomString()}","password":"${randomString()}"}"""

private fun RecordedRequest.bodyText(): String = body!!.utf8()

private fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(bodyText()).jsonObject

class AuthClientMetadataInterceptorTest : DescribeSpec({

    fun sut(clientInfo: ClientInfo = TestClientInfo(randomString(), randomString())): AuthClientMetadataInterceptor =
        AuthClientMetadataInterceptor(clientInfo)

    /** Sends a request through [interceptor] and returns it as the server received it. */
    fun send(
        server: MockWebServer,
        path: String,
        interceptor: AuthClientMetadataInterceptor = sut(),
        method: String = "POST",
        body: String = credentials(),
        mediaType: okhttp3.MediaType = JSON,
    ): RecordedRequest {
        server.enqueue(MockResponse.Builder().code(200).build())
        val request = Request.Builder().url(server.url(path)).method(method, body.toRequestBody(mediaType)).build()
        OkHttpClient.Builder().addInterceptor(interceptor).build().newCall(request).execute().close()
        return server.takeRequest()
    }

    describe("annotating auth requests") {
        context("when a POST goes to an auth path") {
            it("adds the client metadata to the original fields on both the login and register paths") {
                withMockServer { server ->
                    checkAll(Exhaustive.of("/api/auth/login", "/api/auth/register")) { path ->
                        val device = randomString()
                        val version = randomString()
                        val username = randomString()
                        val password = randomString()

                        val received = send(
                            server,
                            path,
                            interceptor = sut(TestClientInfo(device, version)),
                            body = """{"username":"$username","password":"$password"}""",
                        )

                        received.json() shouldBe JsonObject(
                            mapOf(
                                "username" to JsonPrimitive(username),
                                "password" to JsonPrimitive(password),
                                "client_kind" to JsonPrimitive("android"),
                                "device_name" to JsonPrimitive(device),
                                "client_version" to JsonPrimitive(version),
                            ),
                        )
                    }
                }
            }

            it("keeps the request's content type") {
                withMockServer { server ->
                    send(server, "/api/auth/login").headers["Content-Type"]!! shouldStartWith
                        "application/json; charset=utf-8"
                }
            }

            context("when the server has a path prefix") {
                it("adds the client metadata") {
                    withMockServer { server ->
                        send(server, "/omnibus/api/auth/login").json()["client_kind"] shouldBe JsonPrimitive("android")
                    }
                }
            }

            context("when the body isn't a JSON object") {
                it("passes it through unchanged") {
                    withMockServer { server ->
                        val body = randomString()

                        send(
                            server,
                            "/api/auth/login",
                            body = body,
                            mediaType = "text/plain".toMediaType(),
                        ).bodyText() shouldBe body
                    }
                }
            }
        }

        context("when a POST goes to another path") {
            it("leaves the body unchanged") {
                withMockServer { server ->
                    val body = credentials()

                    send(server, "/api/books", body = body).bodyText() shouldBe body
                }
            }
        }

        context("when a request to an auth path isn't a POST") {
            it("leaves the body unchanged") {
                withMockServer { server ->
                    val body = credentials()

                    send(server, "/api/auth/login", method = "PUT", body = body).bodyText() shouldBe body
                }
            }
        }
    }
})
