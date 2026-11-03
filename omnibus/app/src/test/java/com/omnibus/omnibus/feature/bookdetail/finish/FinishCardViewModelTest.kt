package com.omnibus.omnibus.feature.bookdetail.finish

import com.omnibus.omnibus.data.bookprogress.BookProgressError
import com.omnibus.omnibus.data.bookprogress.FakeBookProgressRepository
import com.omnibus.omnibus.data.bookprogress.ReadingSessionsResult
import com.omnibus.omnibus.data.highlights.FakeHighlightsRepository
import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.readstatus.FakeReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.FakeReviewsRepository
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.util.GregorianCalendar
import java.util.TimeZone

private const val UUID = "uuid-2"

/** A UTC instant, so the dates do not depend on the machine's zone. */
private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

private val FINISHED_OCT29 = at(2026, 10, 29, 22, 14)

/** A Finished status with its finish time. */
private fun finishedRecord(finishedAt: Long? = FINISHED_OCT29): ReadStatusState.Known =
    ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, 1L, finishedAt))

/** A Reading status. */
private fun reading(): ReadStatusState.Known = ReadStatusState.Known(ReadStatusRecord(ReadStatus.Reading, 1L, null))

/** Reviews loaded with the reader's own rating of 4. */
private fun rated(): ReviewsState.Loaded = ReviewsState.Loaded(
    BookReviews(mine = Review(0, "", false, 4f, null, null, false, null), others = emptyList(), summary = null),
)

/** A loaded list of [count] highlights, numbered from 1. */
private fun loadedHighlights(count: Int): HighlightsState.Loaded = HighlightsState.Loaded(
    (1..count).map { Highlight(it.toLong(), null, null, HighlightColor.Amber, null, null, null) },
)

/** Three sessions, the first starting on Oct 12 at 20:00. */
private val threeSessions = listOf(
    ReadingSession(at(2026, 10, 12, 20), 600, null, null),
    ReadingSession(at(2026, 10, 13, 20), 900, null, null),
    ReadingSession(at(2026, 10, 14, 20), 1_200, null, null),
)

/** A ViewModel with its fakes, whose state is kept active by [collecting] until the block ends. */
private class Harness(
    val status: FakeReadStatusRepository,
    val progress: FakeBookProgressRepository,
    val reviews: FakeReviewsRepository,
    val highlights: FakeHighlightsRepository,
) {
    /** Keeps the shared state active, as a subscribed screen would. */
    private val collecting = CollectingScope()

    /** The ViewModel under test. */
    val vm = FinishCardViewModel(UUID, status, progress, reviews, highlights)
        .also { collecting.collect(it.uiState) }

    /** Stops the collection. */
    fun close() = collecting.cancel()
}

