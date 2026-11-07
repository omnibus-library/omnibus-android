package com.omnibus.omnibus.data.cover

import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CoverCacheKeysTest : DescribeSpec({

    val window = CoverCacheKeys.REVALIDATE_AFTER_MILLIS

    describe("the disk key") {
        it("TC-130: differs per size of one book") {
            val uuid = randomString()

            CoverCacheKeys.diskKey(uuid, CoverSize.Md) shouldNotBe CoverCacheKeys.diskKey(uuid, CoverSize.Sm)
        }
    }

    describe("the memory key") {
        context("when two reads fall in one revalidation window") {
            it("TC-131: is the same") {
                val thumbnail = randomCoverThumbnail()
                val windowStart = randomLong(0L..1_000_000L) * window

                CoverCacheKeys.memoryKey(thumbnail, windowStart) shouldBe
                    CoverCacheKeys.memoryKey(thumbnail, windowStart + window - 1)
            }
        }

        context("when a read falls in the next revalidation window") {
            it("TC-132: changes, so the entry is revalidated") {
                val thumbnail = randomCoverThumbnail()
                val windowStart = randomLong(0L..1_000_000L) * window

                CoverCacheKeys.memoryKey(thumbnail, windowStart) shouldNotBe
                    CoverCacheKeys.memoryKey(thumbnail, windowStart + window)
            }
        }

        it("TC-133: maps back to the disk key") {
            val thumbnail = randomCoverThumbnail()

            CoverCacheKeys.diskKeyOfMemoryKey(CoverCacheKeys.memoryKey(thumbnail, randomLong())) shouldBe
                CoverCacheKeys.diskKey(thumbnail.uuid, thumbnail.size)
        }
    }
})
