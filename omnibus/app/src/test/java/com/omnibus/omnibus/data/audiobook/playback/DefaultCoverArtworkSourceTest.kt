package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.cover.CoverImageResult
import com.omnibus.omnibus.data.cover.CoverRepository
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.cover.TestCoverRepository
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class DefaultCoverArtworkSourceTest : DescribeSpec({

    fun sut(repository: CoverRepository = TestCoverRepository()) = DefaultCoverArtworkSource(repository)

    describe("reading a book's artwork") {
        context("when the book has a cover") {
            // TC-46
            it("gives the cover's bytes (AND-STK-8)") {
                val bytes = randomString().encodeUtf8()
                val repository = TestCoverRepository(CoverImageResult.Found(bytes, randomEnum<CoverSize>(), randomBoolean()))

                sut(repository).artwork(randomString()) shouldBe CoverArtwork.Found(bytes)
            }
        }

        context("when the book has no cover") {
            // TC-46
            it("reports no cover (AND-STK-8)") {
                val repository = TestCoverRepository(CoverImageResult.NotFound)

                sut(repository).artwork(randomString()) shouldBe CoverArtwork.NoCover
            }
        }

        context("when the cover cannot be fetched") {
            // TC-46
            it("reports a failure (AND-STK-8)") {
                val repository = TestCoverRepository(CoverImageResult.Error)

                sut(repository).artwork(randomString()) shouldBe CoverArtwork.Failed
            }
        }
    }
})
