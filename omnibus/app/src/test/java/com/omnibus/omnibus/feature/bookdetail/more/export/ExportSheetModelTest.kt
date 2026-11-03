package com.omnibus.omnibus.feature.bookdetail.more.export

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.testBook
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

private const val EMAIL = "reader@kindle.com"
private const val MB = 1_000_000L

private val reader = CurrentUser(
    id = 1,
    canEdit = false,
    canUpload = false,
    hiddenFormats = emptySet(),
    canDownload = false,
    kindleEmail = EMAIL,
)
private val downloader = reader.copy(canDownload = true)

private fun file(id: Long, format: String, ordinal: Int, size: Long = MB): BookFile =
    BookFile(id, format, "file-$id.$format", ordinal, null, size, null)

private fun book(formats: List<String>, files: List<BookFile> = emptyList()): Book =
    testBook(1, formats = formats, bookFiles = files)

private val smallEpub = book(listOf("epub"), listOf(file(1, "epub", 1)))

/** One row of a sheet table: the book, the user and what the sheet must show. */
private data class SectionCase(
    val label: String,
    val book: Book,
    val user: CurrentUser?,
    val kindle: Boolean,
    val kobo: Boolean,
    val ebookFormat: String?,
    val audio: Boolean,
    val showsSend: Boolean,
    val showsSave: Boolean,
)