/** Runs [block] on a fresh [Harness] built from the given fakes, then stops its collection. */
private inline fun withHarness(
    status: FakeReadStatusRepository = FakeReadStatusRepository(),
    progress: FakeBookProgressRepository = FakeBookProgressRepository(),
    reviews: FakeReviewsRepository = FakeReviewsRepository(),
    highlights: FakeHighlightsRepository = FakeHighlightsRepository(),
    block: Harness.() -> Unit,
) {
    val harness = Harness(status, progress, reviews, highlights)
    try {
        harness.block()
    } finally {
        harness.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class FinishCardViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("the celebration") {

        it("TC-20: marking the book Finished celebrates once") {
            val status = FakeReadStatusRepository()
            status.seed(UUID, reading())
            withHarness(status = status) {
                vm.celebration.value shouldBe FinishCelebration(0, 0)
                status.seed(UUID, finishedRecord())
                vm.celebration.value shouldBe FinishCelebration(1, 1)
                status.seed(UUID, finishedRecord())
                vm.celebration.value shouldBe FinishCelebration(1, 1)
            }
        }

        it("TC-21: opening an already finished book does not celebrate") {
            val already = FakeReadStatusRepository()
            already.seed(UUID, finishedRecord())
            withHarness(status = already) {
                vm.celebration.value shouldBe FinishCelebration(0, 0)
            }

            val unknown = FakeReadStatusRepository()
            withHarness(status = unknown) {
                status.seed(UUID, finishedRecord())
                vm.celebration.value shouldBe FinishCelebration(0, 0)
            }

            val failed = FakeReadStatusRepository()
            failed.seed(UUID, ReadStatusState.Failed)
            withHarness(status = failed) {
                status.seed(UUID, finishedRecord())
                vm.celebration.value shouldBe FinishCelebration(0, 0)
            }
        }

        it("TC-22: a cover tap throws the burst again only after a celebration") {
            val status = FakeReadStatusRepository()
            status.seed(UUID, reading())
            withHarness(status = status) {
                vm.replayBurst()
                vm.celebration.value shouldBe FinishCelebration(0, 0)

                status.seed(UUID, finishedRecord())
                vm.celebration.value shouldBe FinishCelebration(1, 1)

                vm.replayBurst()
                vm.celebration.value shouldBe FinishCelebration(1, 2)
            }
        }
    }

    describe("the reads") {

        it("TC-23: an unfinished book reads nothing") {
            val status = FakeReadStatusRepository()
            status.seed(UUID, reading())
            withHarness(status = status) {
                vm.reload(0)
                vm.uiState.value.finished shouldBe false
                progress.sessionsCalls.shouldBeEmpty()
                reviews.refreshCalls.shouldBeEmpty()
                highlights.refreshCalls.shouldBeEmpty()

                status.seed(UUID, finishedRecord())
                progress.sessionsCalls shouldBe listOf(UUID)
                vm.uiState.value.finished shouldBe true
            }
        }

        it("TC-24: a finished book loads its totals and reloads them per key") {
            val status = FakeReadStatusRepository()
            status.seed(UUID, finishedRecord())
            val progress = FakeBookProgressRepository()
            val reviews = FakeReviewsRepository()
            reviews.seed(UUID, rated())
            val highlights = FakeHighlightsRepository()
            highlights.seed(UUID, loadedHighlights(4))

            withHarness(status, progress, reviews, highlights) {
                progress.sessionsResults += ReadingSessionsResult.Success(threeSessions)
                vm.reload(0)
                vm.uiState.value.sessions shouldBe threeSessions
                vm.uiState.value.highlightCount shouldBe 4
                vm.uiState.value.reviews shouldBe rated()

                progress.sessionsResults += ReadingSessionsResult.Failed(BookProgressError.Unreachable)
                vm.reload(1)
                progress.sessionsCalls.size shouldBe 2
                highlights.refreshCalls shouldBe listOf(UUID)
                vm.uiState.value.sessions shouldBe threeSessions
                vm.uiState.value.reviews shouldBe rated()
            }
        }

        it("TC-25: member statuses are read once per key, without this book") {
            val status = FakeReadStatusRepository()
            status.seed(UUID, finishedRecord())
            status.readResults += ReadStatusState.Known(ReadStatusRecord(ReadStatus.Finished, 1L, null))
            status.readResults += ReadStatusState.Failed
            status.readResults += reading()
            val members = listOf("uuid-1", UUID, "uuid-3", "uuid-4").map {
                SeriesMember(it, it, null, null, null, false)
            }

            withHarness(status = status) {
                vm.loadSeriesStatuses(members, 0)
                status.fetchCalls shouldBe listOf("uuid-1", "uuid-3", "uuid-4")
                vm.uiState.value.finishedMembers shouldBe setOf("uuid-1")

                vm.loadSeriesStatuses(members, 0)
                status.fetchCalls.size shouldBe 3

                vm.loadSeriesStatuses(members, 1)
                status.fetchCalls.size shouldBe 6
            }
        }
    }
})
