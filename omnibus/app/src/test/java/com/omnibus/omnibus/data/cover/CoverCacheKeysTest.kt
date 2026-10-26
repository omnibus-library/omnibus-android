package com.omnibus.omnibus.data.cover

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CoverCacheKeysTest : DescribeSpec({

    describe("diskKey") {

        it("TC-129: is the SHA-256 of the path as hex") {
            CoverCacheKeys.diskKey("abc") shouldBe
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        }

        it("TC-130: is stable and differs per size") {
            CoverCacheKeys.diskKey("u", CoverSize.Md) shouldBe CoverCacheKeys.diskKey("api/thumbs/u/md")
            CoverCacheKeys.diskKey("u", CoverSize.Md) shouldNotBe CoverCacheKeys.diskKey("u", CoverSize.Sm)
        }
    }

    describe("memoryKey") {
        val thumbnail = CoverThumbnail("u", CoverSize.Md)
        val window = CoverCacheKeys.REVALIDATE_AFTER_MILLIS

        it("TC-131: is the same within a revalidation window") {
            CoverCacheKeys.memoryKey(thumbnail, 10 * window) shouldBe
                CoverCacheKeys.memoryKey(thumbnail, 10 * window + window - 1)
        }

        it("TC-132: changes in the next window so the entry is revalidated") {
            CoverCacheKeys.memoryKey(thumbnail, 10 * window) shouldNotBe
                CoverCacheKeys.memoryKey(thumbnail, 11 * window)
        }

        it("TC-133: maps back to the disk key") {
            CoverCacheKeys.diskKeyOfMemoryKey(CoverCacheKeys.memoryKey(thumbnail, 123456789)) shouldBe
                CoverCacheKeys.diskKey("u", CoverSize.Md)
        }
    }
})
