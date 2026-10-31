package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.reader.ReaderAssets
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderWebHostTest : DescribeSpec({

    describe("READER_MESSAGE_ORIGINS") {

        it("TC-27: allows only the asset origin to post to the bridge") {
            READER_MESSAGE_ORIGINS shouldBe setOf("https://appassets.androidplatform.net")
            READER_MESSAGE_ORIGINS shouldBe setOf(ReaderAssets.ORIGIN)
            ReaderAssets.ORIGIN shouldBe "https://appassets.androidplatform.net"
        }
    }
})
