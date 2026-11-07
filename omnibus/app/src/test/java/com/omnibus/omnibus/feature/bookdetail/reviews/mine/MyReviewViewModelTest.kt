package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.TestReviewsRepository
import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewError
import com.omnibus.omnibus.data.reviews.ReviewPatch
import com.omnibus.omnibus.data.reviews.ReviewWriteResult
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class MyReviewViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    val uuid = "uuid-1"

    /** A review with the given parts, as the signed-in reader's, with no change time. */
    fun review(rating: Float? = null, text: String? = null, notes: String? = null) =
        Review(0L, "", false, rating, text, notes, isSpoiler = false, updatedAtMillis = null)

    /** A Loaded state whose own review is [mine]. */
    fun loaded(mine: Review?) = ReviewsState.Loaded(BookReviews(mine, emptyList(), null))

    /** A ViewModel on [fake] and [pulse], with its state kept active by [collecting]. */
    fun viewModel(
        fake: TestReviewsRepository,
        pulse: ReviewSavePulse,
        collecting: CollectingScope,
    ) = MyReviewViewModel(uuid, fake, pulse).also { collecting.collect(it.uiState) }

    describe("rating") {

        it("TC-39: a rating on no review and a change of it each make one write, and each save counts once") {
            val fake = TestReviewsRepository()
            val pulse = ReviewSavePulse()
            val collecting = CollectingScope()
            fake.seed(uuid, loaded(null))
            val vm = viewModel(fake, pulse, collecting)

            vm.rate(4.5f)
            vm.uiState.value.card shouldBe MyReviewModel.Known(review(rating = 4.5f))
            pulse.pulses(uuid).value shouldBe 1

            fake.seed(uuid, loaded(review(rating = 3f)))
            vm.rate(4f)
            fake.updateCalls shouldContainExactly listOf(
                uuid to ReviewPatch(rating = Patch.Set(4.5f)),
                uuid to ReviewPatch(rating = Patch.Set(4f)),
            )
            vm.uiState.value.card shouldBe MyReviewModel.Known(review(rating = 4f))
            pulse.pulses(uuid).value shouldBe 2
            collecting.cancel()
        }

        it("TC-41: clearing the rating sends only the rating and keeps the text and notes, and counts as a save") {
            val fake = TestReviewsRepository()
            val pulse = ReviewSavePulse()
            val collecting = CollectingScope()
            fake.seed(uuid, loaded(review(rating = 4f, text = "Body", notes = "private")))
            val vm = viewModel(fake, pulse, collecting)

            vm.rate(null)

            fake.updateCalls shouldContainExactly listOf(uuid to ReviewPatch(rating = Patch.Set(null)))
            vm.uiState.value.card shouldBe MyReviewModel.Known(review(text = "Body", notes = "private"))
            pulse.pulses(uuid).value shouldBe 1
            collecting.cancel()
        }

        it("TC-42: rating the current rating, or clearing an unrated review, makes no call and no pulse") {
            val fake = TestReviewsRepository()
            val pulse = ReviewSavePulse()
            val collecting = CollectingScope()
            fake.seed(uuid, loaded(review(rating = 4f)))
            val vm = viewModel(fake, pulse, collecting)

            vm.rate(4f)
            fake.seed(uuid, loaded(null))
            vm.rate(null)

            fake.updateCalls.shouldBeEmpty()
            pulse.pulses(uuid).value shouldBe 0
            vm.uiState.value.saveFailure shouldBe null
            collecting.cancel()
        }

        it("TC-43: while the review is unknown, rating, clearing and deleting do nothing") {
            val fake = TestReviewsRepository()
            val pulse = ReviewSavePulse()
            val fresh = MyReviewViewModel(uuid, fake, pulse)
            fresh.uiState.value.card shouldBe MyReviewModel.Unknown(UnknownReason.Loading)

            listOf(ReviewsState.Loading, ReviewsState.Offline, ReviewsState.Failed).forEach { state ->
                fake.seed(uuid, state)
                val collecting = CollectingScope()
                val vm = viewModel(fake, pulse, collecting)

                vm.rate(4f)
                vm.rate(null)
                vm.delete()

                vm.uiState.value.saveFailure shouldBe null
                collecting.cancel()
            }
            fake.updateCalls.shouldBeEmpty()
            fake.deleteCalls.shouldBeEmpty()
            pulse.pulses(uuid).value shouldBe 0
        }

        it("TC-44: a failed save rolls back, names the failure, and posts nothing for a NotLoaded write") {
            val fake = TestReviewsRepository()
            val pulse = ReviewSavePulse()
            val collecting = CollectingScope()
            fake.seed(uuid, loaded(review(rating = 4f)))
            val vm = viewModel(fake, pulse, collecting)

            val offline = listOf(ReviewError.NoServer, ReviewError.Unreachable)
            val other = listOf(ReviewError.SessionExpired, ReviewError.Http(500), ReviewError.BookNotFound)
            offline.forEach { error ->
                fake.writeResults.addLast(ReviewWriteResult.Failed(error))
                vm.rate(4.5f)
                vm.uiState.value.card shouldBe MyReviewModel.Known(review(rating = 4f))
                vm.uiState.value.saveFailure shouldBe MyReviewSaveFailure(MyReviewSaveFailure.Kind.Rate, offline = true)
                vm.saveFailureShown()
            }
            other.forEach { error ->
                fake.writeResults.addLast(ReviewWriteResult.Failed(error))
                vm.rate(4.5f)
                vm.uiState.value.saveFailure shouldBe MyReviewSaveFailure(MyReviewSaveFailure.Kind.Rate, offline = false)
                vm.saveFailureShown()
            }

            fake.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.NotLoaded))
            vm.rate(4.5f)
            vm.uiState.value.saveFailure shouldBe null

            pulse.pulses(uuid).value shouldBe 0
            collecting.cancel()
        }

        it("TC-44: a failure stays until it is shown, and a later failure sets it again") {
            val fake = TestReviewsRepository()
            val collecting = CollectingScope()
            fake.seed(uuid, loaded(review(rating = 4f)))
            val vm = viewModel(fake, ReviewSavePulse(), collecting)

            fake.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Http(500)))
            vm.rate(4.5f)
            vm.uiState.value.saveFailure shouldBe MyReviewSaveFailure(MyReviewSaveFailure.Kind.Rate, offline = false)

            vm.saveFailureShown()
            vm.uiState.value.saveFailure shouldBe null

            fake.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Http(500)))
            vm.rate(3.5f)
            vm.uiState.value.saveFailure shouldBe MyReviewSaveFailure(MyReviewSaveFailure.Kind.Rate, offline = false)
            collecting.cancel()
        }
    }

    describe("deleting") {

        it("TC-46: a delete makes one call and shows no review, and nothing happens without a review") {
            val fake = TestReviewsRepository()
            val pulse = ReviewSavePulse()
            val collecting = CollectingScope()
            fake.seed(uuid, loaded(review(rating = 4f, text = "Body")))
            val vm = viewModel(fake, pulse, collecting)

            vm.delete()

            fake.deleteCalls shouldContainExactly listOf(uuid)
            vm.uiState.value.card shouldBe MyReviewModel.Known(null)
            pulse.pulses(uuid).value shouldBe 0

            vm.delete()
            fake.deleteCalls.size shouldBe 1
            collecting.cancel()
        }

        it("TC-46: a failed delete shows its failure and restores the review") {
            val fake = TestReviewsRepository()
            val collecting = CollectingScope()
            fake.seed(uuid, loaded(review(rating = 4f)))
            val vm = viewModel(fake, ReviewSavePulse(), collecting)

            fake.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Unreachable))
            vm.delete()

            vm.uiState.value.saveFailure shouldBe MyReviewSaveFailure(MyReviewSaveFailure.Kind.Delete, offline = true)
            vm.uiState.value.card shouldBe MyReviewModel.Known(review(rating = 4f))
            collecting.cancel()
        }
    }

    describe("the save pulse") {

        it("TC-47: a refresh, a delete, a failure and an unchanged write do not count as a save") {
            val fake = TestReviewsRepository()
            val pulse = ReviewSavePulse()
            val collecting = CollectingScope()
            fake.seed(uuid, loaded(review(rating = 4f)))
            val vm = viewModel(fake, pulse, collecting)

            fake.refreshResults.addLast(loaded(review(rating = 3f)))
            vm.reload(0)
            vm.uiState.value.card shouldBe MyReviewModel.Known(review(rating = 3f))
            pulse.pulses(uuid).value shouldBe 0

            vm.delete()
            pulse.pulses(uuid).value shouldBe 0

            fake.seed(uuid, loaded(review(rating = 4f)))
            fake.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Http(500)))
            vm.rate(4.5f)
            pulse.pulses(uuid).value shouldBe 0

            fake.writeResults.addLast(ReviewWriteResult.Unchanged)
            vm.rate(3.5f)
            pulse.pulses(uuid).value shouldBe 0
            collecting.cancel()
        }
    }

    describe("reloading") {

        it("TC-48: a reload refreshes once per new key, and the card reads the refreshed rating") {
            val fake = TestReviewsRepository()
            val collecting = CollectingScope()
            val vm = viewModel(fake, ReviewSavePulse(), collecting)

            vm.reload(0)
            vm.reload(0)
            fake.refreshCalls.size shouldBe 1

            fake.refreshResults.addLast(loaded(review(rating = 4.5f)))
            vm.reload(1)
            fake.refreshCalls.size shouldBe 2
            vm.uiState.value.card shouldBe MyReviewModel.Known(review(rating = 4.5f))
            collecting.cancel()
        }
    }
})
