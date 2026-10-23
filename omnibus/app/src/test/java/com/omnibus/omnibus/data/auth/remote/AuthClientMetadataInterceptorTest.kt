package com.omnibus.omnibus.data.auth.remote

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
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
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class AuthClientMetadataInterceptorTest : DescribeSpec({

    lateinit var server: MockWebServer
    lateinit var client: OkHttpClient

    fun send(path: String, method: String = "POST", body: RequestBody? = null): RecordedRequest {
        server.enqueue(MockResponse.Builder().code(200).build())
        val request = Request.Builder().url(server.url(path)).method(method, body).build()
        client.newCall(request).execute().close()
        return server.takeRequest()
    }

    fun RecordedRequest.json(): JsonObject = Json.parseToJsonElement(body!!.utf8()).jsonObject

    beforeEach {
        server = MockWebServer().apply { start() }
        client = OkHttpClient.Builder()
            .addInterceptor(AuthClientMetadataInterceptor(FakeClientInfo(DEVICE, VERSION)))
            .build()
    }

    afterEach { server.close() }

    describe("on auth POSTs") {

        listOf("/api/auth/login", "/api/auth/register").forEach { path ->

            it("adds client metadata to $path and keeps the original fields") {
                send(path, body = CREDENTIALS.toRequestBody(JSON)).json() shouldBe JsonObject(
                    mapOf(
                        "username" to JsonPrimitive("hunter"),
                        "password" to JsonPrimitive("hunter2"),
                        "client_kind" to JsonPrimitive("android"),
                        "device_name" to JsonPrimitive(DEVICE),
                        "client_version" to JsonPrimitive(VERSION),
                    )
                )
            }
        }

        it("matches the path suffix, so a base URL with a path prefix still works") {
            send("/omnibus/api/auth/login", body = CREDENTIALS.toRequestBody(JSON))
                .json()["client_kind"] shouldBe JsonPrimitive("android")
        }

        it("keeps the request's content type") {
            send("/api/auth/login", body = CREDENTIALS.toRequestBody(JSON))
                .headers["Content-Type"]!! shouldStartWith "application/json; charset=utf-8"
        }

        it("passes a body that isn't a JSON object through unchanged") {
            send("/api/auth/login", body = "not json".toRequestBody(TEXT)).body!!.utf8() shouldBe "not json"
        }
    }

    describe("on other requests") {

        it("leaves POSTs to other paths unchanged") {
            send("/api/books", body = CREDENTIALS.toRequestBody(JSON)).body!!.utf8() shouldBe CREDENTIALS
        }

        it("leaves non-POST requests to auth paths unchanged") {
            send("/api/auth/login", method = "PUT", body = CREDENTIALS.toRequestBody(JSON))
                .body!!.utf8() shouldBe CREDENTIALS
        }

        it("leaves requests without a body unchanged") {
            send("/api/auth/login", method = "GET").body.shouldBeNull()
        }
    }
}) {
    private companion object {
        const val DEVICE = "Pixel 9"
        const val VERSION = "1.2.3"
        const val CREDENTIALS = """{"username":"hunter","password":"hunter2"}"""
        val JSON = "application/json; charset=utf-8".toMediaType()
        val TEXT = "text/plain".toMediaType()
    }
}
