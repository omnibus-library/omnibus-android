package com.omnibus.omnibus.feature.bookdetail.actionbar

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.data.readstatus.randomReadStatusRecord
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.progress.BookReadingProgress
import com.omnibus.omnibus.feature.bookdetail.progress.randomBookReadingProgress
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomOf
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

private fun ebook(): Book = randomBook().copy(formats = listOf("epub"))
private fun audiobook(): Book = randomBook().copy(formats = listOf("m4b"))
private fun both(): Book = randomBook().copy(formats = listOf("epub", "m4b"))

private fun known(status: ReadStatus): ReadStatusState =
    ReadStatusState.Known(randomReadStatusRecord().copy(status = status))

private fun randomStatus(): ReadStatusState =
    randomOf(listOf(ReadStatusState.Unknown, ReadStatusState.Failed, known(ReadStatus.Unread), known(ReadStatus.Reading), known(ReadStatus.Finished)))

private fun notFinishedStatuses() =
    Exhaustive.of(ReadStatusState.Unknown, ReadStatusState.Failed, known(ReadStatus.Unread), known(ReadStatus.Reading))

private fun readingPlace(): BookReadingProgress = randomBookReadingProgress().copy(latestMedium = ProgressFormat.Reading)

private fun listeningPlace(elapsed: Long? = 120L): BookReadingProgress =
    randomBookReadingProgress().copy(latestMedium = ProgressFormat.Listening, audioElapsedSeconds = elapsed)

class ActionBarModelTest : DescribeSpec({

    /** The main button for [book], which has a readable copy. */
    fun sut(
        book: Book,
        status: ReadStatusState = randomStatus(),
        progress: BookReadingProgress? = randomOf(listOf(null, readingPlace(), listeningPlace())),
    ) = actionBarModel(book, status, progress).shouldBeInstanceOf<ActionBarModel.Primary>()

    describe("the label") {
        context("when there is no place") {
            it("TC-1: starts in the book's medium, for each status neither reading nor finished") {
                checkAll(Exhaustive.of(ReadStatusState.Unknown, ReadStatusState.Failed, known(ReadStatus.Unread))) { status ->
                    sut(ebook(), status, null).label shouldBe ActionLabel.StartReading
                    sut(audiobook(), status, null).label shouldBe ActionLabel.StartListening
                }
            }

            context("when the status is Reading") {
                it("TC-2: continues in the book's medium") {
                    sut(ebook(), known(ReadStatus.Reading), null).label shouldBe ActionLabel.ContinueReading(null)
                    sut(audiobook(), known(ReadStatus.Reading), null).label shouldBe ActionLabel.ContinueListening
                }
            }
        }

        context("when the status is Finished") {
            it("TC-3, TC-7: reads again in the book's medium, whatever the place") {
                sut(ebook(), known(ReadStatus.Finished)).label shouldBe ActionLabel.ReadAgain
                sut(audiobook(), known(ReadStatus.Finished)).label shouldBe ActionLabel.ListenAgain
            }

            context("when the latest place is listening") {
                it("TC-7: listens again on a book with both formats") {
                    sut(both(), known(ReadStatus.Finished), listeningPlace()).label shouldBe ActionLabel.ListenAgain
                }
            }
        }

        context("when there is a reading place") {
            it("TC-4, TC-5: continues at its chapter, for each status not finished") {
                checkAll(notFinishedStatuses()) { status ->
                    val chapter = randomInt(1..100)

                    sut(ebook(), status, readingPlace().copy(chapterNumber = chapter)).label shouldBe
                        ActionLabel.ContinueReading(chapter)
                }
            }
        }

        context("when there is a listening place") {
            it("TC-6: continues listening, for each status not finished") {
                checkAll(notFinishedStatuses()) { status ->
                    sut(both(), status, listeningPlace()).label shouldBe ActionLabel.ContinueListening
                }
            }
        }
    }

    describe("where a tap leads") {
        context("when the book is an ebook only") {
            it("TC-8, TC-13: is the reader, for each kind of place") {
                checkAll(Exhaustive.of(null, readingPlace(), listeningPlace())) { progress ->
                    val book = ebook()

                    sut(book, progress = progress).destination shouldBe BookDetailDestination.Reader(book.uuid)
                }
            }
        }

        context("when the book is an audiobook only") {
            it("TC-9: is the player on the place's file, for each kind of place") {
                checkAll(Exhaustive.of(readingPlace(), listeningPlace(), listeningPlace(elapsed = 0L))) { progress ->
                    val book = audiobook()

                    sut(book, progress = progress).destination shouldBe
                        BookDetailDestination.Player(book.uuid, progress.audioFileId)
                }
            }
        }

        context("when the book has both formats") {
            // An older listening record does not win over a newer reading one.
            it("TC-10: is the reader for a newer reading place") {
                val book = both()
                val progress = readingPlace().copy(audioFileId = randomLong(), audioElapsedSeconds = 120L)

                sut(book, progress = progress).destination shouldBe BookDetailDestination.Reader(book.uuid)
            }

            it("TC-11: is the reader for each listening place up to a second") {
                checkAll(Exhaustive.of(0L, 1L, null)) { elapsed ->
                    val book = both()

                    sut(book, progress = listeningPlace(elapsed)).destination shouldBe BookDetailDestination.Reader(book.uuid)
                }
            }

            it("TC-12: is the player on the file of a listening place over a second") {
                val book = both()
                val progress = listeningPlace(elapsed = 2L)

                sut(book, progress = progress).destination shouldBe BookDetailDestination.Player(book.uuid, progress.audioFileId)
            }
        }
    }

    describe("the fill") {
        context("when the status is not Finished") {
            it("TC-15, TC-16: follows the place, clamped, for each kind of value") {
                checkAll(
                    Exhaustive.of(
                        .4f to .4f,
                        1.7f to 1f,
                        -0.2f to 0f,
                        Float.POSITIVE_INFINITY to 1f,
                        Float.NEGATIVE_INFINITY to 0f,
                        Float.NaN to 0f,
                        null to 0f,
                    ),
                ) { (fraction, fill) ->
                    sut(ebook(), known(ReadStatus.Reading), readingPlace().copy(fraction = fraction)).fill shouldBe fill
                }
            }
        }

        context("when the status is Finished") {
            it("TC-14, TC-17: is complete, for each kind of place") {
                checkAll(Exhaustive.of(readingPlace().copy(fraction = 0.2f), readingPlace().copy(fraction = Float.NaN), null)) { progress ->
                    sut(ebook(), known(ReadStatus.Finished), progress).fill shouldBe 1f
                }
            }
        }
    }

    describe("the main button") {
        context("when the book has no readable copy") {
            it("TC-18: has no main button, whatever the status, the place, the physical copy") {
                checkAll(Exhaustive.boolean(), Exhaustive.of(null, readingPlace(), listeningPlace())) { physical, progress ->
                    val book = randomBook().copy(formats = emptyList(), hasPhysical = physical)

                    actionBarModel(book, randomStatus(), progress) shouldBe ActionBarModel.NoCopy(physical)
                }
            }
        }
    }
})
