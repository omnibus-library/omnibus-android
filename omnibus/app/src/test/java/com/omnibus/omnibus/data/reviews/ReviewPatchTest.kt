package com.omnibus.omnibus.data.reviews

import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReviewPatchTest : DescribeSpec({

    describe("applying a patch") {

        context("when there is no review") {

            it("TC-7: makes a new one with no attribution or change time") {
                val rating = randomRating()

                (null as Review?).applying(ReviewPatch(rating = Patch.Set(rating))) shouldBe
                    Review(0L, "", false, rating, null, null, isSpoiler = false, updatedAtMillis = null)
            }

            context("when the patch sets only the spoiler flag") {

                it("TC-10: makes nothing") {
                    (null as Review?).applying(ReviewPatch(isSpoiler = Patch.Set(true))).shouldBeNull()
                }
            }
        }

        context("when the review has text") {

            it("TC-7, TC-9: changes only the rating, regardless of whether it sets or clears it") {
                val review = randomReview().copy(text = randomString())

                checkAll(Exhaustive.of(randomRating(), null)) { rating ->
                    review.applying(ReviewPatch(rating = Patch.Set(rating))) shouldBe review.copy(rating = rating)
                }
            }
        }

        context("when the patch leaves the review empty") {

            it("TC-10: gives no review, regardless of whether the text is null or blank") {
                val review = randomReview().copy(rating = null, text = randomString(), privateNotes = null)

                checkAll(Exhaustive.of(null, "  ")) { text ->
                    review.applying(ReviewPatch(text = Patch.Set(text))).shouldBeNull()
                }
            }
        }
    }

    describe("whether a patch is a no-op") {

        it("TC-11: is one regardless of which field repeats its current value") {
            val rating = randomRating()
            val text = randomString()
            val notes = randomString()
            val spoiler = randomBoolean()
            val mine = randomReview().copy(rating = rating, text = text, privateNotes = notes, isSpoiler = spoiler)

            checkAll(
                Exhaustive.of(
                    ReviewPatch(),
                    ReviewPatch(rating = Patch.Set(rating)),
                    ReviewPatch(text = Patch.Set(text)),
                    ReviewPatch(privateNotes = Patch.Set(notes)),
                    ReviewPatch(isSpoiler = Patch.Set(spoiler)),
                ),
            ) { patch ->
                patch.isNoOpFor(mine) shouldBe true
            }
        }

        context("when there is no review") {

            it("TC-11: is one regardless of which empty value it sets") {
                checkAll(Exhaustive.of(ReviewPatch(), ReviewPatch(rating = Patch.Set(null)), ReviewPatch(text = Patch.Set("  ")))) { patch ->
                    patch.isNoOpFor(null) shouldBe true
                }
            }
        }

        it("TC-11: is not one regardless of whether it adds a rating or clears one") {
            val rated = randomReview().copy(rating = randomRating())

            checkAll(
                Exhaustive.of(
                    ReviewPatch(rating = Patch.Set(randomRating())) to null,
                    ReviewPatch(rating = Patch.Set(null)) to rated,
                ),
            ) { (patch, mine) ->
                patch.isNoOpFor(mine) shouldBe false
            }
        }
    }
})
