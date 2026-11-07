package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.reviews.randomReview
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class MyReviewModelTest : DescribeSpec({

    describe("the card's model") {
        it("TC-37: follows each reviews state") {
            val mine = randomReview()

            checkAll(
                Exhaustive.of(
                    ReviewsState.Loading to MyReviewModel.Unknown(UnknownReason.Loading),
                    ReviewsState.Offline to MyReviewModel.Unknown(UnknownReason.Offline),
                    ReviewsState.Failed to MyReviewModel.Unknown(UnknownReason.Failed),
                    ReviewsState.Loaded(BookReviews(null, emptyList(), null)) to MyReviewModel.Known(null),
                    ReviewsState.Loaded(BookReviews(mine, emptyList(), null)) to MyReviewModel.Known(mine),
                ),
            ) { (state, model) ->
                myReviewModel(state) shouldBe model
            }
        }
    }

    describe("the note under the stars") {
        it("TC-38: follows each kind of model") {
            checkAll(
                Exhaustive.of(
                    MyReviewModel.Known(randomReview().copy(rating = 4.5f)) to MyReviewNote.Rated(4.5f),
                    MyReviewModel.Known(randomReview().copy(rating = null)) to MyReviewNote.NotRated,
                    MyReviewModel.Known(null) to MyReviewNote.NotRated,
                    MyReviewModel.Unknown(UnknownReason.Loading) to MyReviewNote.Loading,
                    MyReviewModel.Unknown(UnknownReason.Offline) to MyReviewNote.Offline,
                    MyReviewModel.Unknown(UnknownReason.Failed) to MyReviewNote.Failed,
                ),
            ) { (model, note) ->
                model.note() shouldBe note
            }
        }
    }

    describe("whether the pencil edits") {
        it("TC-38: follows the writing of each kind of model") {
            checkAll(
                Exhaustive.of(
                    MyReviewModel.Known(randomReview().copy(text = randomString(), privateNotes = null)) to true,
                    MyReviewModel.Known(randomReview().copy(text = null, privateNotes = randomString())) to true,
                    MyReviewModel.Known(randomReview().copy(text = null, privateNotes = null)) to false,
                    MyReviewModel.Known(null) to false,
                    MyReviewModel.Unknown(UnknownReason.Failed) to false,
                ),
            ) { (model, edits) ->
                model.hasWriting shouldBe edits
            }
        }
    }

    describe("whether the prompt to write shows") {
        it("TC-38: is only for a known review with no text, across each kind of model") {
            checkAll(
                Exhaustive.of(
                    MyReviewModel.Known(null) to true,
                    MyReviewModel.Known(randomReview().copy(text = null)) to true,
                    MyReviewModel.Known(randomReview().copy(text = randomString())) to false,
                    MyReviewModel.Unknown(UnknownReason.Failed) to false,
                ),
            ) { (model, prompts) ->
                model.showsPrompt shouldBe prompts
            }
        }
    }
})
