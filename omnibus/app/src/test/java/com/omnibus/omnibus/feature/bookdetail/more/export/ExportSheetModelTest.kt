package com.omnibus.omnibus.feature.bookdetail.more.export

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomBookFile
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

private const val MB = 1_000_000L

private fun file(format: String, ordinal: Int = 1, size: Long = MB): BookFile =
    randomBookFile().copy(format = format, ordinal = ordinal, sizeBytes = size)

private fun book(formats: List<String>, files: List<BookFile> = emptyList()): Book =
    randomBook().copy(formats = formats, bookFiles = files)

private fun epubBook(size: Long = MB): Book = book(listOf("epub"), listOf(file("epub", size = size)))

/** A signed-in user with a Kindle address, who may or may not download. */
private fun user(canDownload: Boolean): CurrentUser =
    randomCurrentUser().copy(canDownload = canDownload, kindleEmail = randomString())

/** A book with an EPUB and an m4b, which a downloader can save three ways. */
private fun everySaveRow(online: Boolean, saving: ExportSave?): List<SaveRow> {
    val both = book(listOf("epub", "m4b"), listOf(file("epub"), file("m4b")))
    val model = exportSheet(both, user(canDownload = true), online, KindlePhase.Idle, saving)
    return listOfNotNull(model.kobo, model.ebook, model.audio)
}

/** One row of the sections table: the book, the user and which rows the sheet must show. */
private data class SectionCase(
    val book: Book,
    val user: CurrentUser,
    val kindle: Boolean,
    val kobo: Boolean,
    val ebookFormat: String?,
    val audio: Boolean,
)

