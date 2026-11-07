package com.omnibus.omnibus.feature.bookdetail.finish

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.reviews.randomReview
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.data.series.randomSeriesMember
import com.omnibus.omnibus.feature.bookdetail.progress.ReadingSession
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import java.util.GregorianCalendar
import java.util.TimeZone

/** A UTC instant, so the day counts do not depend on the machine's zone. */
private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
    GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { clear(); set(year, month - 1, day, hour, minute) }.timeInMillis

private const val DAY = 86_400_000L
private val OCT12 = at(2026, 10, 12, 20)
private val FINISHED_OCT29 = at(2026, 10, 29, 22, 14)

/** Series member [n], whose uuid is "uuid-$n". */
private fun member(n: Int): SeriesMember = randomSeriesMember().copy(uuid = "uuid-$n")

/** Members 1 to 5 in series order. */
private fun fiveMembers() = (1..5).map(::member)

/** This book, in a series. */
private fun inSeries(): Book = randomBook().copy(uniqueIdentifier = "uuid-2", series = "Sherlock Holmes", seriesId = 7L)

/** A loaded reviews state with the reader's own [rating]. */
private fun reviewed(rating: Float?): ReviewsState.Loaded = ReviewsState.Loaded(
    BookReviews(mine = randomReview().copy(rating = rating), others = emptyList(), summary = null),
)

private fun known(status: ReadStatus): ReadStatusState.Known =
    ReadStatusState.Known(randomReadStatusRecord().copy(status = status))

/** Sessions starting on [starts]. */
private fun sessions(vararg starts: Long): List<ReadingSession> =
    starts.map { ReadingSession(startedAtMillis = it, durationSeconds = 600, startFraction = null, endFraction = null) }

class FinishRulesTest : DescribeSpec({

    describe("the next book in a series") {

        context("when a later book is unfinished") {
            it("TC-1: is the first of those") {
                nextBook(fiveMembers(), "uuid-2", setOf("uuid-1"))?.uuid shouldBe "uuid-3"
            }
        }

        context("when only earlier books are unfinished") {
            it("TC-2: is the earliest of those") {
                nextBook(fiveMembers(), "uuid-4", setOf("uuid-5"))?.uuid shouldBe "uuid-1"
            }
        }

        context("when every other book is finished") {
            it("TC-3: is none") {
                nextBook(listOf(member(1), member(2), member(3)), "uuid-2", setOf("uuid-1", "uuid-3")) shouldBe null
            }
        }

        context("when this book is not in the list") {
            it("TC-3: is none") {
                nextBook(fiveMembers(), "uuid-9", emptySet()) shouldBe null
            }
        }
    }

    describe("the one way on") {

        context("when the review is unknown") {
            it("TC-5: shows no row, while loading, offline or failed") {
                checkAll(Exhaustive.of(ReviewsState.Loading, ReviewsState.Offline, ReviewsState.Failed)) { state ->
                    finishAction(state, inSeries(), fiveMembers(), emptySet()) shouldBe null
                }
            }
        }

        context("when the book is unrated") {
            it("TC-6: asks for a review, with no review, no rating or a rating of zero") {
                val noReview = ReviewsState.Loaded(BookReviews(mine = null, others = emptyList(), summary = null))

                checkAll(Exhaustive.of(noReview, reviewed(null), reviewed(0f))) { state ->
                    finishAction(state, inSeries(), fiveMembers(), emptySet()) shouldBe FinishAction.Review
                }
            }
        }

        context("when the book is rated") {
            context("when there is nothing to point at") {
                it("TC-7: shows no row, with no series id, a blank series, unknown members or nothing left") {
                    checkAll(
                        Exhaustive.of(
                            Triple(inSeries().copy(seriesId = null), fiveMembers(), emptySet()),
                            Triple(inSeries().copy(series = " "), fiveMembers(), emptySet()),
                            Triple(inSeries(), null, emptySet()),
                            Triple(inSeries(), listOf(member(1), member(2)), setOf("uuid-1")),
                        ),
                    ) { (book, members, finished) ->
                        finishAction(reviewed(4f), book, members, finished) shouldBe null
                    }
                }
            }

            context("when a later book is unfinished") {
                it("TC-8: points at the next unfinished book") {
                    val members = fiveMembers()

                    finishAction(reviewed(4f), inSeries(), members, setOf("uuid-3")) shouldBe
                        FinishAction.UpNext(members[3], "Sherlock Holmes")
                }
            }
        }
    }

    describe("the totals") {

        context("when every part has something to count") {
            it("TC-9: counts days, sessions and highlights") {
                val starts = (0L until 13L).map { OCT12 + it * DAY }

                finishTotals(FINISHED_OCT29, sessions(*starts.toLongArray()), 4) shouldBe listOf(
                    FinishTotal.Days(17),
                    FinishTotal.Sessions(13),
                    FinishTotal.Highlights(4),
                )
            }
        }

        context("when there is no finish date") {
            it("TC-10: gives no totals") {
                finishTotals(null, sessions(OCT12), 4).shouldBeEmpty()
            }
        }

        context("when the sessions never loaded") {
            it("TC-11: leaves out the days and the sessions") {
                finishTotals(FINISHED_OCT29, null, 4) shouldBe listOf(FinishTotal.Highlights(4))
            }
        }

        context("when the book was finished within a day of starting") {
            it("TC-11: counts one day, finishing the same day or on an earlier clock") {
                checkAll(Exhaustive.of(OCT12 + 3 * 3_600_000L, OCT12 - DAY)) { finishedAt ->
                    finishTotals(finishedAt, sessions(OCT12), 0).first() shouldBe FinishTotal.Days(1)
                }
            }
        }
    }

    describe("whether a status change celebrates") {

        context("when a known unfinished status becomes Finished") {
            it("TC-12: celebrates, from Reading or Unread") {
                checkAll(Exhaustive.of(ReadStatus.Reading, ReadStatus.Unread)) { from ->
                    celebratesOnChange(known(from), known(ReadStatus.Finished)) shouldBe true
                }
            }
        }

        context("when the earlier status was not a known unfinished one") {
            it("TC-12: does not celebrate, from nothing, Unknown, Failed or Finished") {
                checkAll(
                    Exhaustive.of(null, ReadStatusState.Unknown, ReadStatusState.Failed, known(ReadStatus.Finished)),
                ) { previous ->
                    celebratesOnChange(previous, known(ReadStatus.Finished)) shouldBe false
                }
            }
        }
    }

    describe("the play latch") {

        it("TC-13: hands out each play once") {
            val latch = PlayLatch()

            latch.claim(1) shouldBe true
            latch.claim(1) shouldBe false
        }
    }

    describe("which books get the card") {

        context("when the book has a digital copy") {
            it("TC-14: shows it") {
                checkAll(Exhaustive.of("epub", "m4b")) { format ->
                    showsFinishCard(randomBook().copy(formats = listOf(format))) shouldBe true
                }
            }
        }

        context("when the book has no file") {
            it("TC-14: hides it, even with a physical copy") {
                showsFinishCard(randomBook().copy(formats = emptyList(), hasPhysical = true)) shouldBe false
            }
        }
    }
})
