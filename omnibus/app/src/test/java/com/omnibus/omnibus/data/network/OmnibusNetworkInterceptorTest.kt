package com.omnibus.omnibus.data.network

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request

class OmnibusNetworkInterceptorTest : DescribeSpec({

    lateinit var server: MockWebServer
    val client = OkHttpClient.Builder().addInterceptor(OmnibusNetworkInterceptor()).build()

    beforeEach {
        server = MockWebServer().apply { start() }
        server.enqueue(MockResponse.Builder().code(200).build())
    }

    afterEach { server.close() }

    describe("Accept") {

        it("TC-183: defaults to application/json when the request names none") {
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()

            server.takeRequest().headers.values("Accept") shouldBe listOf("application/json")
        }

        it("TC-184: keeps the request's own value, without adding a second one") {
            client.newCall(
                Request.Builder().url(server.url("/")).header("Accept", "image/webp,image/*;q=0.8").build(),
            ).execute().close()

            server.takeRequest().headers.values("Accept") shouldBe listOf("image/webp,image/*;q=0.8")
        }
    }
})
