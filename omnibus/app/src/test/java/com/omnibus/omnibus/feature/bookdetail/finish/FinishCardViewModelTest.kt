package com.omnibus.omnibus.feature.bookdetail.finish

import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.bookprogress.TestBookProgressRepository
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.highlights.TestHighlightsRepository
import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.TestReadStatusRepository
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.reviews.TestReviewsRepository
import com.omnibus.omnibus.data.reviews.randomRating
import com.omnibus.omnibus.data.reviews.randomReview
import com.omnibus.omnibus.data.series.randomSeriesMember
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import com.omnibus.omnibus.testing.CollectingScope
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

private fun known(status: ReadStatus) = ReadStatusState.Known(randomReadStatusRecord().copy(status = status))

private fun rated() = ReviewsState.Loaded(
    BookReviews(mine = randomReview().copy(rating = randomRating()), others = emptyList(), summary = null),
)

private fun randomSessions() = List(2) { ReadingSession(randomLong(), randomLong(), null, null) }

/** Runs [block] with [vm]'s state kept active, as a subscribed screen would. */
private inline fun collecting(vm: FinishCardViewModel, block: FinishCardViewModel.() -> Unit) {
    val scope = CollectingScope()
    scope.collect(vm.uiState)
    try {
        vm.block()
    } finally {
        scope.cancel()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FinishCardViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        status: TestReadStatusRepository = TestReadStatusRepository(),
        progress: TestBookProgressRepository = TestBookProgressRepository(),
        reviews: TestReviewsRepository = TestReviewsRepository(),
        highlights: TestHighlightsRepository = TestHighlightsRepository(),
    ) = FinishCardViewModel(bookUuid, status, progress, reviews, highlights)

    describe("the celebration") {

        context("when the reader marks the book Finished") {
            it("TC-20: celebrates once, however often the status arrives again") {
                val uuid = randomString()
                val status = TestReadStatusRepository()
                status.seed(uuid, known(ReadStatus.Reading))
                val vm = sut(uuid, status)

                status.seed(uuid, known(ReadStatus.Finished))
                status.seed(uuid, known(ReadStatus.Finished))

                vm.celebration.value shouldBe FinishCelebration(1, 1)
            }
        }

        context("when the book opens already finished") {
            it("TC-21: does not celebrate") {
                val uuid = randomString()
                val status = TestReadStatusRepository()
                status.seed(uuid, known(ReadStatus.Finished))

                sut(uuid, status).celebration.value shouldBe FinishCelebration(0, 0)
            }
        }

    }

    describe("tapping the cover") {

        context("when nothing has been celebrated") {
            it("TC-22: throws no burst") {
                val vm = sut()

                vm.replayBurst()

                vm.celebration.value shouldBe FinishCelebration(0, 0)
            }
        }

        context("when the book has been celebrated") {
            it("TC-22: throws the burst again") {
                val uuid = randomString()
                val status = TestReadStatusRepository()
                status.seed(uuid, known(ReadStatus.Reading))
                val vm = sut(uuid, status)
                status.seed(uuid, known(ReadStatus.Finished))

                vm.replayBurst()

                vm.celebration.value shouldBe FinishCelebration(1, 2)
            }
        }
    }

    describe("the card's reads") {

        context("when the book is not finished") {
            it("TC-23: reads no sessions") {
                val uuid = randomString()
                val status = TestReadStatusRepository()
                status.seed(uuid, known(ReadStatus.Reading))
                val progress = TestBookProgressRepository()

                sut(uuid, status, progress).reload(randomInt())

                progress.sessionsCalls.shouldBeEmpty()
            }
        }

        context("when the book is finished") {
            it("TC-24: loads its sessions, highlight count and reviews") {
                val uuid = randomString()
                val sessions = randomSessions()
                val reviewsState = rated()
                val status = TestReadStatusRepository()
                status.seed(uuid, known(ReadStatus.Finished))
                val progress = TestBookProgressRepository()
                progress.sessionsResults += ReadingSessionsResult.Success(sessions)
                val reviews = TestReviewsRepository()
                reviews.seed(uuid, reviewsState)
                val highlights = TestHighlightsRepository()
                highlights.seed(uuid, HighlightsState.Loaded(List(4) { randomHighlight() }))

                collecting(sut(uuid, status, progress, reviews, highlights)) {
                    reload(0)

                    uiState.value.sessions shouldBe sessions
                    uiState.value.highlightCount shouldBe 4
                    uiState.value.reviews shouldBe reviewsState
                }
            }
        }
    }

    describe("the statuses of the series members") {

        fun member(uuid: String) = randomSeriesMember().copy(uuid = uuid)

        it("TC-25: keeps the finished ones among the other members") {
            val uuid = randomString()
            val status = TestReadStatusRepository()
            status.seed(uuid, known(ReadStatus.Finished))
            status.seed("$uuid-done", known(ReadStatus.Finished))
            status.seed("$uuid-next", known(ReadStatus.Reading))
            val members = listOf(member("$uuid-done"), member(uuid), member("$uuid-next"))

            collecting(sut(uuid, status)) {
                loadSeriesStatuses(members, 0)

                uiState.value.finishedMembers shouldBe setOf("$uuid-done")
                status.fetchCalls shouldContainExactlyInAnyOrder listOf("$uuid-done", "$uuid-next")
            }
        }

        context("when the same key asks again") {
            it("TC-25: reads nothing more") {
                val status = TestReadStatusRepository()
                val members = listOf(member(randomString()))
                val vm = sut(status = status)
                vm.loadSeriesStatuses(members, 0)

                vm.loadSeriesStatuses(members, 0)

                status.fetchCalls.size shouldBe 1
            }
        }
    }
})
