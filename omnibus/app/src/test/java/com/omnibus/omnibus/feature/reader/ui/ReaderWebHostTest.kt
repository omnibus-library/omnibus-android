package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderWebHostTest : DescribeSpec({

    describe("the origins allowed to post to the bridge") {
        it("TC-27: are only the asset origin") {
            READER_MESSAGE_ORIGINS shouldBe setOf("https://appassets.androidplatform.net")
        }
    }
})
