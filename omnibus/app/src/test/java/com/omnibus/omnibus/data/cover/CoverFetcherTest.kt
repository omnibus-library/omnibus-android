package com.omnibus.omnibus.data.cover

import android.content.Context
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import okio.ByteString.Companion.encodeUtf8
import java.io.IOException

class CoverFetcherTest : DescribeSpec({

    val thumbnail = CoverThumbnail("book", CoverSize.Md)
    val options = Options(mockk<Context>(relaxed = true))

    describe("CoverKeyer") {

        it("TC-186: changes key once the clock moves into the next revalidation window") {
            var now = 0L
            val keyer = CoverKeyer { now }
            val before = keyer.key(thumbnail, options)

            now += CoverCacheKeys.REVALIDATE_AFTER_MILLIS

            (keyer.key(thumbnail, options) == before) shouldBe false
        }
    }

    describe("CoverFetcher") {

        fun fetcher(repository: CoverRepository) = CoverFetcher.Factory(repository)
            .create(thumbnail, options, mockk(relaxed = true))

        it("TC-187: asks the repository for the thumbnail's uuid and size") {
            val repository = FakeCoverRepository(CoverImageResult.NotFound)

            runCatching { fetcher(repository).fetch() }

            repository.requests shouldBe listOf("book" to CoverSize.Md)
        }

        it("TC-188: serves bytes from the server as a NETWORK source") {
            val repository = FakeCoverRepository(
                CoverImageResult.Found("art".encodeUtf8(), CoverSize.Md, fromCache = false),
            )

            val result = fetcher(repository).fetch().shouldBeInstanceOf<SourceFetchResult>()

            result.dataSource shouldBe DataSource.NETWORK
            result.mimeType shouldBe "image/webp"
            result.source.source().readUtf8() shouldBe "art"
        }

        it("TC-189: serves cached bytes as a DISK source") {
            val repository = FakeCoverRepository(
                CoverImageResult.Found("art".encodeUtf8(), CoverSize.Lg, fromCache = true),
            )

            val result = fetcher(repository).fetch().shouldBeInstanceOf<SourceFetchResult>()

            result.dataSource shouldBe DataSource.DISK
            result.source.source().readUtf8() shouldBe "art"
        }

        it("TC-190: fails with an IOException when the repository has no cover") {
            shouldThrow<IOException> { fetcher(FakeCoverRepository(CoverImageResult.NotFound)).fetch() }
        }

        it("TC-190: fails with an IOException when the repository errors") {
            shouldThrow<IOException> { fetcher(FakeCoverRepository(CoverImageResult.Error)).fetch() }
        }
    }
})
