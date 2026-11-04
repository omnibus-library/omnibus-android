package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.cover.CoverImageResult
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.cover.FakeCoverRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.encodeUtf8

class DefaultCoverArtworkSourceTest : DescribeSpec({

    describe("cover results") {

        // TC-46
        it("maps cover results (AND-STK-8)") {
            val repository = FakeCoverRepository()
            val source = DefaultCoverArtworkSource(repository)
            val bytes = "cover".encodeUtf8()

            repository.result = CoverImageResult.Found(bytes, CoverSize.Lg, fromCache = false)
            source.artwork("book-1") shouldBe CoverArtwork.Found(bytes)

            repository.result = CoverImageResult.NotFound
            source.artwork("book-1") shouldBe CoverArtwork.NoCover

            repository.result = CoverImageResult.Error
            source.artwork("book-1") shouldBe CoverArtwork.Failed

            repository.requests shouldBe List(3) { "book-1" to CoverSize.Lg }
        }
    }
})
