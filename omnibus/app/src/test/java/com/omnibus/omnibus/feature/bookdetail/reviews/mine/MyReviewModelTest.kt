package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewsState
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class MyReviewModelTest : DescribeSpec({

    /** A review with the given parts. */
    fun review(rating: Float? = null, text: String? = null, notes: String? = null) =
        Review(0L, "", false, rating, text, notes, isSpoiler = false, updatedAtMillis = null)

    describe("the card's model") {

        it("TC-37: each reviews state maps to the card model it shows") {
            myReviewModel(ReviewsState.Loading) shouldBe MyReviewModel.Unknown(UnknownReason.Loading)
            myReviewModel(ReviewsState.Offline) shouldBe MyReviewModel.Unknown(UnknownReason.Offline)
            myReviewModel(ReviewsState.Failed) shouldBe MyReviewModel.Unknown(UnknownReason.Failed)
            myReviewModel(ReviewsState.Loaded(BookReviews(null, emptyList(), null))) shouldBe MyReviewModel.Known(null)

            val mine = review(rating = 4f)
            myReviewModel(ReviewsState.Loaded(BookReviews(mine, emptyList(), null))) shouldBe MyReviewModel.Known(mine)
        }
    }

    describe("the card's helpers") {

        it("TC-38: rating, hasReview, hasWriting, note and showsPrompt follow the review") {
            val rated = MyReviewModel.Known(review(rating = 4.5f, text = "Body"))
            rated.rating shouldBe 4.5f
            rated.hasReview shouldBe true
            rated.hasWriting shouldBe true
            rated.note() shouldBe MyReviewNote.Rated(4.5f)
            rated.showsPrompt shouldBe false

            val none = MyReviewModel.Known(null)
            none.rating shouldBe null
            none.hasReview shouldBe false
            none.hasWriting shouldBe false
            none.note() shouldBe MyReviewNote.NotRated
            none.showsPrompt shouldBe true

            val notesOnly = MyReviewModel.Known(review(notes = "private"))
            notesOnly.hasWriting shouldBe true
            notesOnly.note() shouldBe MyReviewNote.NotRated
            notesOnly.showsPrompt shouldBe true

            MyReviewModel.Unknown(UnknownReason.Loading).note() shouldBe MyReviewNote.Loading
            MyReviewModel.Unknown(UnknownReason.Offline).note() shouldBe MyReviewNote.Offline
            MyReviewModel.Unknown(UnknownReason.Failed).note() shouldBe MyReviewNote.Failed
            MyReviewModel.Unknown(UnknownReason.Failed).rating shouldBe null
            MyReviewModel.Unknown(UnknownReason.Failed).hasReview shouldBe false
            MyReviewModel.Unknown(UnknownReason.Failed).showsPrompt shouldBe false
        }
    }
})