class ExportSheetModelTest : DescribeSpec({

    describe("which rows exist") {

        it("#118 TC-1: shows the Export row when the book has formats and the user can download or it can go to a Kindle") {
            val cases: List<Triple<String, Pair<Book, CurrentUser?>, Boolean>> = listOf(
                Triple("no formats, may download", book(emptyList()) to downloader, false),
                Triple("m4b, may download", book(listOf("m4b")) to downloader, true),
                Triple("epub, no download", book(listOf("epub")) to reader, true),
                Triple("pdf, no download", book(listOf("pdf")) to reader, true),
                Triple("mobi with an epub file, no download", book(listOf("mobi"), listOf(file(1, "epub", 1))) to reader, true),
                Triple("m4b and mobi, no download", book(listOf("m4b", "mobi")) to reader, false),
                Triple("epub, no user", book(listOf("epub")) to null, true),
                Triple("m4b, no user", book(listOf("m4b")) to null, false),
                Triple("EPUB upper case, no download", book(listOf("EPUB")) to reader, true),
                Triple("Pdf mixed case, no download", book(listOf("Pdf")) to reader, true),
                Triple("no formats, no user", book(emptyList()) to null, false),
            )
            cases.forEach { (label, input, expected) ->
                withClue(label) {
                    showsExportRow(input.first, input.second) shouldBe expected
                }
            }
        }

        it("#118 TC-2: picks the lowest-ordinal EPUB, else the lowest-ordinal PDF, for the Kindle send") {
            val epubOrd1 = file(2, "epub", 1)
            val pdfOrd1 = file(5, "pdf", 1)
            val epubCase = book(listOf("epub", "pdf"), listOf(file(1, "epub", 2), epubOrd1, file(3, "pdf", 3)))
            val pdfCase = book(listOf("pdf"), listOf(file(4, "pdf", 3), pdfOrd1))
            val mixedCase = book(listOf("epub", "pdf"), listOf(file(6, "pdf", 1), file(7, "epub", 2)))
            val noneCase = book(listOf("m4b"))

            kindleFile(epubCase) shouldBe epubOrd1
            hasKindleSendable(epubCase) shouldBe true
            kindleFile(pdfCase) shouldBe pdfOrd1
            hasKindleSendable(pdfCase) shouldBe true
            kindleFile(mixedCase) shouldBe file(7, "epub", 2)
            kindleFile(noneCase) shouldBe null
            hasKindleSendable(noneCase) shouldBe false
        }

        it("#118 TC-3: the Kindle gate is Oversize above 50 MB of the file that would be mailed, else Ready") {
            val email: String? = EMAIL
            val cases: List<Triple<String, Book, KindleGate>> = listOf(
                Triple("epub 50_000_001 B", book(listOf("epub"), listOf(file(1, "epub", 1, 50_000_001))), KindleGate.Oversize),
                Triple("epub 50_000_000 B", book(listOf("epub"), listOf(file(1, "epub", 1, 50_000_000))), KindleGate.Ready),
                Triple(
                    "epub 1 MB and pdf 60 MB",
                    book(listOf("epub", "pdf"), listOf(file(1, "epub", 1, MB), file(2, "pdf", 2, 60 * MB))),
                    KindleGate.Ready,
                ),
                Triple("pdf only 60 MB", book(listOf("pdf"), listOf(file(1, "pdf", 1, 60 * MB))), KindleGate.Oversize),
            )
            cases.forEach { (label, input, expected) ->
                withClue(label) {
                    kindleGate(input, email, isOnline = true) shouldBe expected
                }
            }
        }

        it("#118 TC-4: a missing or blank address gives NoAddress, then offline gives Offline, else Ready") {
            val cases: List<Triple<String, Pair<String?, Boolean>, KindleGate>> = listOf(
                Triple("address null, online", null to true, KindleGate.NoAddress),
                Triple("address empty, online", "" to true, KindleGate.NoAddress),
                Triple("address blank, online", "  " to true, KindleGate.NoAddress),
                Triple("address set, offline", EMAIL to false, KindleGate.Offline),
                Triple("address set, online", EMAIL to true, KindleGate.Ready),
            )
            cases.forEach { (label, input, expected) ->
                withClue(label) {
                    kindleGate(smallEpub, input.first, input.second) shouldBe expected
                }
            }
        }

        it("#118 TC-5: too large wins over no address and offline, and no address wins over offline") {
            val huge = book(listOf("epub"), listOf(file(1, "epub", 1, 50_000_001)))
            kindleGate(huge, kindleEmail = null, isOnline = false) shouldBe KindleGate.Oversize
            kindleGate(smallEpub, kindleEmail = null, isOnline = false) shouldBe KindleGate.NoAddress
        }
    }

    describe("the Kindle row") {

        it("#118 TC-6: a ready gate shows the send phase: idle, sending, sent or failed, none dimmed") {
            val failedUnconfirmed = KindlePhase.Failed(KindleFailure.Unconfirmed)
            val failedWithMessage = KindlePhase.Failed(KindleFailure.Undelivered, "mailbox full")
            val pdfOnly = book(listOf("pdf"), listOf(file(1, "pdf", 1)))

            exportSheet(smallEpub, reader, true, KindlePhase.Idle, null).kindle shouldBe
                KindleRow(KindleRowState.Ready, enabled = true, dimmed = false, sendsEpub = true)
            exportSheet(pdfOnly, reader, true, KindlePhase.Idle, null).kindle shouldBe
                KindleRow(KindleRowState.Ready, enabled = true, dimmed = false, sendsEpub = false)
            exportSheet(smallEpub, reader, true, KindlePhase.Sending, null).kindle shouldBe
                KindleRow(KindleRowState.Sending, enabled = false, dimmed = false, sendsEpub = true)
            exportSheet(smallEpub, reader, true, KindlePhase.Sent, null).kindle shouldBe
                KindleRow(KindleRowState.Sent, enabled = false, dimmed = false, sendsEpub = true)
            exportSheet(smallEpub, reader, true, failedUnconfirmed, null).kindle shouldBe
                KindleRow(KindleRowState.Failed, enabled = true, dimmed = false, sendsEpub = true, failure = failedUnconfirmed)
            exportSheet(smallEpub, reader, true, failedWithMessage, null).kindle shouldBe
                KindleRow(KindleRowState.Failed, enabled = true, dimmed = false, sendsEpub = true, failure = failedWithMessage)
        }

        it("#118 TC-7: offline, too large and no address stay tappable; the other gates are disabled and dimmed; no file means no row") {
            val huge = book(listOf("epub"), listOf(file(1, "epub", 1, 60 * MB)))
            val noAddress = reader.copy(kindleEmail = null)

            exportSheet(huge, reader, false, KindlePhase.Idle, null).kindle shouldBe
                KindleRow(KindleRowState.Oversize, enabled = true, dimmed = false, sendsEpub = true)
            exportSheet(smallEpub, noAddress, false, KindlePhase.Idle, null).kindle shouldBe
                KindleRow(KindleRowState.NoAddress, enabled = true, dimmed = false, sendsEpub = true)
            exportSheet(smallEpub, reader, false, KindlePhase.Idle, null).kindle shouldBe
                KindleRow(KindleRowState.Offline, enabled = false, dimmed = true, sendsEpub = true)
            exportSheet(book(listOf("m4b")), reader, false, KindlePhase.Idle, null).kindle shouldBe null
        }

        it("#118 TC-8: the gate decides the row even while a send is sending or failed") {
            val huge = book(listOf("epub"), listOf(file(1, "epub", 1, 60 * MB)))
            val noAddress = reader.copy(kindleEmail = null)
            val phases = listOf(KindlePhase.Sending, KindlePhase.Failed(KindleFailure.Unknown))

            phases.forEach { phase ->
                withClue("phase=$phase") {
                    exportSheet(huge, reader, true, phase, null).kindle shouldBe
                        KindleRow(KindleRowState.Oversize, enabled = true, dimmed = false, sendsEpub = true)
                    exportSheet(smallEpub, noAddress, true, phase, null).kindle shouldBe
                        KindleRow(KindleRowState.NoAddress, enabled = true, dimmed = false, sendsEpub = true)
                }
            }
        }
    }

    describe("the sections") {

        it("#118 TC-9: each book and user combination shows the expected rows and sections") {
            val epubBook = book(listOf("epub"), listOf(file(1, "epub", 1)))
            val pdfBook = book(listOf("pdf"), listOf(file(1, "pdf", 1)))
            val audioBook = book(listOf("m4b"), listOf(file(1, "m4b", 1)))
            val cases = listOf(
                SectionCase("EPUB, may download", epubBook, downloader, kindle = true, kobo = true, ebookFormat = "EPUB", audio = false, showsSend = true, showsSave = true),
                SectionCase("EPUB, no download", epubBook, reader, kindle = true, kobo = false, ebookFormat = null, audio = false, showsSend = true, showsSave = false),
                SectionCase("PDF, may download", pdfBook, downloader, kindle = true, kobo = false, ebookFormat = "PDF", audio = false, showsSend = true, showsSave = true),
                SectionCase("PDF, no download", pdfBook, reader, kindle = true, kobo = false, ebookFormat = null, audio = false, showsSend = true, showsSave = false),
                SectionCase("audio only, may download", audioBook, downloader, kindle = false, kobo = false, ebookFormat = null, audio = true, showsSend = false, showsSave = true),
                SectionCase("audio only, no download", audioBook, reader, kindle = false, kobo = false, ebookFormat = null, audio = false, showsSend = false, showsSave = false),
                SectionCase("mobi and cbz, may download", book(listOf("mobi", "cbz")), downloader, kindle = false, kobo = false, ebookFormat = null, audio = false, showsSend = false, showsSave = false),
            )
            cases.forEach { case ->
                withClue(case.label) {
                    val model = exportSheet(case.book, case.user, true, KindlePhase.Idle, null)
                    (model.kindle != null) shouldBe case.kindle
                    (model.kobo != null) shouldBe case.kobo
                    model.ebook?.format shouldBe case.ebookFormat
                    (model.audio != null) shouldBe case.audio
                    model.showsSendSection shouldBe case.showsSend
                    model.showsSaveSection shouldBe case.showsSave
                }
            }
        }

        it("#118 TC-10: the ebook and audiobook targets use the EPUB or PDF and the lowest-ordinal m4b, m4a or mp3") {
            val epubWithPdf = book(listOf("epub", "pdf"), listOf(file(1, "epub", 1, 412_000), file(2, "pdf", 2, 2 * MB)))
            val pdfOnly = book(listOf("pdf"), listOf(file(1, "pdf", 1, 3 * MB)))
            val audio = book(
                listOf("flac", "m4b", "mp3"),
                listOf(file(1, "flac", 0, 5 * MB), file(2, "m4b", 1, 138_000_000), file(3, "mp3", 2, 9 * MB)),
            )
            val flacOnly = book(listOf("flac"), listOf(file(1, "flac", 1, 5)))

            ebookTarget(epubWithPdf) shouldBe EbookTarget("EPUB", 412_000, "epub")
            ebookTarget(pdfOnly) shouldBe EbookTarget("PDF", 3 * MB, "pdf")
            audioTarget(audio) shouldBe AudioTarget("M4B", 138_000_000, "m4b")
            audioTarget(flacOnly) shouldBe AudioTarget(null, 0, "m4b")
        }

        it("#118 TC-11: while one save runs, its row is busy and the other two save rows are disabled and dimmed") {
            val both = book(listOf("epub", "m4b"), listOf(file(1, "epub", 1, 412_000), file(2, "m4b", 1, 138_000_000)))

            for (kind in listOf(ExportSave.Kobo, ExportSave.Ebook, ExportSave.Audio)) {
                withClue("saving=$kind") {
                    val model = exportSheet(both, downloader, true, KindlePhase.Idle, saving = kind)
                    val rows = listOfNotNull(model.kobo, model.ebook, model.audio)
                    rows.size shouldBe 3
                    for (row in rows) {
                        if (row.kind == kind) {
                            row.busy shouldBe true
                            row.enabled shouldBe false
                            row.dimmed shouldBe false
                        } else {
                            row.busy shouldBe false
                            row.enabled shouldBe false
                            row.dimmed shouldBe true
                        }
                    }
                }
            }

            val sending = exportSheet(both, downloader, true, KindlePhase.Sending, saving = ExportSave.Ebook)
            sending.kindle shouldBe KindleRow(KindleRowState.Sending, enabled = false, dimmed = false, sendsEpub = true)
            sending.ebook?.busy shouldBe true
        }

        it("#118 TC-12: offline, every save row is disabled, dimmed and marked offline; back online it is enabled again") {
            val both = book(listOf("epub", "m4b"), listOf(file(1, "epub", 1, 412_000), file(2, "m4b", 1, 138_000_000)))

            val offline = exportSheet(both, downloader, false, KindlePhase.Idle, null)
            for (row in listOfNotNull(offline.kobo, offline.ebook, offline.audio)) {
                withClue("offline ${row.kind}") {
                    row.offline shouldBe true
                    row.enabled shouldBe false
                    row.dimmed shouldBe true
                }
            }

            val online = exportSheet(both, downloader, true, KindlePhase.Idle, null)
            for (row in listOfNotNull(online.kobo, online.ebook, online.audio)) {
                withClue("online ${row.kind}") {
                    row.offline shouldBe false
                    row.enabled shouldBe true
                    row.dimmed shouldBe false
                }
            }
        }
    }
})
