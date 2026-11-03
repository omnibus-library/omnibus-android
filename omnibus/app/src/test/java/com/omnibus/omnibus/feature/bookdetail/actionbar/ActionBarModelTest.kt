package com.omnibus.omnibus.feature.bookdetail.actionbar

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRecord
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.progress.BookReadingProgress
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val ebookFormats = listOf("epub")
private val audioFormats = listOf("m4b")
private val bothFormats = listOf("epub", "m4b")

private fun book(formats: List<String>, physical: Boolean = false): Book =
    testBook(1, formats = formats).copy(hasPhysical = physical)

private fun known(status: ReadStatus): ReadStatusState = ReadStatusState.Known(ReadStatusRecord(status, 1L, null))

/** The statuses every table walks: U, F, KU, KR, KF. */
private val statuses: List<Pair<String, ReadStatusState>> = listOf(
    "U" to ReadStatusState.Unknown,
    "F" to ReadStatusState.Failed,
    "KU" to known(ReadStatus.Unread),
    "KR" to known(ReadStatus.Reading),
    "KF" to known(ReadStatus.Finished),
)

private val notFinished = statuses.filter { it.first != "KF" }

/** A reading-latest position at [chapter], 40 %. */
private fun reading(chapter: Int?) = BookReadingProgress(
    fraction = 0.4f,
    chapterNumber = chapter,
    latestMedium = ProgressFormat.Reading,
    audioFileId = null,
    audioElapsedSeconds = null,
    audioTotalSeconds = null,
)

/** A listening-latest position of [elapsed] seconds in file [fileId], 40 %. */
private fun listening(elapsed: Long?, fileId: Long? = 7L) = BookReadingProgress(
    fraction = 0.4f,
    chapterNumber = null,
    latestMedium = ProgressFormat.Listening,
    audioFileId = fileId,
    audioElapsedSeconds = elapsed,
    audioTotalSeconds = 300L,
)

/** The positions the matrix walks: none, then reading with and without a chapter, then listening at 0, 1 and 2 s. */
private val positions: List<Pair<String, BookReadingProgress?>> = listOf(
    "none" to null,
    "reading ch3" to reading(3),
    "reading no chapter" to reading(null),
    "listening 0s" to listening(0L),
    "listening 1s" to listening(1L),
    "listening 2s" to listening(2L),
)

/** A primary button model's expected value, compared whole. */
private fun primary(label: ActionLabel, fill: Float, destination: BookDetailDestination) =
    ActionBarModel.Primary(label, fill, destination)

