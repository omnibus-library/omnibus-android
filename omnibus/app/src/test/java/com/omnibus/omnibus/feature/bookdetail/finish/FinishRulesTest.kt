package com.omnibus.omnibus.feature.bookdetail.finish

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.util.GregorianCalendar
import java.util.TimeZone

/** A UTC instant, so the day counts do not depend on the machine's zone. */
private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

private const val DAY = 86_400_000L
private val OCT12 = at(2026, 10, 12, 20)
private val FINISHED_OCT29 = at(2026, 10, 29, 22, 14)

/** A book in the test library, with the fields the rules read. */
private fun book(
    uuid: String = "uuid-2",
    formats: List<String> = listOf("epub"),
    hasPhysical: Boolean = false,
    series: String? = null,
    seriesId: Long? = null,
): Book = Book(
    id = 1,
    uniqueIdentifier = uuid,
    filename = "the-hound.epub",
    title = "The Hound",
    creators = emptyList(),
    coverUrl = null,
    accent = null,
    formats = formats,
    series = series,
    seriesIndex = null,
    hasPhysical = hasPhysical,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
    seriesId = seriesId,
)

/** Series member [n], whose uuid is "uuid-$n". */
private fun member(n: Int): SeriesMember = SeriesMember("uuid-$n", "Book $n", "$n", null, null, false)

/** Members 1 to 5 in series order. */
private val fiveMembers = (1..5).map(::member)

/** A loaded reviews state with the reader's own [rating], or no review of their own when null. */
private fun reviewed(rating: Float?, hasReview: Boolean = true): ReviewsState.Loaded = ReviewsState.Loaded(
    BookReviews(
        mine = if (hasReview) Review(0, "", false, rating, null, null, false, null) else null,
        others = emptyList(),
        summary = null,
    ),
)

/** A known status record. */
private fun known(status: ReadStatus, finishedAt: Long? = null): ReadStatusState.Known =
    ReadStatusState.Known(ReadStatusRecord(status, 1L, finishedAt))

/** Sessions starting on [starts], each lasting ten minutes. */
private fun sessions(vararg starts: Long): List<ReadingSession> =
    starts.map { ReadingSession(startedAtMillis = it, durationSeconds = 600, startFraction = null, endFraction = null) }

