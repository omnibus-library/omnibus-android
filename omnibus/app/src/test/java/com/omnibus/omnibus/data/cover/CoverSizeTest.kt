package com.omnibus.omnibus.data.cover

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class CoverSizeTest : DescribeSpec({

    describe("fallbacks") {

        it("TC-126: tries the other sizes largest first") {
            CoverSize.Sm.fallbacks shouldBe listOf(CoverSize.Lg, CoverSize.Md)
            CoverSize.Md.fallbacks shouldBe listOf(CoverSize.Lg, CoverSize.Sm)
            CoverSize.Lg.fallbacks shouldBe listOf(CoverSize.Md, CoverSize.Sm)
        }
    }

    describe("path") {

        it("TC-127: names the thumbnail endpoint relative to the server") {
            CoverSize.Md.path("abc-123") shouldBe "api/thumbs/abc-123/md"
        }
    }
})
