package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.export.remote.contentDispositionFilename
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ContentDispositionTest : DescribeSpec({

    describe("contentDispositionFilename") {

        it("#118 TC-30: reads the file name, preferring a valid filename* and percent-decoding it") {
            val cases: List<Pair<String?, String?>> = listOf(
                "attachment; filename=\"x.epub\"; filename*=UTF-8''Caf%C3%A9%20Society.epub" to "Café Society.epub",
                "attachment; filename=\"abc.kepub.epub\"" to "abc.kepub.epub",
                "attachment; filename=abc.epub" to "abc.epub",
                "Attachment; FileName*=UTF-8''a.pdf" to "a.pdf",
                "attachment;  FILENAME = \"b.pdf\"" to "b.pdf",
                null to null,
                "" to null,
                "attachment" to null,
                "attachment; filename*=UTF-8''bad%ZZ.epub; filename=\"ok.epub\"" to "ok.epub",
                "attachment; filename=\"a;b.epub\"" to "a;b.epub",
            )
            cases.forEach { (header, expected) ->
                withClue("header=$header") {
                    contentDispositionFilename(header) shouldBe expected
                }
            }
        }
    }
})
