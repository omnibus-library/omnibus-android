package com.omnibus.omnibus.data.reader

import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.io.ByteArrayInputStream

class BookHttpResponseTest : DescribeSpec({

    describe("answering epub.js with a book file") {

        context("when the book opened") {

            it("TC-223: answers 200 with the book") {
                val stream = ByteArrayInputStream(ByteArray(0))
                val length = randomLong()

                val response = BookHttpResponse.of(BookFileResult.Opened(stream, length))

                response.status shouldBe 200
                response.body shouldBeSameInstanceAs stream
                response.headers["Content-Length"] shouldBe length.toString()
            }

            context("when its size is unknown") {

                it("TC-224: leaves out the length") {
                    val response = BookHttpResponse.of(BookFileResult.Opened(ByteArrayInputStream(ByteArray(0)), -1))

                    response.headers.containsKey("Content-Length") shouldBe false
                }
            }
        }

        context("when the book could not be opened") {

            it("TC-225: answers each failure with only its own status") {
                checkAll(
                    Exhaustive.of(
                        BookFileResult.Unauthenticated to 401,
                        BookFileResult.NotFound to 404,
                        BookFileResult.NoServer to 503,
                        BookFileResult.Error to 502,
                    ),
                ) { (failure, status) ->
                    val response = BookHttpResponse.of(failure)

                    response.status shouldBe status
                    response.body.shouldBeNull()
                }
            }
        }
    }
})