class ExportSheetModelTest : DescribeSpec({

    describe("the Export row in the More menu") {
        it("#118 TC-1: shows for a book with formats that can be downloaded or sent to a Kindle") {
            checkAll(
                Exhaustive.of(
                    Triple(book(emptyList()), user(canDownload = true), false),
                    Triple(book(listOf("m4b")), user(canDownload = true), true),
                    Triple(book(listOf("EPUB")), user(canDownload = false), true),
                    Triple(book(listOf("Pdf")), user(canDownload = false), true),
                    Triple(book(listOf("mobi"), listOf(file("epub"))), user(canDownload = false), true),
                    Triple(book(listOf("m4b", "mobi")), user(canDownload = false), false),
                    Triple(book(listOf("epub")), null, true),
                    Triple(book(listOf("m4b")), null, false),
                ),
            ) { (book, user, expected) ->
                showsExportRow(book, user) shouldBe expected
            }
        }
    }

    describe("the file a Kindle send mails") {
        it("#118 TC-2: is the lowest-ordinal EPUB, else the lowest-ordinal PDF") {
            val firstEpub = file("epub", ordinal = 1)
            val firstPdf = file("pdf", ordinal = 1)
            val laterEpub = file("epub", ordinal = 2)

            kindleFile(book(listOf("epub"), listOf(laterEpub, firstEpub, file("pdf", ordinal = 0)))) shouldBe firstEpub
            kindleFile(book(listOf("pdf"), listOf(file("pdf", ordinal = 3), firstPdf))) shouldBe firstPdf
            kindleFile(book(listOf("m4b"), listOf(file("m4b")))) shouldBe null
        }
    }

    describe("the Kindle gate") {
        it("#118 TC-3: is oversize only above 50 MB of the file that would be mailed") {
            checkAll(
                Exhaustive.of(
                    epubBook(size = 50_000_001) to KindleGate.Oversize,
                    epubBook(size = 50_000_000) to KindleGate.Ready,
                    book(listOf("epub", "pdf"), listOf(file("epub"), file("pdf", size = 60 * MB))) to KindleGate.Ready,
                    book(listOf("pdf"), listOf(file("pdf", size = 60 * MB))) to KindleGate.Oversize,
                ),
            ) { (book, expected) ->
                kindleGate(book, randomString(), isOnline = true) shouldBe expected
            }
        }

        it("#118 TC-4: needs an address, then a connection") {
            checkAll(
                Exhaustive.of(
                    Triple(null, true, KindleGate.NoAddress),
                    Triple("", true, KindleGate.NoAddress),
                    Triple("  ", true, KindleGate.NoAddress),
                    Triple(randomString(), false, KindleGate.Offline),
                    Triple(randomString(), true, KindleGate.Ready),
                ),
            ) { (email, online, expected) ->
                kindleGate(epubBook(), email, online) shouldBe expected
            }
        }

        it("#118 TC-5: ranks too large, then no address, then offline") {
            kindleGate(epubBook(size = 60 * MB), kindleEmail = null, isOnline = false) shouldBe KindleGate.Oversize
            kindleGate(epubBook(), kindleEmail = null, isOnline = false) shouldBe KindleGate.NoAddress
        }
    }

    describe("the Kindle row") {
        context("when the gate is ready") {
            it("#118 TC-6: shows the send's phase, undimmed") {
                val failed = KindlePhase.Failed(KindleFailure.Undelivered, "mailbox full")

                checkAll(
                    Exhaustive.of(
                        KindlePhase.Idle to KindleRow(KindleRowState.Ready, enabled = true, dimmed = false, sendsEpub = true),
                        KindlePhase.Sending to KindleRow(KindleRowState.Sending, enabled = false, dimmed = false, sendsEpub = true),
                        KindlePhase.Sent to KindleRow(KindleRowState.Sent, enabled = false, dimmed = false, sendsEpub = true),
                        failed to KindleRow(KindleRowState.Failed, enabled = true, dimmed = false, sendsEpub = true, failure = failed),
                    ),
                ) { (phase, expected) ->
                    exportSheet(epubBook(), user(canDownload = false), true, phase, null).kindle shouldBe expected
                }
            }

            context("when the book has only a PDF") {
                it("#118 TC-6: says it does not send an EPUB") {
                    val pdf = book(listOf("pdf"), listOf(file("pdf")))

                    exportSheet(pdf, user(canDownload = false), true, KindlePhase.Idle, null).kindle?.sendsEpub shouldBe false
                }
            }
        }

        context("when the gate blocks the send") {
            it("#118 TC-7, TC-8: shows the gate over any phase, dimming only offline") {
                val noAddress = user(canDownload = false).copy(kindleEmail = null)

                checkAll(
                    Exhaustive.of(KindlePhase.Idle, KindlePhase.Sending, KindlePhase.Failed(KindleFailure.Unknown)),
                ) { phase ->
                    exportSheet(epubBook(size = 60 * MB), user(canDownload = false), true, phase, null).kindle shouldBe
                        KindleRow(KindleRowState.Oversize, enabled = true, dimmed = false, sendsEpub = true)
                    exportSheet(epubBook(), noAddress, true, phase, null).kindle shouldBe
                        KindleRow(KindleRowState.NoAddress, enabled = true, dimmed = false, sendsEpub = true)
                    exportSheet(epubBook(), user(canDownload = false), false, phase, null).kindle shouldBe
                        KindleRow(KindleRowState.Offline, enabled = false, dimmed = true, sendsEpub = true)
                }
            }
        }
    }

    describe("the sheet's rows") {
        it("#118 TC-9: follow the book's formats with the user's download right") {
            val pdf = book(listOf("pdf"), listOf(file("pdf")))
            val audio = book(listOf("m4b"), listOf(file("m4b")))

            checkAll(
                Exhaustive.of(
                    SectionCase(epubBook(), user(true), kindle = true, kobo = true, ebookFormat = "EPUB", audio = false),
                    SectionCase(epubBook(), user(false), kindle = true, kobo = false, ebookFormat = null, audio = false),
                    SectionCase(pdf, user(true), kindle = true, kobo = false, ebookFormat = "PDF", audio = false),
                    SectionCase(audio, user(true), kindle = false, kobo = false, ebookFormat = null, audio = true),
                    SectionCase(audio, user(false), kindle = false, kobo = false, ebookFormat = null, audio = false),
                    SectionCase(book(listOf("mobi")), user(true), kindle = false, kobo = false, ebookFormat = null, audio = false),
                ),
            ) { case ->
                val model = exportSheet(case.book, case.user, true, KindlePhase.Idle, null)

                (model.kindle != null) shouldBe case.kindle
                (model.kobo != null) shouldBe case.kobo
                model.ebook?.format shouldBe case.ebookFormat
                (model.audio != null) shouldBe case.audio
                model.showsSendSection shouldBe (case.kindle || case.kobo)
                model.showsSaveSection shouldBe (case.ebookFormat != null || case.audio)
            }
        }

        it("#118 TC-10: download the EPUB, else the PDF, as the ebook") {
            val epub = file("epub", size = 412_000)

            ebookTarget(book(listOf("epub", "pdf"), listOf(epub, file("pdf")))) shouldBe EbookTarget("EPUB", 412_000, "epub")
            ebookTarget(book(listOf("pdf"), listOf(file("pdf", size = 3 * MB)))) shouldBe EbookTarget("PDF", 3 * MB, "pdf")
        }

        it("#118 TC-10: download the lowest-ordinal m4b, m4a or mp3 as the audiobook") {
            val audio = book(
                listOf("flac", "m4b", "mp3"),
                listOf(file("flac", ordinal = 0), file("m4b", ordinal = 1, size = 138 * MB), file("mp3", ordinal = 2)),
            )
            val flacOnly = book(listOf("flac"), listOf(file("flac")))

            audioTarget(audio) shouldBe AudioTarget("M4B", 138 * MB, "m4b")
            audioTarget(flacOnly) shouldBe AudioTarget(null, 0, "m4b")
        }

        context("when a save runs") {
            it("#118 TC-11: marks its row busy, dimming the others, for each kind") {
                checkAll(Exhaustive.enum<ExportSave>()) { kind ->
                    val rows = everySaveRow(online = true, saving = kind)

                    rows.single { it.busy }.kind shouldBe kind
                    rows.filter { it.dimmed }.map { it.kind } shouldContainExactlyInAnyOrder ExportSave.entries - kind
                    rows.none { it.enabled } shouldBe true
                }
            }
        }

        context("when the device is offline") {
            it("#118 TC-12: marks every save row offline, disabled") {
                everySaveRow(online = false, saving = null).forEach { row ->
                    row.offline shouldBe true
                    row.enabled shouldBe false
                    row.dimmed shouldBe true
                }
            }
        }
    }
})
