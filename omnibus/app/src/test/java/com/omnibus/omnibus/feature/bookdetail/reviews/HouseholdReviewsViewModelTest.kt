package com.omnibus.omnibus.feature.bookdetail.reviews

import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.TestReviewsRepository
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdReviewsViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    val uuid = "uuid-1"

    /** A review by [userId] with a rating, and a spoiler when [spoiler] is set. */
    fun review(userId: Long, spoiler: Boolean = false) = Review(
        userId = userId,
        displayName = "Reader $userId",
        hasAvatar = false,
        rating = 4f,
        text = "Text $userId",
        privateNotes = null,
        isSpoiler = spoiler,
        updatedAtMillis = null,
    )

    fun loaded(vararg others: Review) =
        ReviewsState.Loaded(BookReviews(mine = null, others = others.toList(), summary = null))

    /** A ViewModel on the fakes, with its state kept active by [collecting]. */
    fun viewModel(
        reviews: TestReviewsRepository,
        readStatus: TestReadStatusRepository,
        collecting: CollectingScope,
    ) = HouseholdReviewsViewModel(uuid, reviews, readStatus, TimeSource { 1_000L })
        .also { collecting.collect(it.uiState) }

    describe("uiState") {

        it("TC-35: uiState starts Checking/Hidden and combines reviews and read status") {
            val reviews = TestReviewsRepository()
            val readStatus = TestReadStatusRepository()
            val collecting = CollectingScope()
            val vm = viewModel(reviews, readStatus, collecting)

            vm.uiState.value.stage shouldBe StageRatingModel.Checking
            vm.uiState.value.tab shouldBe ReviewsTabModel.Hidden

            reviews.seed(uuid, loaded(review(7, spoiler = true)))
            vm.uiState.value.stage.shouldBeInstanceOf<StageRatingModel.Average>()
            vm.uiState.value.tab.shouldBeInstanceOf<ReviewsTabModel.Listed>().entries.single().veiled shouldBe true

            readStatus.seed(uuid, ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, 1L, 1L)))
            vm.uiState.value.tab.shouldBeInstanceOf<ReviewsTabModel.Listed>().entries.single().veiled shouldBe false
            vm.uiState.value.nowMillis shouldBe 1_000L
            collecting.cancel()
        }

        it("TC-36: a revealed review stays revealed across new data") {
            val reviews = TestReviewsRepository()
            val collecting = CollectingScope()
            val vm = viewModel(reviews, TestReadStatusRepository(), collecting)
            reviews.seed(uuid, loaded(review(7, spoiler = true), review(8, spoiler = true)))

            fun veiled() = vm.uiState.value.tab.shouldBeInstanceOf<ReviewsTabModel.Listed>().entries.associate { it.userId to it.veiled }

            veiled() shouldBe mapOf(7L to true, 8L to true)
            vm.reveal(7)
            veiled() shouldBe mapOf(7L to false, 8L to true)

            reviews.seed(uuid, loaded(review(7, spoiler = true), review(8, spoiler = true)))
            veiled() shouldBe mapOf(7L to false, 8L to true)
            collecting.cancel()
        }
    }

    describe("reload, retry and reveal") {

        it("TC-37: reload refreshes once per new reloadKey") {
            val reviews = TestReviewsRepository()
            val collecting = CollectingScope()
            val vm = viewModel(reviews, TestReadStatusRepository(), collecting)

            vm.reload(0)
            vm.reload(0)
            vm.reload(1)

            reviews.refreshCalls shouldContainExactly listOf(uuid, uuid)
            collecting.cancel()
        }

        it("TC-38: retry refreshes the reviews") {
            val reviews = TestReviewsRepository()
            val collecting = CollectingScope()
            reviews.seed(uuid, ReviewsState.Failed)
            val vm = viewModel(reviews, TestReadStatusRepository(), collecting)
            reviews.refreshResults += loaded(review(3))

            vm.retry()

            reviews.refreshCalls shouldContainExactly listOf(uuid)
            vm.uiState.value.tab.shouldBeInstanceOf<ReviewsTabModel.Listed>()
            collecting.cancel()
        }
    }
})
