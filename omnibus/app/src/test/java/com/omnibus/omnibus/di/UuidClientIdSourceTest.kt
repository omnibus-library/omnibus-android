package com.omnibus.omnibus.di

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

class UuidClientIdSourceTest : DescribeSpec({

    describe("UuidClientIdSource") {

        // TC-10: each mint is a parseable 36-character UUID within the server's 64-character limit, and two mints differ.
        it("TC-10: mints distinct uuid strings within the server limit") {
            val source = UuidClientIdSource()
            val first = source.mint()
            val second = source.mint()

            UUID.fromString(first).toString() shouldBe first
            first.length shouldBe 36
            first.length shouldBeLessThanOrEqual 64
            second shouldNotBe first
        }
    }
})
