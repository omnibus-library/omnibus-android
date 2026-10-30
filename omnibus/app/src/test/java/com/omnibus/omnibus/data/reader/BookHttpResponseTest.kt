package com.omnibus.omnibus.data.reader

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayInputStream

class BookHttpResponseTest : DescribeSpec({

    describe("of") {

        it("TC-223: answers an opened book with 200, its length and its stream") {
            val stream = ByteArrayInputStream(ByteArray(4))

            val response = BookHttpResponse.of(BookFileResult.Opened(stream, length = 4))

            response.status shouldBe 200
            response.headers["Content-Length"] shouldBe "4"
            response.body shouldBe stream
        }

        it("TC-224: omits Content-Length when the size is unknown") {
            val response = BookHttpResponse.of(BookFileResult.Opened(ByteArrayInputStream(ByteArray(0)), length = -1))

            response.headers.containsKey("Content-Length") shouldBe false
        }

        it("TC-225: answers each failure with its own non-2xx status and no body") {
            BookHttpResponse.of(BookFileResult.Unauthenticated).status shouldBe 401
            BookHttpResponse.of(BookFileResult.NotFound).status shouldBe 404
            BookHttpResponse.of(BookFileResult.NoServer).status shouldBe 503
            BookHttpResponse.of(BookFileResult.Error).status shouldBe 502
            listOf(
                BookFileResult.Unauthenticated,
                BookFileResult.NotFound,
                BookFileResult.NoServer,
                BookFileResult.Error,
            ).forEach { BookHttpResponse.of(it).body shouldBe null }
        }
    }
})