class FinishRulesTest : DescribeSpec({

    describe("the next book in a series") {

        it("TC-1: the next book is the first unfinished one after this") {
            nextBook(fiveMembers, "uuid-2", setOf("uuid-1"))?.uuid shouldBe "uuid-3"
        }

        it("TC-2: with nothing unfinished after this, the earliest unfinished one") {
            nextBook(fiveMembers, "uuid-4", setOf("uuid-5"))?.uuid shouldBe "uuid-1"
        }

        it("TC-3: nothing left, or this book not in the list, gives no next book") {
            nextBook(listOf(member(1), member(2), member(3)), "uuid-2", setOf("uuid-1", "uuid-3")) shouldBe null
            nextBook(fiveMembers, "uuid-9", emptySet()) shouldBe null
            nextBook(listOf(member(1)), "uuid-1", emptySet()) shouldBe null
        }

        it("TC-4: a status that has not loaded counts as unfinished, and this book is never next") {
            nextBook(listOf(member(1), member(2), member(3)), "uuid-2", emptySet())?.uuid shouldBe "uuid-3"
            nextBook(listOf(member(1), member(2), member(3)), "uuid-3", emptySet())?.uuid shouldBe "uuid-1"
        }
    }

    describe("the one way on") {

        it("TC-5: an unknown review shows no row") {
            val inSeries = book(series = "Sherlock Holmes", seriesId = 7L)
            finishAction(ReviewsState.Loading, inSeries, fiveMembers, emptySet()) shouldBe null
            finishAction(ReviewsState.Offline, inSeries, fiveMembers, emptySet()) shouldBe null
            finishAction(ReviewsState.Failed, inSeries, fiveMembers, emptySet()) shouldBe null
        }

        it("TC-6: an unrated book asks for a review, series or not") {
            val inSeries = book(series = "Sherlock Holmes", seriesId = 7L)
            finishAction(reviewed(rating = null, hasReview = false), inSeries, fiveMembers, emptySet()) shouldBe
                FinishAction.Review
            finishAction(reviewed(rating = null), inSeries, fiveMembers, emptySet()) shouldBe FinishAction.Review
            finishAction(reviewed(rating = 0f), inSeries, fiveMembers, emptySet()) shouldBe FinishAction.Review
        }

        it("TC-7: a rated standalone book, unknown members or nothing left show no row") {
            val rated = reviewed(rating = 4f)
            val members = listOf(member(1), member(2))
            val standalone = book(series = "Sherlock Holmes", seriesId = null)
            finishAction(rated, standalone, members, emptySet()) shouldBe null

            val blankSeries = book(series = " ", seriesId = 7L)
            finishAction(rated, blankSeries, members, emptySet()) shouldBe null

            val inSeries = book(uuid = "uuid-2", series = "Sherlock Holmes", seriesId = 7L)
            finishAction(rated, inSeries, null, emptySet()) shouldBe null
            finishAction(rated, inSeries, members, setOf("uuid-1")) shouldBe null
        }

        it("TC-8: a rated book in a series points at the next unfinished book") {
            val inSeries = book(uuid = "uuid-2", series = "Sherlock Holmes", seriesId = 7L)
            finishAction(reviewed(rating = 4f), inSeries, fiveMembers, setOf("uuid-3")) shouldBe
                FinishAction.UpNext(member(4), "Sherlock Holmes")
        }
    }

    describe("the totals") {

        it("TC-9: the totals count days, sessions and highlights") {
            val starts = (0L until 13L).map { OCT12 + it * DAY }
            finishTotals(FINISHED_OCT29, sessions(*starts.toLongArray()), 4) shouldBe listOf(
                FinishTotal.Days(17),
                FinishTotal.Sessions(13),
                FinishTotal.Highlights(4),
            )
        }

        it("TC-10: no finish date, no totals") {
            finishTotals(null, sessions(OCT12), 4) shouldBe emptyList()
        }

        it("TC-11: parts with nothing to count are left out, and days are at least one") {
            finishTotals(FINISHED_OCT29, null, 4) shouldBe listOf(FinishTotal.Highlights(4))
            finishTotals(FINISHED_OCT29, emptyList(), 0).shouldBeEmpty()
            finishTotals(OCT12 + 3 * 3_600_000L, sessions(OCT12), 0) shouldBe
                listOf(FinishTotal.Days(1), FinishTotal.Sessions(1))
            finishTotals(OCT12 - DAY, sessions(OCT12), 0) shouldBe
                listOf(FinishTotal.Days(1), FinishTotal.Sessions(1))
        }
    }

    describe("the celebration") {

        it("TC-12: only a change from a known unfinished status to Finished celebrates") {
            val finished = known(ReadStatus.Finished, FINISHED_OCT29)
            celebratesOnChange(null, finished) shouldBe false
            celebratesOnChange(ReadStatusState.Unknown, finished) shouldBe false
            celebratesOnChange(ReadStatusState.Failed, finished) shouldBe false
            celebratesOnChange(known(ReadStatus.Reading), finished) shouldBe true
            celebratesOnChange(known(ReadStatus.Unread), finished) shouldBe true
            celebratesOnChange(finished, finished) shouldBe false
            celebratesOnChange(known(ReadStatus.Reading), known(ReadStatus.Reading)) shouldBe false
            celebratesOnChange(known(ReadStatus.Reading), ReadStatusState.Unknown) shouldBe false
        }

        it("TC-13: the latch hands out each play once") {
            val latch = PlayLatch()
            latch.isPending(0) shouldBe false
            latch.isPending(1) shouldBe true
            latch.claim(1) shouldBe true
            latch.claim(1) shouldBe false
            latch.claim(0) shouldBe false
            latch.claim(3) shouldBe true
            latch.isPending(2) shouldBe false
        }
    }

    describe("the card's book") {

        it("TC-14: the card needs an ebook or an audiobook") {
            showsFinishCard(book(formats = listOf("epub"))) shouldBe true
            showsFinishCard(book(formats = listOf("m4b"))) shouldBe true
            showsFinishCard(book(formats = emptyList())) shouldBe false
            showsFinishCard(book(formats = emptyList(), hasPhysical = true)) shouldBe false
        }
    }
})
