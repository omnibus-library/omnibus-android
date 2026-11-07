package com.omnibus.omnibus.data.network

import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.withMockServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import okhttp3.OkHttpClient
import okhttp3.Request

class OmnibusNetworkInterceptorTest : DescribeSpec({

    fun sut(): OkHttpClient = OkHttpClient.Builder().addInterceptor(OmnibusNetworkInterceptor()).build()

    describe("the Accept header") {

        context("when the request names none") {
            it("TC-183: defaults to JSON") {
                withMockServer { server ->
                    server.enqueue(reply(200))

                    sut().newCall(Request.Builder().url(server.url("/")).build()).execute().close()

                    server.nextRequest().headers.values("Accept") shouldBe listOf("application/json")
                }
            }
        }

        context("when the request names its own") {
            it("TC-184: keeps it, without adding a second") {
                withMockServer { server ->
                    server.enqueue(reply(200))
                    val accept = randomString()

                    sut().newCall(Request.Builder().url(server.url("/")).header("Accept", accept).build()).execute().close()

                    server.nextRequest().headers.values("Accept") shouldBe listOf(accept)
                }
            }
        }
    }
})
