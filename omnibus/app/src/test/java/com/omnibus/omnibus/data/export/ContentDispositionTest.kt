package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.export.remote.contentDispositionFilename
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ContentDispositionTest : DescribeSpec({

    describe("reading a file name from Content-Disposition") {
        it("#118 TC-30: prefers a valid filename*, across quoted, bare, spaced and mixed-case forms") {
            checkAll(
                Exhaustive.of(
                    "attachment; filename=\"x.epub\"; filename*=UTF-8''Caf%C3%A9%20Society.epub" to "Café Society.epub",
                    "attachment; filename=\"abc.kepub.epub\"" to "abc.kepub.epub",
                    "attachment; filename=abc.epub" to "abc.epub",
                    "Attachment; FileName*=UTF-8''a.pdf" to "a.pdf",
                    "attachment;  FILENAME = \"b.pdf\"" to "b.pdf",
                    "attachment; filename*=UTF-8''bad%ZZ.epub; filename=\"ok.epub\"" to "ok.epub",
                    "attachment; filename=\"a;b.epub\"" to "a;b.epub",
                ),
            ) { (header, expected) ->
                contentDispositionFilename(header) shouldBe expected
            }
        }

        context("when the header names no file") {
            it("#118 TC-30: gives null for a missing, empty and nameless header") {
                checkAll(Exhaustive.of(null, "", "attachment")) { header ->
                    contentDispositionFilename(header).shouldBeNull()
                }
            }
        }
    }
})
