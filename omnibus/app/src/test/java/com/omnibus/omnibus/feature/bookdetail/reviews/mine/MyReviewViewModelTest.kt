package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewError
import com.omnibus.omnibus.data.reviews.ReviewPatch
import com.omnibus.omnibus.data.reviews.ReviewWriteResult
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.reviews.TestReviewsRepository
import com.omnibus.omnibus.data.reviews.randomReview
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A ViewModel on fresh fakes, with its state kept active by [collecting] until the test ends. */
private class Harness {
    val uuid = randomString()
    val reviews = TestReviewsRepository()
    val pulse = ReviewSavePulse()
    val collecting = CollectingScope()
    val vm = MyReviewViewModel(uuid, reviews, pulse).also { collecting.collect(it.uiState) }

    /** Seeds the reader's own review as [mine]. */
    fun seedMine(mine: Review?) = reviews.seed(uuid, ReviewsState.Loaded(BookReviews(mine, emptyList(), null)))
}

/** Runs [block] on a [Harness], and cancels its collection afterwards. */
private inline fun withHarness(block: Harness.() -> Unit) {
    val harness = Harness()
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MyReviewViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("rating") {
        it("TC-39: writes only the rating, for each of a new value, a clear") {
            checkAll(Exhaustive.of(4.5f, null)) { rating ->
                withHarness {
                    seedMine(randomReview().copy(rating = 4f))

                    vm.rate(rating)

                    reviews.updateCalls shouldContainExactly listOf(uuid to ReviewPatch(rating = Patch.Set(rating)))
                }
            }
        }

        it("TC-39, TC-47: counts only a saved rating, across each answer") {
            checkAll(
                Exhaustive.of(
                    ReviewWriteResult.Saved(randomReview()) to 1,
                    ReviewWriteResult.Failed(ReviewError.Http(500)) to 0,
                    ReviewWriteResult.Unchanged to 0,
                ),
            ) { (result, count) ->
                withHarness {
                    seedMine(randomReview().copy(rating = 4f))
                    reviews.writeResults.addLast(result)

                    vm.rate(4.5f)

                    pulse.pulses(uuid).value shouldBe count
                }
            }
        }

        context("when the write fails") {
            it("TC-44: says whether it was offline, posting nothing for a write never sent, for each error") {
                fun failure(offline: Boolean) = MyReviewSaveFailure(MyReviewSaveFailure.Kind.Rate, offline)

                checkAll(
                    Exhaustive.of(
                        ReviewError.NoServer to failure(offline = true),
                        ReviewError.Unreachable to failure(offline = true),
                        ReviewError.SessionExpired to failure(offline = false),
                        ReviewError.Http(500) to failure(offline = false),
                        ReviewError.BookNotFound to failure(offline = false),
                        ReviewError.NotLoaded to null,
                    ),
                ) { (error, posted) ->
                    withHarness {
                        seedMine(randomReview().copy(rating = 4f))
                        reviews.writeResults.addLast(ReviewWriteResult.Failed(error))

                        vm.rate(4.5f)

                        vm.uiState.value.saveFailure shouldBe posted
                    }
                }
            }

            context("when the failure has been shown") {
                it("TC-44: clears it") {
                    withHarness {
                        seedMine(randomReview().copy(rating = 4f))
                        reviews.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Http(500)))
                        vm.rate(4.5f)

                        vm.saveFailureShown()

                        vm.uiState.value.saveFailure shouldBe null
                    }
                }
            }
        }
    }

    describe("deleting") {
        context("when the reader has a review") {
            it("TC-46: deletes it") {
                withHarness {
                    seedMine(randomReview())

                    vm.delete()

                    reviews.deleteCalls shouldContainExactly listOf(uuid)
                }
            }
        }

        context("when the reader has no review") {
            it("TC-46: deletes nothing") {
                withHarness {
                    seedMine(null)

                    vm.delete()

                    reviews.deleteCalls.shouldBeEmpty()
                }
            }
        }

        context("when the delete fails") {
            it("TC-46: shows a delete failure") {
                withHarness {
                    seedMine(randomReview())
                    reviews.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Unreachable))

                    vm.delete()

                    vm.uiState.value.saveFailure shouldBe MyReviewSaveFailure(MyReviewSaveFailure.Kind.Delete, offline = true)
                }
            }
        }
    }

    describe("reloading") {
        it("TC-48: refreshes once per new key") {
            withHarness {
                vm.reload(0)
                vm.reload(0)
                vm.reload(1)

                reviews.refreshCalls shouldContainExactly listOf(uuid, uuid)
            }
        }
    }
})
