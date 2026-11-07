package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class RatingWordsTest : DescribeSpec({

    describe("the word for a rating") {
        it("TC-21: is its own for each of the ten half steps") {
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

        it("TC-22, TC-23: rounds each off-grid value to a half star first, with none below half a star") {
            checkAll(
                Exhaustive.of(
                    4.3f to RatingWord.LovedIt,
                    4.74f to RatingWord.LovedIt,
                    5.4f to RatingWord.AFavorite,
                    0.2f to null,
                    0f to null,
                    -1f to null,
                    null to null,
                ),
            ) { (rating, word) ->
                ratingWord(rating) shouldBe word
            }
        }
    }
})
