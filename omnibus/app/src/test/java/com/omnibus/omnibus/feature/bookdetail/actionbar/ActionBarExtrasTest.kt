package com.omnibus.omnibus.feature.bookdetail.actionbar

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomBookFile
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomOf
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private val readingLabels = listOf(ActionLabel.StartReading, ActionLabel.ContinueReading(3), ActionLabel.ReadAgain)
private val listeningLabels = listOf(ActionLabel.StartListening, ActionLabel.ContinueListening, ActionLabel.ListenAgain)

/** A book with an ebook and an audiobook, on the [files] it has. */
private fun bothBook(files: List<BookFile> = emptyList()): Book =
    randomBook().copy(formats = listOf("epub", "m4b"), bookFiles = files)

private fun audioFile(): BookFile = randomBookFile().copy(format = "m4b")

class ActionBarExtrasTest : DescribeSpec({

    /** The round buttons beside a main button labelled [label]. */
    fun sut(
        book: Book,
        label: ActionLabel,
        savedAudioFileId: Long? = randomLong(),
        listenAvailable: Boolean = randomBoolean(),
    ) = roundActions(
        book,
        ActionBarModel.Primary(label, 0.4f, BookDetailDestination.Reader(book.uuid)),
        savedAudioFileId,
        listenAvailable,
    )

    describe("which round buttons show") {
        it("#99 TC-2: none for a book with one format, across both formats, every label") {
            checkAll(Exhaustive.of("epub", "m4b")) { format ->
                readingLabels.plus(listeningLabels).forEach { label ->
                    sut(randomBook().copy(formats = listOf(format)), label).shouldBeEmpty()
                }
            }
        }

        context("when the main button is reading") {
            it("#99 TC-1, TC-8, TC-44: are Listen then Immersive read, on the saved file") {
                val book = bothBook(listOf(audioFile()))
                val saved = randomLong()

                sut(book, randomOf(readingLabels), saved, listenAvailable = true) shouldBe listOf(
                    RoundAction.Listen(true, ListenTarget.Open(BookDetailDestination.Player(book.uuid, saved))),
                    RoundAction.ImmersiveRead(BookDetailDestination.Reader(book.uuid), saved, loadsAudio = true),
                )
            }
        }

        context("when the main button is listening") {
            it("#99 TC-7: are Read then Immersive read") {
                val book = bothBook(listOf(audioFile()))
                val saved = randomLong()

                sut(book, randomOf(listeningLabels), saved, listenAvailable = true) shouldBe listOf(
                    RoundAction.Read(BookDetailDestination.Reader(book.uuid)),
                    RoundAction.ImmersiveRead(BookDetailDestination.Reader(book.uuid), saved, loadsAudio = true),
                )
            }
        }

        context("when listening is unavailable") {
            it("#99 TC-46: disable Listen, with Immersive read loading no audio") {
                val book = bothBook()
                val saved = randomLong()

                sut(book, randomOf(readingLabels), saved, listenAvailable = false) shouldBe listOf(
                    RoundAction.Listen(false, ListenTarget.Open(BookDetailDestination.Player(book.uuid, saved))),
                    RoundAction.ImmersiveRead(BookDetailDestination.Reader(book.uuid), saved, loadsAudio = false),
                )
            }
        }
    }

    describe("what Listen opens") {
        context("when fewer than two files can be listened to") {
            it("#99 TC-24, TC-25, TC-26: is the player on the saved file, for each of no file, one, one beside a flac") {
                checkAll(
                    Exhaustive.of(
                        emptyList(),
                        listOf(audioFile()),
                        listOf(audioFile(), randomBookFile().copy(format = "flac")),
                    ),
                ) { files ->
                    val book = bothBook(files)
                    val saved = randomLong()

                    listenTarget(book, saved) shouldBe ListenTarget.Open(BookDetailDestination.Player(book.uuid, saved))
                }
            }
        }

        context("when two files can be listened to") {
            it("#99 TC-29, TC-30: is the chooser in ordinal order") {
                val second = audioFile().copy(ordinal = 2, label = null)
                val first = audioFile().copy(ordinal = 1, label = null)

                listenTarget(bothBook(listOf(second, first)), savedAudioFileId = null) shouldBe ListenTarget.Choose(
                    listOf(first, second).map { AudioFileChoice(it.id, it.filename, it.format, it.sizeBytes, continues = false) },
                )
            }

            it("#99 TC-35: marks only the file holding the saved position") {
                val files = List(3) { audioFile() }
                val target = listenTarget(bothBook(files.mapIndexed { i, file -> file.copy(ordinal = i) }), files[1].id)

                target.shouldBeInstanceOf<ListenTarget.Choose>().choices.map { it.continues } shouldContainExactly
                    listOf(false, true, false)
            }
        }

        it("#99 TC-28: lists only m4b, m4a, mp3, whatever the case") {
            val selectable = listOf("M4B", "Mp3", "m4a").map { randomBookFile().copy(format = it) }
            val others = listOf("flac", "epub").map { randomBookFile().copy(format = it) }
            val files = (selectable + others).mapIndexed { i, file -> file.copy(ordinal = i) }

            selectableAudioFiles(bothBook(files)).map { it.id } shouldContainExactly selectable.map { it.id }
        }
    }

    describe("the chooser's rows") {
        it("#99 TC-31: are named by the label, falling back on the filename, for each label") {
            val name = randomString()

            checkAll(Exhaustive.of(null to null, "" to null, "  " to null, name to name)) { (label, shown) ->
                val file = randomBookFile().copy(label = label)

                audioFileName(file) shouldBe (shown ?: file.filename)
            }
        }

        it("#99 TC-32: detail the upper-cased format with any known size") {
            checkAll(Exhaustive.of(Triple("m4b", 412_000_000L, "M4B · 412 MB"), Triple("Mp3", 0L, "MP3"))) { (format, size, detail) ->
                audioFileDetail(format, size) shouldBe detail
            }
        }
    }
})
