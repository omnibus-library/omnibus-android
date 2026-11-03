package com.omnibus.omnibus.feature.bookdetail.more.delete

import com.omnibus.omnibus.data.deletion.DeletableCopy
import com.omnibus.omnibus.data.deletion.DeletableFile
import com.omnibus.omnibus.data.deletion.DeletionImpact
import com.omnibus.omnibus.data.deletion.DeletionManifest
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.testBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

private fun deletableFile(id: Long, label: String? = null, filename: String = "f$id.epub") =
    DeletableFile(id = id, format = "epub", filename = filename, label = label, sizeBytes = 10)

private fun deletableCopy(id: Long, isbn: String? = null, note: String? = null) =
    DeletableCopy(id = id, isbn = isbn, note = note)

private fun manifestOf(
    files: List<DeletableFile> = emptyList(),
    copies: List<DeletableCopy> = emptyList(),
    impact: DeletionImpact = DeletionImpact(),
) = DeletionManifest(files = files, copies = copies, impact = impact)

class DeleteRulesTest : DescribeSpec({

    describe("the delete row") {

        it("#121 TC-1: only an admin sees the row, and offline it is disabled, with the label from the files") {
            deleteRow(isAdmin = false, isOnline = true, hasFiles = true) shouldBe DeleteRow.Hidden
            deleteRow(isAdmin = false, isOnline = true, hasFiles = false) shouldBe DeleteRow.Hidden
            deleteRow(isAdmin = false, isOnline = false, hasFiles = true) shouldBe DeleteRow.Hidden
            deleteRow(isAdmin = false, isOnline = false, hasFiles = false) shouldBe DeleteRow.Hidden

            deleteRow(isAdmin = true, isOnline = true, hasFiles = true) shouldBe DeleteRow.Enabled(DeleteRowLabel.Files)
            deleteRow(isAdmin = true, isOnline = true, hasFiles = false) shouldBe DeleteRow.Enabled(DeleteRowLabel.Record)
            deleteRow(isAdmin = true, isOnline = false, hasFiles = true) shouldBe
                DeleteRow.DisabledOffline(DeleteRowLabel.Files)
            deleteRow(isAdmin = true, isOnline = false, hasFiles = false) shouldBe
                DeleteRow.DisabledOffline(DeleteRowLabel.Record)
        }

        it("#121 TC-2: a book has files when either its file list or its format list is non-empty") {
            hasFiles(testBook(1, formats = listOf("epub"), bookFiles = emptyList())) shouldBe true
            hasFiles(testBook(1, formats = emptyList(), bookFiles = listOf(BookFile(1, "epub", "a.epub", 0, null, 0, null)))) shouldBe true
            hasFiles(testBook(1, formats = emptyList(), bookFiles = emptyList())) shouldBe false
            hasFiles(testBook(1, formats = emptyList(), bookFiles = emptyList()).copy(hasPhysical = true)) shouldBe false
        }

        it("#121 TC-3: the sheet opens on confirm only when the book has no files and no copies") {
            opensOnConfirm(manifestOf()) shouldBe true
            opensOnConfirm(manifestOf(copies = listOf(deletableCopy(5)))) shouldBe false
            opensOnConfirm(manifestOf(files = listOf(deletableFile(1)))) shouldBe false
            opensOnConfirm(manifestOf(files = listOf(deletableFile(1)), copies = listOf(deletableCopy(5)))) shouldBe false
        }

        it("#121 TC-4: the choose step counts files on disk, or items when there are copies, and the action needs a pick") {
            chooseSummary(manifestOf(files = listOf(deletableFile(1), deletableFile(2)))) shouldBe
                ChooseSummary(count = 2, noun = DeleteNoun.File, onDisk = true)
            chooseSummary(
                manifestOf(files = listOf(deletableFile(1), deletableFile(2)), copies = listOf(deletableCopy(5))),
            ) shouldBe ChooseSummary(count = 3, noun = DeleteNoun.Item, onDisk = false)

            chooseAction(picked = 0, hasCopies = false) shouldBe ChooseAction.Unpicked(DeleteNoun.File)
            chooseAction(picked = 0, hasCopies = true) shouldBe ChooseAction.Unpicked(DeleteNoun.Item)
            chooseAction(picked = 2, hasCopies = false) shouldBe ChooseAction.Picked(2, DeleteNoun.File)
            chooseAction(picked = 2, hasCopies = true) shouldBe ChooseAction.Picked(2, DeleteNoun.Item)
        }

        it("#121 TC-5: picking every file of a files-only book is a total delete of the book") {
            val manifest = manifestOf(files = listOf(deletableFile(1), deletableFile(2)))

            confirmCopy(manifest, pickedFiles = setOf(1, 2), pickedCopies = emptySet()) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.All(2, DeleteNoun.File),
                body = ConfirmBody.TotalFiles,
                action = ConfirmAction.DeleteBook,
                losses = emptyList(),
            )
        }

        it("#121 TC-6: one file of two picked names that file, by its label or else its filename") {
            val labelled = manifestOf(files = listOf(deletableFile(1, label = "Read copy"), deletableFile(2)))
            confirmCopy(labelled, pickedFiles = setOf(1), pickedCopies = emptySet()) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.Some(1, DeleteNoun.File),
                body = ConfirmBody.PartialFiles(PickedLabel.NamedFile("Read copy"), remaining = 1, remainingNoun = DeleteNoun.File),
                action = ConfirmAction.DeleteSome(1, DeleteNoun.File),
                losses = emptyList(),
            )

            val blankLabel = manifestOf(files = listOf(deletableFile(1, label = " "), deletableFile(2)))
            confirmCopy(blankLabel, pickedFiles = setOf(1), pickedCopies = emptySet()).body shouldBe
                ConfirmBody.PartialFiles(PickedLabel.NamedFile("f1.epub"), remaining = 1, remainingNoun = DeleteNoun.File)
        }

        it("#121 TC-7: two of three files picked are counted, not named") {
            val manifest = manifestOf(files = listOf(deletableFile(1), deletableFile(2), deletableFile(3)))

            confirmCopy(manifest, pickedFiles = setOf(1, 2), pickedCopies = emptySet()) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.Some(2, DeleteNoun.File),
                body = ConfirmBody.PartialFiles(PickedLabel.Counts(2, 0), remaining = 1, remainingNoun = DeleteNoun.File),
                action = ConfirmAction.DeleteSome(2, DeleteNoun.File),
                losses = emptyList(),
            )
        }

        it("#121 TC-8: a single-item total names the book and never says all 1") {
            confirmCopy(
                manifestOf(files = listOf(deletableFile(1))),
                pickedFiles = setOf(1),
                pickedCopies = emptySet(),
            ) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.NameBook,
                body = ConfirmBody.TotalFiles,
                action = ConfirmAction.DeleteBook,
                losses = emptyList(),
            )

            confirmCopy(
                manifestOf(copies = listOf(deletableCopy(5))),
                pickedFiles = emptySet(),
                pickedCopies = setOf(5),
            ) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.NameBook,
                body = ConfirmBody.TotalCopiesOnly,
                action = ConfirmAction.DeleteBook,
                losses = emptyList(),
            )
        }

        it("#121 TC-9: a book with nothing to pick is a record-only delete") {
            confirmCopy(manifestOf(), pickedFiles = emptySet(), pickedCopies = emptySet()) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.NameBook,
                body = ConfirmBody.RecordOnly,
                action = ConfirmAction.DeleteRecord,
                losses = emptyList(),
            )
        }

        it("#121 TC-10: copies-only books count items, and a partial pick says the copies are un-recorded") {
            val manifest = manifestOf(copies = listOf(deletableCopy(5), deletableCopy(6)))

            confirmCopy(manifest, pickedFiles = emptySet(), pickedCopies = setOf(5, 6)) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.All(2, DeleteNoun.Item),
                body = ConfirmBody.TotalCopiesOnly,
                action = ConfirmAction.DeleteBook,
                losses = emptyList(),
            )
            confirmCopy(manifest, pickedFiles = emptySet(), pickedCopies = setOf(5)) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.Some(1, DeleteNoun.Item),
                body = ConfirmBody.PartialCopies(PickedLabel.Counts(0, 1), remaining = 1, remainingNoun = DeleteNoun.Item),
                action = ConfirmAction.DeleteSome(1, DeleteNoun.Item),
                losses = emptyList(),
            )
        }

        it("#121 TC-11: a mixed book counts items, and a partial pick of a file and a copy is a mixed partial") {
            val manifest = manifestOf(
                files = listOf(deletableFile(1), deletableFile(2)),
                copies = listOf(deletableCopy(5)),
            )

            confirmCopy(manifest, pickedFiles = setOf(1, 2), pickedCopies = setOf(5)) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.All(3, DeleteNoun.Item),
                body = ConfirmBody.TotalFilesAndCopies,
                action = ConfirmAction.DeleteBook,
                losses = emptyList(),
            )
            confirmCopy(manifest, pickedFiles = setOf(1), pickedCopies = setOf(5)) shouldBe DeleteConfirmCopy(
                heading = ConfirmHeading.Some(2, DeleteNoun.Item),
                body = ConfirmBody.PartialMixed(PickedLabel.Counts(1, 1), remaining = 1, remainingNoun = DeleteNoun.Item),
                action = ConfirmAction.DeleteSome(2, DeleteNoun.Item),
                losses = emptyList(),
            )
            confirmCopy(manifest, pickedFiles = setOf(1), pickedCopies = emptySet()).body shouldBe
                ConfirmBody.PartialFiles(PickedLabel.NamedFile("f1.epub"), remaining = 2, remainingNoun = DeleteNoun.Item)
        }

        it("#121 TC-12: a total delete lists the full impact in the order of the server's losses") {
            val impact = DeletionImpact(
                highlights = 4,
                journalEntries = 3,
                bookmarks = 2,
                readingSessions = 1,
                listeningSessions = 5,
                reviews = 6,
                shelves = 7,
            )
            val expected = listOf(
                Loss(LossKind.Highlights, 4),
                Loss(LossKind.JournalEntries, 3),
                Loss(LossKind.Bookmarks, 2),
                Loss(LossKind.ReadingSessions, 1),
                Loss(LossKind.ListeningSessions, 5),
                Loss(LossKind.Reviews, 6),
                Loss(LossKind.Shelves, 7),
            )

            losses(impact) shouldBe expected
            confirmCopy(
                manifestOf(files = listOf(deletableFile(1)), impact = impact),
                pickedFiles = setOf(1),
                pickedCopies = emptySet(),
            ).losses shouldBe expected
        }

        it("#121 TC-13: zero counts are left out, a partial delete lists none, and a record-only delete still lists them") {
            val impact = DeletionImpact(highlights = 1, shelves = 2)
            val files = listOf(deletableFile(1), deletableFile(2))

            confirmCopy(manifestOf(files = files, impact = impact), setOf(1, 2), emptySet()).losses shouldBe
                listOf(Loss(LossKind.Highlights, 1), Loss(LossKind.Shelves, 2))
            losses(DeletionImpact()) shouldBe emptyList()
            confirmCopy(manifestOf(files = files), setOf(1, 2), emptySet()).losses shouldBe emptyList()
            confirmCopy(manifestOf(files = files, impact = impact), setOf(1), emptySet()).losses shouldBe emptyList()
            confirmCopy(manifestOf(impact = impact), emptySet(), emptySet()).losses shouldBe
                listOf(Loss(LossKind.Highlights, 1), Loss(LossKind.Shelves, 2))
        }

        it("#121 TC-14: the done toast follows the iOS wording for files, copies and both") {
            doneToast(files = 1, copies = 0) shouldBe DoneToast.File
            doneToast(files = 2, copies = 0) shouldBe DoneToast.Files(2)
            doneToast(files = 0, copies = 1) shouldBe DoneToast.Copy
            doneToast(files = 0, copies = 2) shouldBe DoneToast.ItemsRemoved(2)
            doneToast(files = 1, copies = 1) shouldBe DoneToast.ItemsDeleted(2)
            doneToast(files = 2, copies = 1) shouldBe DoneToast.ItemsDeleted(3)
        }
    }
})
