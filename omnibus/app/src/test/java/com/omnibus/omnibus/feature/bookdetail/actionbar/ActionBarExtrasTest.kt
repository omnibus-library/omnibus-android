package com.omnibus.omnibus.feature.bookdetail.actionbar

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

private val ebookFormats = listOf("epub")
private val audioFormats = listOf("m4b")
private val bothFormats = listOf("epub", "m4b")

private fun book(formats: List<String>, files: List<BookFile> = emptyList()): Book =
    testBook(1, formats = formats, bookFiles = files)

private fun file(
    id: Long,
    format: String = "m4b",
    ordinal: Int = id.toInt(),
    label: String? = null,
    filename: String = "file-$id.$format",
    sizeBytes: Long = 0L,
): BookFile = BookFile(id, format, filename, ordinal, label, sizeBytes, etag = null)

/** A primary button model with [label], for the round buttons to read. */
private fun primary(label: ActionLabel, uuid: String) =
    ActionBarModel.Primary(label, 0.4f, BookDetailDestination.Reader(uuid))

/** The reading labels and the listening labels, which the first round button branches on. */
private val readingLabels = listOf(
    ActionLabel.StartReading,
    ActionLabel.ContinueReading(3),
    ActionLabel.ReadAgain,
)
private val listeningLabels = listOf(
    ActionLabel.StartListening,
    ActionLabel.ContinueListening,
    ActionLabel.ListenAgain,
)

