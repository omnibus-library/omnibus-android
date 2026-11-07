package com.omnibus.omnibus.data.cover

import android.content.Context
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import io.mockk.mockk
import java.io.IOException
import okio.ByteString.Companion.encodeUtf8

class CoverFetcherTest : DescribeSpec({

    fun sut(
        repository: CoverRepository = TestCoverRepository(),
        thumbnail: CoverThumbnail = randomCoverThumbnail(),
    ): Fetcher = CoverFetcher.Factory(repository)
        .create(thumbnail, Options(mockk<Context>(relaxed = true)), mockk(relaxed = true))

    describe("keying a cover request") {
        context("when the clock moves into the next revalidation window") {
            it("TC-186: changes the key") {
                val time = TestTimeSource(randomLong())
                val thumbnail = randomCoverThumbnail()
                val options = Options(mockk<Context>(relaxed = true))
                val keyer = CoverKeyer(time)
                val before = keyer.key(thumbnail, options)

                time.advanceBy(CoverCacheKeys.REVALIDATE_AFTER_MILLIS)

                keyer.key(thumbnail, options) shouldNotBe before
            }
        }
    }

    describe("fetching a cover") {
        it("TC-187: asks the repository for the thumbnail's uuid and size") {
            val repository = TestCoverRepository()
            val thumbnail = randomCoverThumbnail()

            runCatching { sut(repository, thumbnail).fetch() }

            repository.requests shouldBe listOf(thumbnail.uuid to thumbnail.size)
        }

        context("when the repository has the cover") {
            it("TC-188: serves its bytes") {
                val bytes = randomString()
                val repository = TestCoverRepository(
                    CoverImageResult.Found(bytes.encodeUtf8(), randomEnum<CoverSize>(), fromCache = randomBoolean()),
                )

                val result = sut(repository).fetch().shouldBeInstanceOf<SourceFetchResult>()

                result.source.source().readUtf8() shouldBe bytes
            }

            it("TC-189: labels bytes from the server as NETWORK and cached bytes as DISK") {
                checkAll(Exhaustive.of(false to DataSource.NETWORK, true to DataSource.DISK)) { (fromCache, source) ->
                    val repository = TestCoverRepository(
                        CoverImageResult.Found(randomString().encodeUtf8(), randomEnum<CoverSize>(), fromCache),
                    )

                    sut(repository).fetch().shouldBeInstanceOf<SourceFetchResult>().dataSource shouldBe source
                }
            }
        }

        context("when the repository has no image to give") {
            it("TC-190: fails with an IOException for NotFound and Error") {
                checkAll(Exhaustive.of(CoverImageResult.NotFound, CoverImageResult.Error)) { result ->
                    shouldThrow<IOException> { sut(TestCoverRepository(result)).fetch() }
                }
            }
        }
    }
})
