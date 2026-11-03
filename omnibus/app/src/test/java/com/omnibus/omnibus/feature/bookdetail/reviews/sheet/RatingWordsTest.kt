package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.feature.bookdetail.reviews.halfStarRound
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class RatingWordsTest : DescribeSpec({

    describe("ratingWord") {

        it("TC-21: each of the ten half steps has its word") {
            (1..10).map { ratingWord(it / 2f) } shouldBe listOf(
                RatingWord.NotForMe,
                RatingWord.DidntWork,
                RatingWord.HardGoing,
                RatingWord.HadItsMoments,
                RatingWord.MixedFeelings,
                RatingWord.LikedIt,
                RatingWord.LikedItALot,
                RatingWord.ReallyGood,
                RatingWord.LovedIt,
                RatingWord.AFavorite,
            )
        }

        it("TC-22: null, zero and negative ratings have no word") {
            ratingWord(null) shouldBe null
            ratingWord(0f) shouldBe null
            ratingWord(-1f) shouldBe null
        }

        it("TC-23: off-grid values round to a half star before the word is chosen") {
            ratingWord(4.3f) shouldBe ratingWord(halfStarRound(4.3f))
            ratingWord(4.3f) shouldBe RatingWord.LovedIt
            ratingWord(4.74f) shouldBe ratingWord(halfStarRound(4.74f))
            ratingWord(4.74f) shouldBe RatingWord.LovedIt
            ratingWord(0.2f) shouldBe ratingWord(halfStarRound(0.2f))
            ratingWord(0.2f) shouldBe null
            ratingWord(5.4f) shouldBe ratingWord(halfStarRound(5.4f))
            ratingWord(5.4f) shouldBe RatingWord.AFavorite
        }
    }
})