class ActionBarExtrasTest : DescribeSpec({

    describe("which round buttons show") {

        it("#99 TC-1: an ebook and an audiobook with a reading label get Listen then Immersive read") {
            val b = book(bothFormats, listOf(file(7L)))
            roundActions(b, primary(ActionLabel.ContinueReading(3), b.uuid), savedAudioFileId = 7L, listenAvailable = true) shouldBe
                listOf(
                    RoundAction.Listen(true, ListenTarget.Open(BookDetailDestination.Player(b.uuid, 7L))),
                    RoundAction.ImmersiveRead(BookDetailDestination.Reader(b.uuid), 7L, loadsAudio = true),
                )
        }

        it("#99 TC-2: a book with only one format gets no round buttons") {
            val ebookOnly = book(ebookFormats)
            val audioOnly = book(audioFormats, listOf(file(7L)))
            for (label in readingLabels + listeningLabels) {
                withClue("$label") {
                    roundActions(ebookOnly, primary(label, ebookOnly.uuid), null, listenAvailable = true).shouldBeEmpty()
                    roundActions(audioOnly, primary(label, audioOnly.uuid), 7L, listenAvailable = true).shouldBeEmpty()
                }
            }
        }

        it("#99 TC-7: a listening label puts Read first, then Immersive read") {
            val b = book(bothFormats, listOf(file(7L)))
            for (label in listeningLabels) {
                withClue("$label") {
                    roundActions(b, primary(label, b.uuid), 7L, listenAvailable = true) shouldBe
                        listOf(
                            RoundAction.Read(BookDetailDestination.Reader(b.uuid)),
                            RoundAction.ImmersiveRead(BookDetailDestination.Reader(b.uuid), 7L, loadsAudio = true),
                        )
                }
            }
        }

        it("#99 TC-8: a reading label puts Listen first") {
            val b = book(bothFormats, listOf(file(7L)))
            for (label in readingLabels) {
                withClue("$label") {
                    roundActions(b, primary(label, b.uuid), 7L, listenAvailable = true)
                        .first().shouldBeInstanceOf<RoundAction.Listen>()
                }
            }
        }
    }

    describe("what Listen opens") {

        it("#99 TC-24: no selectable audio files opens the player on the saved file") {
            val b = book(bothFormats)
            listenTarget(b, savedAudioFileId = 7L) shouldBe
                ListenTarget.Open(BookDetailDestination.Player(b.uuid, 7L))
        }

        it("#99 TC-25: one selectable file opens the player, with the saved file or with none") {
            val b = book(bothFormats, listOf(file(7L)))
            listenTarget(b, savedAudioFileId = 7L) shouldBe
                ListenTarget.Open(BookDetailDestination.Player(b.uuid, 7L))
            listenTarget(b, savedAudioFileId = null) shouldBe
                ListenTarget.Open(BookDetailDestination.Player(b.uuid, null))
        }

        it("#99 TC-26: one m4b and one flac file opens the player, not the chooser") {
            val b = book(bothFormats, listOf(file(7L, "m4b"), file(8L, "flac")))
            listenTarget(b, savedAudioFileId = 7L).shouldBeInstanceOf<ListenTarget.Open>()
        }

        it("#99 TC-28: only m4b, m4a and mp3 are selectable, whatever the case") {
            val b = book(
                listOf("epub", "M4B", "Mp3", "m4a", "flac", "epub"),
                listOf(
                    file(1L, "M4B"),
                    file(2L, "Mp3"),
                    file(3L, "m4a"),
                    file(4L, "flac"),
                    file(5L, "epub"),
                ),
            )
            selectableAudioFiles(b).map { it.id } shouldContainExactly listOf(1L, 2L, 3L)
        }

        it("#99 TC-29: selectable files come in ordinal order") {
            val b = book(
                bothFormats,
                listOf(file(30L, ordinal = 3), file(10L, ordinal = 1), file(20L, ordinal = 2)),
            )
            selectableAudioFiles(b).map { it.id } shouldContainExactly listOf(10L, 20L, 30L)
        }

        it("#99 TC-30: two selectable files open the chooser in ordinal order with matching ids") {
            val b = book(
                bothFormats,
                listOf(
                    file(30L, ordinal = 2, filename = "part-two.m4b"),
                    file(10L, ordinal = 1, filename = "part-one.m4b"),
                ),
            )
            listenTarget(b, savedAudioFileId = null) shouldBe ListenTarget.Choose(
                listOf(
                    AudioFileChoice(10L, "part-one.m4b", "m4b", 0L, continues = false),
                    AudioFileChoice(30L, "part-two.m4b", "m4b", 0L, continues = false),
                ),
            )
        }
    }

    describe("the chooser's rows") {

        it("#99 TC-31: a file's name is its label when the label is not blank") {
            val cases = listOf(
                null to "part.m4b",
                "" to "part.m4b",
                "  " to "part.m4b",
                "Narrated by X" to "Narrated by X",
            )
            for ((label, expected) in cases) {
                withClue("label=$label") {
                    audioFileName(file(1L, label = label, filename = "part.m4b")) shouldBe expected
                }
            }
        }

        it("#99 TC-32: the detail is the upper-cased format and the size, or the format alone when unknown") {
            audioFileDetail("m4b", 412_000_000L) shouldBe "M4B · 412 MB"
            audioFileDetail("Mp3", 0L) shouldBe "MP3"
        }

        it("#99 TC-35: only the file holding the saved position continues") {
            val b = book(bothFormats, listOf(file(1L), file(2L), file(3L)))
            val choices = (listenTarget(b, savedAudioFileId = 2L) as ListenTarget.Choose).choices
            choices.map { it.continues } shouldContainExactly listOf(false, true, false)
        }

        it("#99 TC-36: with no saved position, or one that matches no file, nothing continues") {
            val b = book(bothFormats, listOf(file(1L), file(2L), file(3L)))
            for (saved in listOf(null, 99L)) {
                withClue("saved=$saved") {
                    val choices = (listenTarget(b, savedAudioFileId = saved) as ListenTarget.Choose).choices
                    choices.map { it.continues } shouldContainExactly listOf(false, false, false)
                }
            }
        }
    }

    describe("what Immersive read does") {

        it("#99 TC-44: Immersive read loads the saved file and opens the reader") {
            val b = book(bothFormats, listOf(file(7L), file(8L)))
            val immersive = roundActions(b, primary(ActionLabel.ContinueReading(3), b.uuid), 7L, listenAvailable = true).last()
            immersive shouldBe RoundAction.ImmersiveRead(BookDetailDestination.Reader(b.uuid), 7L, loadsAudio = true)
        }

        it("#99 TC-46: when listening is unavailable Immersive read opens the reader without loading audio") {
            val b = book(bothFormats, listOf(file(7L)))
            roundActions(b, primary(ActionLabel.ContinueReading(3), b.uuid), 7L, listenAvailable = false) shouldBe
                listOf(
                    RoundAction.Listen(false, ListenTarget.Open(BookDetailDestination.Player(b.uuid, 7L))),
                    RoundAction.ImmersiveRead(BookDetailDestination.Reader(b.uuid), 7L, loadsAudio = false),
                )
        }
    }
})
