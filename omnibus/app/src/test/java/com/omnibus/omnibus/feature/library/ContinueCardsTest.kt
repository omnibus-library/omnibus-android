package com.omnibus.omnibus.feature.library

import com.omnibus.omnibus.data.library.PositionConfidence
import com.omnibus.omnibus.data.library.ProgressFormat.Listening
import com.omnibus.omnibus.data.library.ProgressFormat.Reading
import com.omnibus.omnibus.data.library.ResolvedPosition
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.about
import com.omnibus.omnibus.data.library.randomResumePoint
import com.omnibus.omnibus.data.library.withRecord
import com.omnibus.omnibus.feature.library.PositionLabel.Chapter
import com.omnibus.omnibus.feature.library.PositionLabel.InProgress
import com.omnibus.omnibus.feature.library.PositionLabel.Part
import com.omnibus.omnibus.feature.library.PositionLabel.PercentRead
import com.omnibus.omnibus.feature.library.PositionLabel.TimeLeft
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ContinueCardsTest : DescribeSpec({

    fun randomNow() = randomLong(1_000_000_000_000L..2_000_000_000_000L)

    /** A book with a uuid of its own, in the given [formats]. */
    fun book(vararg formats: String) = randomBook().copy(uniqueIdentifier = randomString(8..16), formats = formats.toList())

    fun listeningPoint(position: Double? = 600.0, total: Double? = 3600.0, rate: Double? = null) = randomResumePoint()
        .about(book("m4b"))
        .copy(playbackRate = rate, audioPart = null, audioPartCount = null)
        .withRecord { copy(format = Listening, audioPositionSeconds = position, totalDurationSeconds = total, resolved = null) }

    describe("the medium a card presents") {
        it("#22 TC-5: falls back from a requested one the book lacks, for each format mix") {
            checkAll(
                Exhaustive.of(
                    Triple(emptyList<String>(), Listening, Listening),
                    Triple(emptyList(), Reading, Reading),
                    Triple(listOf("m4b"), Listening, Listening),
                    Triple(listOf("epub"), Listening, Reading),
                    Triple(listOf("m4b"), Reading, Listening),
                    Triple(listOf("epub", "m4b"), Reading, Reading),
                    Triple(listOf("epub", "m4b"), Listening, Listening),
                ),
            ) { (formats, requested, presented) ->
                randomBook().copy(formats = formats).resumeMedium(requested) shouldBe presented
            }
        }
    }

    describe("a card's progress fraction") {
        context("when listening") {
            it("#22 TC-6: is the position over the duration, clamped at both ends") {
                checkAll(Exhaustive.of(300.0 to 0.25, 1500.0 to 1.0, -10.0 to 0.0)) { (position, fraction) ->
                    listeningPoint(position, 1200.0).fraction(Listening) shouldBe fraction
                }
            }

            it("#22 TC-6: is null without an honest denominator, for no or zero duration or no position") {
                checkAll(Exhaustive.of(300.0 to null, 300.0 to 0.0, null to 1200.0)) { (position, total) ->
                    listeningPoint(position, total).fraction(Listening) shouldBe null
                }
            }
        }

        context("when reading") {
            it("#22 TC-6: is the percent, clamped at both ends") {
                checkAll(Exhaustive.of(42.0 to 0.42, 140.0 to 1.0, -5.0 to 0.0)) { (percent, fraction) ->
                    randomResumePoint().withRecord { copy(format = Reading, progressPercent = percent) }.fraction(Reading) shouldBe fraction
                }
            }
        }

        context("when presented as another medium") {
            it("#22 TC-6: is null") {
                randomResumePoint().withRecord { copy(format = Reading, totalDurationSeconds = 1200.0, audioPositionSeconds = 300.0) }
                    .fraction(Listening) shouldBe null
            }
        }
    }

    describe("a card's position label") {
        fun high() = ResolvedPosition(
            chapterOrdinal = 3,
            chaptersTotal = 12,
            chapterTitle = null,
            percentThroughBook = null,
            confidence = PositionConfidence.High,
        )

        fun listeningWith(resolved: ResolvedPosition? = null, part: Int? = null, partCount: Int? = null) = randomResumePoint()
            .copy(audioPart = part, audioPartCount = partCount, playbackRate = null)
            .withRecord { copy(format = Listening, audioPositionSeconds = 600.0, totalDurationSeconds = 3600.0, resolved = resolved) }

        context("when listening") {
            it("#22 TC-7: prefers a confident chapter over the part") {
                positionLabelOf(listeningWith(high(), part = 2, partCount = 4), Listening) shouldBe Chapter(3, 12)
            }

            it("#22 TC-7: is the part without a chapter") {
                positionLabelOf(listeningWith(part = 2, partCount = 4), Listening) shouldBe Part(2, 4)
            }

            it("#22 TC-7: falls through to time left, for a low-confidence or partial chapter, or a part without a count") {
                checkAll(
                    Exhaustive.of(
                        listeningWith(high().copy(confidence = PositionConfidence.Low)),
                        listeningWith(high().copy(chapterOrdinal = null)),
                        listeningWith(high().copy(chaptersTotal = null)),
                        listeningWith(part = 2),
                    ),
                ) { point ->
                    positionLabelOf(point, Listening) shouldBe TimeLeft(3000)
                }
            }

            it("#22 TC-7: scales time left by the playback rate") {
                positionLabelOf(listeningPoint(rate = 1.5), Listening) shouldBe TimeLeft(2000)
            }

            it("#22 TC-7: counts a missing, non-positive or non-finite rate as normal speed") {
                checkAll(Exhaustive.of(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) { rate ->
                    positionLabelOf(listeningPoint(rate = rate), Listening) shouldBe TimeLeft(3000)
                }
            }

            context("with a position past the duration") {
                it("#22 TC-7: has no time left") {
                    positionLabelOf(listeningPoint(position = 4000.0), Listening) shouldBe TimeLeft(0)
                }
            }
        }

        context("when reading") {
            it("#22 TC-7: rounds to a whole percent read") {
                checkAll(Exhaustive.of(42.4 to 42, 99.6 to 100, 0.0 to 0)) { (percent, read) ->
                    positionLabelOf(randomResumePoint().withRecord { copy(format = Reading, progressPercent = percent) }, Reading) shouldBe
                        PercentRead(read)
                }
            }
        }

        context("with nothing to measure") {
            it("#22 TC-7: is in progress, for no duration, only a CFI or a medium mismatch") {
                checkAll(
                    Exhaustive.of(
                        listeningPoint(total = null) to Listening,
                        randomResumePoint().withRecord { copy(format = Reading, progressPercent = null) } to Reading,
                        listeningPoint() to Reading,
                    ),
                ) { (point, presented) ->
                    positionLabelOf(point, presented) shouldBe InProgress
                }
            }
        }
    }

    describe("formatting a duration") {
        it("#22 TC-8: reads in its largest units, for hours, minutes, seconds, zero and negative") {
            checkAll(
                Exhaustive.of(
                    11100L to "3h 5m",
                    7200L to "2h",
                    3599L to "59m",
                    60L to "1m",
                    59L to "59s",
                    0L to "0m",
                    -5L to "0m",
                ),
            ) { (seconds, text) ->
                formatDuration(seconds) shouldBe text
            }
        }
    }

    describe("a card's relative age") {
        it("#22 TC-9: floors into the largest bucket, at each bucket's edges") {
            val day = 86_400L
            val now = randomNow()

            checkAll(
                Exhaustive.of(
                    1L to RelativeAge.Seconds(1),
                    59L to RelativeAge.Seconds(59),
                    60L to RelativeAge.Minutes(1),
                    3599L to RelativeAge.Minutes(59),
                    3600L to RelativeAge.Hours(1),
                    day - 1 to RelativeAge.Hours(23),
                    day to RelativeAge.Days(1),
                    6 * day to RelativeAge.Days(6),
                    7 * day to RelativeAge.Weeks(1),
                    29 * day to RelativeAge.Weeks(4),
                    30 * day to RelativeAge.Months(1),
                    364 * day to RelativeAge.Months(12),
                    365 * day to RelativeAge.Years(1),
                ),
            ) { (ageSeconds, age) ->
                relativeAgeOf(now / 1000 - ageSeconds, now) shouldBe age
            }
        }

        it("#22 TC-9: is just now, for a future or sub-second timestamp") {
            val now = randomNow()

            checkAll(Exhaustive.of(now / 1000 + 60, now / 1000)) { updatedAt ->
                relativeAgeOf(updatedAt, now) shouldBe RelativeAge.JustNow
            }
        }
    }

    describe("building the cards") {
        context("with one book in both media") {
            it("#22 TC-10: gives two cards with their media") {
                val both = book("epub", "m4b")

                val cards = continueCardsOf(
                    listOf(
                        randomResumePoint().about(both).withRecord { copy(format = Reading) },
                        randomResumePoint().about(both).withRecord { copy(format = Listening) },
                    ),
                    randomNow(),
                )

                cards.map { it.id } shouldBe listOf("${both.uuid}:epub", "${both.uuid}:audio")
                cards.map { it.medium } shouldBe listOf(Reading, Listening)
            }
        }

        context("with repeated points for one card") {
            it("#22 TC-10: keeps the first") {
                val same = book("epub")
                fun pointAt(percent: Double) = randomResumePoint().about(same).withRecord { copy(format = Reading, progressPercent = percent) }
                val first = pointAt(10.0)

                val cards = continueCardsOf(listOf(first, pointAt(20.0)), randomNow())

                cards.single().fraction shouldBe first.fraction(Reading)
            }
        }

        context("with more points than the carousel shows") {
            it("#22 TC-11: keeps the first five in server order") {
                val points = List(7) { randomResumePoint().about(book("epub")) }

                continueCardsOf(points, randomNow()).map { it.id } shouldBe points.take(5).map { it.cardId }
            }
        }

        context("with a saved file") {
            it("#22 TC-12: carries it on a card of the record's own medium") {
                val point = randomResumePoint().about(book("m4b")).withRecord { copy(format = Listening, bookFileId = 20L) }

                continueCardsOf(listOf(point), randomNow()).single().resumeFileId shouldBe 20L
            }

            it("#22 TC-12: carries no file on a card of the other medium") {
                val point = randomResumePoint().about(book("m4b")).withRecord { copy(format = Reading, bookFileId = 7L) }

                continueCardsOf(listOf(point), randomNow()).single().resumeFileId shouldBe null
            }
        }
    }

    describe("the selection") {
        context("after a refresh") {
            it("#22 TC-13: keeps a listed card, else takes the first or nothing") {
                checkAll(
                    Exhaustive.of(
                        Triple("b", listOf("a", "b"), "b"),
                        Triple("z", listOf("a", "b"), "a"),
                        Triple(null, listOf("a"), "a"),
                        Triple("a", emptyList<String>(), null),
                    ),
                ) { (current, ids, selected) ->
                    selectionAfterRefresh(current, ids) shouldBe selected
                }
            }
        }
    }

    describe("the card a pager settled on") {
        it("#22 TC-14: is the one at the index, or nothing out of range") {
            checkAll(
                Exhaustive.of(0 to "a", 1 to "b", 2 to null, -1 to null),
            ) { (index, id) ->
                settledCardId(index, listOf("a", "b"), 1) shouldBe id
            }
        }

        it("#217 TC-6: is the first card on the settled page, or nothing past the last page") {
            val ids = listOf("a", "b", "c", "d", "e")
            checkAll(
                Exhaustive.of(
                    Triple(0, 2, "a"),
                    Triple(1, 2, "c"),
                    Triple(2, 2, "e"),
                    Triple(3, 2, null),
                    Triple(-1, 2, null),
                ),
            ) { (page, cardsPerPage, id) ->
                settledCardId(page, ids, cardsPerPage) shouldBe id
            }
        }

        it("#217 TC-6: is nothing when there are no cards") {
            settledCardId(0, emptyList(), 2) shouldBe null
        }
    }
})