class ActionBarModelTest : DescribeSpec({

    describe("the label") {

        it("TC-1: no position and not reading or finished starts") {
            for ((status, state) in statuses.filter { it.first != "KR" && it.first != "KF" }) {
                for ((format, formats) in listOf("E" to ebookFormats, "A" to audioFormats, "EA" to bothFormats)) {
                    withClue("$status/$format") {
                        val b = book(formats)
                        val expected = if (format == "A") {
                            primary(ActionLabel.StartListening, 0f, BookDetailDestination.Player(b.uuid, null))
                        } else {
                            primary(ActionLabel.StartReading, 0f, BookDetailDestination.Reader(b.uuid))
                        }
                        actionBarModel(b, state, null) shouldBe expected
                    }
                }
            }
        }

        it("TC-2: reading status without a position continues") {
            val state = known(ReadStatus.Reading)
            for ((format, formats) in listOf("E" to ebookFormats, "A" to audioFormats, "EA" to bothFormats)) {
                withClue(format) {
                    val b = book(formats)
                    val expected = if (format == "A") {
                        primary(ActionLabel.ContinueListening, 0f, BookDetailDestination.Player(b.uuid, null))
                    } else {
                        primary(ActionLabel.ContinueReading(null), 0f, BookDetailDestination.Reader(b.uuid))
                    }
                    actionBarModel(b, state, null) shouldBe expected
                }
            }
        }

        it("TC-3: finished without a position reads again") {
            val state = known(ReadStatus.Finished)
            for ((format, formats) in listOf("E" to ebookFormats, "A" to audioFormats, "EA" to bothFormats)) {
                withClue(format) {
                    val b = book(formats)
                    val expected = if (format == "A") {
                        primary(ActionLabel.ListenAgain, 1f, BookDetailDestination.Player(b.uuid, null))
                    } else {
                        primary(ActionLabel.ReadAgain, 1f, BookDetailDestination.Reader(b.uuid))
                    }
                    actionBarModel(b, state, null) shouldBe expected
                }
            }
        }

        it("TC-4: a reading position with a chapter continues at that chapter") {
            for ((format, formats) in listOf("E" to ebookFormats, "EA" to bothFormats)) {
                for ((status, state) in statuses) {
                    withClue("$format/$status") {
                        val b = book(formats)
                        val expected = if (status == "KF") {
                            primary(ActionLabel.ReadAgain, 1f, BookDetailDestination.Reader(b.uuid))
                        } else {
                            primary(ActionLabel.ContinueReading(3), 0.4f, BookDetailDestination.Reader(b.uuid))
                        }
                        actionBarModel(b, state, reading(3)) shouldBe expected
                    }
                }
            }
        }

        it("TC-5: a reading position without a chapter continues without one") {
            for ((status, state) in notFinished) {
                withClue(status) {
                    val b = book(ebookFormats)
                    actionBarModel(b, state, reading(null)) shouldBe
                        primary(ActionLabel.ContinueReading(null), 0.4f, BookDetailDestination.Reader(b.uuid))
                }
            }
        }

        it("TC-6: a listening position continues listening") {
            for ((format, formats) in listOf("A" to audioFormats, "EA" to bothFormats)) {
                for ((status, state) in notFinished) {
                    withClue("$format/$status") {
                        val b = book(formats)
                        actionBarModel(b, state, listening(120L, fileId = 7L)) shouldBe
                            primary(ActionLabel.ContinueListening, 0.4f, BookDetailDestination.Player(b.uuid, 7L))
                    }
                }
            }
        }

        it("TC-7: finished beats any position") {
            val finished = known(ReadStatus.Finished)
            for ((format, formats) in listOf("E" to ebookFormats, "A" to audioFormats, "EA" to bothFormats)) {
                for (position in listOf(reading(3), listening(120L, fileId = 7L))) {
                    withClue("$format/$position") {
                        val b = book(formats)
                        val toPlayer = b.hasAudiobook && (!b.hasEbook || position.latestMedium == ProgressFormat.Listening)
                        val expected = if (toPlayer) {
                            primary(ActionLabel.ListenAgain, 1f, BookDetailDestination.Player(b.uuid, position.audioFileId))
                        } else {
                            primary(ActionLabel.ReadAgain, 1f, BookDetailDestination.Reader(b.uuid))
                        }
                        actionBarModel(b, finished, position) shouldBe expected
                    }
                }
            }
        }
    }

    describe("the destination") {

        it("TC-8: ebook-only books always open the reader") {
            for (physical in listOf(false, true)) {
                val b = book(ebookFormats, physical)
                for ((position, progress) in listOf("none" to null, "reading" to reading(3), "listening" to listening(120L))) {
                    withClue("physical=$physical/$position") {
                        val model = actionBarModel(b, ReadStatusState.Unknown, progress)
                        model.shouldBeInstanceOf<ActionBarModel.Primary>().destination shouldBe
                            BookDetailDestination.Reader(b.uuid)
                    }
                }
            }
        }

        it("TC-9: audio-only books always open the player") {
            val cases: List<Triple<String, BookReadingProgress?, Long?>> = listOf(
                Triple("none", null, null),
                Triple("reading", reading(3), null),
                Triple("listening file 7", listening(120L, fileId = 7L), 7L),
                Triple("listening no file", listening(120L, fileId = null), null),
                Triple("listening elapsed 0", listening(0L, fileId = 7L), 7L),
            )
            for (physical in listOf(false, true)) {
                for ((name, progress, fileId) in cases) {
                    withClue("physical=$physical/$name") {
                        val b = book(audioFormats, physical)
                        actionBarModel(b, ReadStatusState.Unknown, progress)
                            .shouldBeInstanceOf<ActionBarModel.Primary>().destination shouldBe
                            BookDetailDestination.Player(b.uuid, fileId)
                    }
                }
            }
        }

        it("TC-10: ebook and audio opens the reader unless the latest place is listening") {
            val b = book(bothFormats)
            actionBarModel(b, ReadStatusState.Unknown, null) shouldBe
                primary(ActionLabel.StartReading, 0f, BookDetailDestination.Reader(b.uuid))
            actionBarModel(b, ReadStatusState.Unknown, reading(3)) shouldBe
                primary(ActionLabel.ContinueReading(3), 0.4f, BookDetailDestination.Reader(b.uuid))
            // An older listening record does not win over a newer reading one.
            val olderListening = reading(3).copy(audioFileId = 7L, audioElapsedSeconds = 120L, audioTotalSeconds = 300L)
            actionBarModel(b, ReadStatusState.Unknown, olderListening) shouldBe
                primary(ActionLabel.ContinueReading(3), 0.4f, BookDetailDestination.Reader(b.uuid))
        }

        it("TC-11: a listening position of one second or less does not open the player") {
            val b = book(bothFormats)
            for (elapsed in listOf(0L, 1L, null)) {
                withClue("elapsed=$elapsed") {
                    actionBarModel(b, ReadStatusState.Unknown, listening(elapsed)) shouldBe
                        primary(ActionLabel.ContinueReading(null), 0.4f, BookDetailDestination.Reader(b.uuid))
                }
            }
        }

        it("TC-12: a listening position over one second opens the player on its file") {
            val b = book(bothFormats)
            for ((elapsed, fileId) in listOf(2L to 7L, 3600L to 7L, 2L to null, 3600L to null)) {
                withClue("elapsed=$elapsed/file=$fileId") {
                    actionBarModel(b, ReadStatusState.Unknown, listening(elapsed, fileId)) shouldBe
                        primary(ActionLabel.ContinueListening, 0.4f, BookDetailDestination.Player(b.uuid, fileId))
                }
            }
        }

        it("TC-13: listening position on an ebook-only book stays in the reader") {
            val b = book(ebookFormats)
            actionBarModel(b, ReadStatusState.Unknown, listening(120L)) shouldBe
                primary(ActionLabel.ContinueReading(null), 0.4f, BookDetailDestination.Reader(b.uuid))
        }
    }

    describe("the fill") {

        it("TC-14: fill without a position") {
            for (formats in listOf(ebookFormats, audioFormats, bothFormats)) {
                for ((status, state) in statuses) {
                    withClue("$formats/$status") {
                        val model = actionBarModel(book(formats), state, null).shouldBeInstanceOf<ActionBarModel.Primary>()
                        model.fill shouldBe (if (status == "KF") 1f else 0f)
                    }
                }
            }
        }

        it("TC-15: fill follows the position fraction") {
            for (formats in listOf(ebookFormats, audioFormats, bothFormats)) {
                for (progress in listOf(reading(3), listening(120L))) {
                    for ((status, state) in statuses) {
                        withClue("$formats/${progress.latestMedium}/$status") {
                            val model = actionBarModel(book(formats), state, progress).shouldBeInstanceOf<ActionBarModel.Primary>()
                            model.fill shouldBe (if (status == "KF") 1f else 0.4f)
                        }
                    }
                }
            }
        }

        it("TC-16: fill is clamped, NaN and null become zero") {
            val inputs: List<Pair<Float?, Float>> = listOf(
                1.7f to 1f,
                -0.2f to 0f,
                Float.POSITIVE_INFINITY to 1f,
                Float.NEGATIVE_INFINITY to 0f,
                Float.NaN to 0f,
                null to 0f,
            )
            for (status in listOf("KR", "U")) {
                val state = statuses.first { it.first == status }.second
                for ((fraction, expected) in inputs) {
                    withClue("$status/$fraction") {
                        val progress = BookReadingProgress(
                            fraction = fraction,
                            chapterNumber = null,
                            latestMedium = ProgressFormat.Reading,
                            audioFileId = null,
                            audioElapsedSeconds = null,
                            audioTotalSeconds = null,
                        )
                        val model = actionBarModel(book(ebookFormats), state, progress).shouldBeInstanceOf<ActionBarModel.Primary>()
                        model.fill shouldBe expected
                        model.fill.isNaN() shouldBe false
                    }
                }
            }
        }

        it("TC-17: finished always fills completely") {
            for (fraction in listOf(Float.NaN, 0.2f, null)) {
                withClue("fraction=$fraction") {
                    val progress = BookReadingProgress(
                        fraction = fraction,
                        chapterNumber = null,
                        latestMedium = ProgressFormat.Reading,
                        audioFileId = null,
                        audioElapsedSeconds = null,
                        audioTotalSeconds = null,
                    )
                    actionBarModel(book(ebookFormats), known(ReadStatus.Finished), progress)
                        .shouldBeInstanceOf<ActionBarModel.Primary>().fill shouldBe 1f
                }
            }
        }
    }

    describe("the physical copy") {

        it("TC-18: books without a readable copy get NoCopy") {
            for (physical in listOf(true, false)) {
                val b = book(emptyList(), physical)
                for ((status, state) in statuses) {
                    for ((position, progress) in positions) {
                        withClue("physical=$physical/$status/$position") {
                            actionBarModel(b, state, progress) shouldBe ActionBarModel.NoCopy(physical = physical)
                        }
                    }
                }
            }
        }

        it("TC-19: a physical copy next to a readable one does not change the bar") {
            for ((formats, plain) in listOf(ebookFormats to book(ebookFormats), audioFormats to book(audioFormats))) {
                val withPhysical = book(formats, physical = true)
                for ((status, state) in statuses) {
                    for ((position, progress) in positions) {
                        withClue("$formats/$status/$position") {
                            val model = actionBarModel(withPhysical, state, progress)
                            model.shouldBeInstanceOf<ActionBarModel.Primary>()
                            model shouldBe actionBarModel(plain, state, progress)
                        }
                    }
                }
            }
        }

        it("TC-20: every status, format and position combination is consistent") {
            val formatsByName = listOf(
                "E" to ebookFormats,
                "A" to audioFormats,
                "EA" to bothFormats,
                "P" to emptyList(),
                "PE" to ebookFormats,
                "PA" to audioFormats,
                "none" to emptyList(),
            )
            for ((formatName, formats) in formatsByName) {
                val b = book(formats, physical = formatName.startsWith("P"))
                val readable = b.hasEbook || b.hasAudiobook
                for ((status, state) in statuses) {
                    for ((position, progress) in positions) {
                        withClue("$formatName/$status/$position") {
                            val model = actionBarModel(b, state, progress)
                            if (!readable) {
                                model shouldBe ActionBarModel.NoCopy(physical = b.hasPhysical)
                            } else {
                                val primary = model.shouldBeInstanceOf<ActionBarModel.Primary>()
                                val finished = status == "KF"
                                val expectedFill = if (finished) 1f else (progress?.fraction ?: 0f).coerceIn(0f, 1f)
                                primary.fill shouldBe expectedFill

                                val toPlayer = b.hasAudiobook && (
                                    !b.hasEbook ||
                                        (progress?.latestMedium == ProgressFormat.Listening && (progress.audioElapsedSeconds ?: 0L) > 1L)
                                    )
                                val expectedDestination: BookDetailDestination = if (toPlayer) {
                                    BookDetailDestination.Player(b.uuid, progress?.audioFileId)
                                } else {
                                    BookDetailDestination.Reader(b.uuid)
                                }
                                primary.destination shouldBe expectedDestination
                                primary.label.isListening shouldBe toPlayer

                                val isContinue = primary.label is ActionLabel.ContinueReading ||
                                    primary.label is ActionLabel.ContinueListening
                                isContinue shouldBe ((status == "KR" || progress != null) && !finished)

                                val isAgain = primary.label is ActionLabel.ReadAgain || primary.label is ActionLabel.ListenAgain
                                isAgain shouldBe finished
                            }
                        }
                    }
                }
            }
        }
    }

    describe("the label's kind and the copy") {

        it("TC-21: isListening is true only for listening labels") {
            ActionLabel.StartListening.isListening shouldBe true
            ActionLabel.ContinueListening.isListening shouldBe true
            ActionLabel.ListenAgain.isListening shouldBe true
            ActionLabel.StartReading.isListening shouldBe false
            ActionLabel.ContinueReading(null).isListening shouldBe false
            ActionLabel.ContinueReading(5).isListening shouldBe false
            ActionLabel.ReadAgain.isListening shouldBe false
        }

        it("TC-22: only ebook or audiobook is readable") {
            hasReadableCopy(book(ebookFormats)) shouldBe true
            hasReadableCopy(book(audioFormats)) shouldBe true
            hasReadableCopy(book(bothFormats)) shouldBe true
            hasReadableCopy(book(emptyList(), physical = true)) shouldBe false
            hasReadableCopy(book(ebookFormats, physical = true)) shouldBe true
            hasReadableCopy(book(emptyList())) shouldBe false
        }
    }
})
