package com.omnibus.omnibus.feature.bookdetail.reviews

import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.reviews.TestReviewsRepository
import com.omnibus.omnibus.data.reviews.randomReview
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A ViewModel on fresh fakes, with its state kept active by [collecting] until the test ends. */
private class Harness(val now: Long) {
    val uuid = randomString()
    val reviews = TestReviewsRepository()
    val readStatus = TestReadStatusRepository()
    val collecting = CollectingScope()
    val vm = HouseholdReviewsViewModel(uuid, reviews, readStatus, TimeSource { now })
        .also { collecting.collect(it.uiState) }

    /** Whether each listed review is veiled, by user id. */
    fun veiled() = vm.uiState.value.tab.shouldBeInstanceOf<ReviewsTabModel.Listed>().entries.associate { it.userId to it.veiled }
}

/** Runs [block] on a [Harness], and cancels its collection afterwards. */
private inline fun withHarness(now: Long = randomLong(), block: Harness.() -> Unit) {
    val harness = Harness(now)
    try {
        harness.block()
    } finally {
        harness.collecting.cancel()
    }
}

private fun loaded(vararg others: Review) =
    ReviewsState.Loaded(BookReviews(mine = null, others = others.toList(), summary = null))

private fun spoiler() = randomReview().copy(isSpoiler = true, text = randomString())

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdReviewsViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the state") {
        context("when the reader finishes the book") {
            it("TC-35: opens the spoilers") {
                withHarness {
                    val review = spoiler()
                    reviews.seed(uuid, loaded(review))

                    readStatus.seed(uuid, ReadStatusState.Known(randomReadStatusRecord().copy(status = ReadStatus.Finished)))

                    veiled() shouldBe mapOf(review.userId to false)
                }
            }
        }

        it("TC-35: carries the clock, for the entries' dates") {
            val now = randomLong()
            withHarness(now) {
                reviews.seed(uuid, loaded(randomReview()))

                vm.uiState.value.nowMillis shouldBe now
            }
        }
    }

    describe("revealing a spoiler") {
        context("when new reviews arrive") {
            it("TC-36: stays revealed") {
                withHarness {
                    val opened = spoiler()
                    val other = spoiler()
                    reviews.seed(uuid, loaded(opened, other))
                    vm.reveal(opened.userId)

                    reviews.seed(uuid, loaded(opened, other))

                    veiled() shouldBe mapOf(opened.userId to false, other.userId to true)
                }
            }
        }
    }

    describe("reloading") {
        it("TC-37: refreshes once per new key") {
            withHarness {
                vm.reload(0)
                vm.reload(0)
                vm.reload(1)

                reviews.refreshCalls shouldContainExactly listOf(uuid, uuid)
            }
        }
    }

    describe("retrying") {
        context("when the reviews failed to load") {
            it("TC-38: shows the reviews the refresh brings") {
                withHarness {
                    reviews.seed(uuid, ReviewsState.Failed)
                    reviews.refreshResults += loaded(randomReview())

                    vm.retry()

                    vm.uiState.value.tab.shouldBeInstanceOf<ReviewsTabModel.Listed>()
                }
            }
        }
    }
})
