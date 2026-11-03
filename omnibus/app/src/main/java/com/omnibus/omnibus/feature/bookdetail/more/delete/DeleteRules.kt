package com.omnibus.omnibus.feature.bookdetail.more.delete

import com.omnibus.omnibus.data.deletion.DeletableCopy
import com.omnibus.omnibus.data.deletion.DeletableFile
import com.omnibus.omnibus.data.deletion.DeletionImpact
import com.omnibus.omnibus.data.deletion.DeletionManifest
import com.omnibus.omnibus.data.library.Book

/*
 * The Delete entry's decisions, as plain models with no Android or Compose types. The wording follows iOS
 * `DeleteSelectionCopy.resolve`, case for case. The composables map each model to a string.
 */

/** Which label the More menu's delete row shows. */
internal enum class DeleteRowLabel { Files, Record }

/** What the More menu's delete row shows. */
internal sealed interface DeleteRow {
    /** Not an admin, so there is no row. */
    data object Hidden : DeleteRow

    /** An admin who is online: a tappable row. */
    data class Enabled(val label: DeleteRowLabel) : DeleteRow

    /** An admin who is offline: the row is shown but cannot be tapped. */
    data class DisabledOffline(val label: DeleteRowLabel) : DeleteRow
}

/** The delete row for the More menu. Only an admin sees it, and only online can tap it. */
internal fun deleteRow(isAdmin: Boolean, isOnline: Boolean, hasFiles: Boolean): DeleteRow {
    if (!isAdmin) return DeleteRow.Hidden
    val label = if (hasFiles) DeleteRowLabel.Files else DeleteRowLabel.Record
    return if (isOnline) DeleteRow.Enabled(label) else DeleteRow.DisabledOffline(label)
}

/** Whether [book] has any file on disk, from its file list or its format list. */
internal fun hasFiles(book: Book): Boolean = book.bookFiles.isNotEmpty() || book.formats.isNotEmpty()

/** Whether the sheet opens on the confirm step: a book with no files and no copies has nothing to choose. */
internal fun opensOnConfirm(manifest: DeletionManifest): Boolean = manifest.itemCount == 0

/** The word for what is counted: "file" when the book has no physical copies, otherwise "item". */
internal enum class DeleteNoun { File, Item }

internal fun nounFor(hasCopies: Boolean): DeleteNoun = if (hasCopies) DeleteNoun.Item else DeleteNoun.File

/**
 * The choose step's count heading.
 *
 * @property onDisk True when every item is a file on disk, so the heading says "on disk".
 */
internal data class ChooseSummary(val count: Int, val noun: DeleteNoun, val onDisk: Boolean)

internal fun chooseSummary(manifest: DeletionManifest): ChooseSummary {
    val hasCopies = manifest.copies.isNotEmpty()
    return ChooseSummary(count = manifest.itemCount, noun = nounFor(hasCopies), onDisk = !hasCopies)
}

/** The choose step's delete pill: unpicked (disabled), or the count of what is picked. */
internal sealed interface ChooseAction {
    data class Unpicked(val noun: DeleteNoun) : ChooseAction

    data class Picked(val count: Int, val noun: DeleteNoun) : ChooseAction
}

internal fun chooseAction(picked: Int, hasCopies: Boolean): ChooseAction {
    val noun = nounFor(hasCopies)
    return if (picked == 0) ChooseAction.Unpicked(noun) else ChooseAction.Picked(picked, noun)
}

/** The confirm step's heading. [NameBook] names the book, for a single-item total or a record-only book. */
internal sealed interface ConfirmHeading {
    data object NameBook : ConfirmHeading

    data class All(val count: Int, val noun: DeleteNoun) : ConfirmHeading

    data class Some(val count: Int, val noun: DeleteNoun) : ConfirmHeading
}

/** What the confirm body names as going. [NamedFile] is one file, by its label or filename. */
internal sealed interface PickedLabel {
    data class NamedFile(val name: String) : PickedLabel

    data class Counts(val files: Int, val copies: Int) : PickedLabel
}

/** The confirm step's body. The partial cases say what stays with the book. */
internal sealed interface ConfirmBody {
    data object RecordOnly : ConfirmBody

    data object TotalFiles : ConfirmBody

    data object TotalFilesAndCopies : ConfirmBody

    data object TotalCopiesOnly : ConfirmBody

    data class PartialFiles(val picked: PickedLabel, val remaining: Int, val remainingNoun: DeleteNoun) : ConfirmBody

    data class PartialCopies(val picked: PickedLabel, val remaining: Int, val remainingNoun: DeleteNoun) : ConfirmBody

    data class PartialMixed(val picked: PickedLabel, val remaining: Int, val remainingNoun: DeleteNoun) : ConfirmBody
}

/** The confirm step's action pill. */
internal sealed interface ConfirmAction {
    data object DeleteRecord : ConfirmAction

    data object DeleteBook : ConfirmAction

    data class DeleteSome(val count: Int, val noun: DeleteNoun) : ConfirmAction
}

/** The kinds of data a whole-book delete also removes. The order is the order the losses are listed in. */
internal enum class LossKind { Highlights, JournalEntries, Bookmarks, ReadingSessions, ListeningSessions, Reviews, Shelves }

/** One kind of data a whole-book delete removes, with how many. */
internal data class Loss(val kind: LossKind, val count: Long)

/** Everything the confirm step shows. [losses] is empty unless the delete takes the whole book. */
internal data class DeleteConfirmCopy(
    val heading: ConfirmHeading,
    val body: ConfirmBody,
    val action: ConfirmAction,
    val losses: List<Loss>,
)

/** The non-zero counts of [impact], in [LossKind] order. */
internal fun losses(impact: DeletionImpact): List<Loss> = listOf(
    Loss(LossKind.Highlights, impact.highlights),
    Loss(LossKind.JournalEntries, impact.journalEntries),
    Loss(LossKind.Bookmarks, impact.bookmarks),
    Loss(LossKind.ReadingSessions, impact.readingSessions),
    Loss(LossKind.ListeningSessions, impact.listeningSessions),
    Loss(LossKind.Reviews, impact.reviews),
    Loss(LossKind.Shelves, impact.shelves),
).filter { it.count != 0L }

/**
 * The confirm step for a picked set. Only the picked ids that are in [manifest] count. The delete is total when it
 * picks everything, or when the book has nothing to pick, which is the record-only case.
 */
internal fun confirmCopy(
    manifest: DeletionManifest,
    pickedFiles: Set<Long>,
    pickedCopies: Set<Long>,
): DeleteConfirmCopy {
    val files = manifest.files.filter { it.id in pickedFiles }
    val copies = manifest.copies.filter { it.id in pickedCopies }
    val picked = files.size + copies.size
    val itemCount = manifest.itemCount
    val empty = itemCount == 0
    val total = empty || picked == itemCount
    val remaining = maxOf(0, itemCount - picked)
    val noun = nounFor(manifest.copies.isNotEmpty())

    val heading = when {
        empty || (total && picked == 1) -> ConfirmHeading.NameBook
        total -> ConfirmHeading.All(picked, noun)
        else -> ConfirmHeading.Some(picked, noun)
    }
    val body = when {
        empty -> ConfirmBody.RecordOnly
        total && manifest.files.isEmpty() -> ConfirmBody.TotalCopiesOnly
        total && manifest.copies.isNotEmpty() -> ConfirmBody.TotalFilesAndCopies
        total -> ConfirmBody.TotalFiles
        else -> {
            val label = pickedLabel(files, copies)
            when {
                files.isEmpty() -> ConfirmBody.PartialCopies(label, remaining, noun)
                copies.isNotEmpty() -> ConfirmBody.PartialMixed(label, remaining, noun)
                else -> ConfirmBody.PartialFiles(label, remaining, noun)
            }
        }
    }
    val action = when {
        empty -> ConfirmAction.DeleteRecord
        total -> ConfirmAction.DeleteBook
        else -> ConfirmAction.DeleteSome(picked, noun)
    }
    return DeleteConfirmCopy(
        heading = heading,
        body = body,
        action = action,
        losses = if (total) losses(manifest.impact) else emptyList(),
    )
}

/** The label for the picked items: the file's name when exactly one file and no copy is picked, else the counts. */
private fun pickedLabel(files: List<DeletableFile>, copies: List<DeletableCopy>): PickedLabel {
    val single = files.singleOrNull()
    return if (single != null && copies.isEmpty()) {
        PickedLabel.NamedFile(single.label?.takeIf { it.isNotBlank() } ?: single.filename)
    } else {
        PickedLabel.Counts(files.size, copies.size)
    }
}

/** The message shown after a partial delete, which keeps the page open. */
internal sealed interface DoneToast {
    /** One file was deleted. */
    data object File : DoneToast

    /** One physical copy was un-recorded. */
    data object Copy : DoneToast

    /** Several files were deleted. Used only when the book has no physical copies. */
    data class Files(val count: Int) : DoneToast

    /** Several items were deleted, with some files and some copies. */
    data class ItemsDeleted(val count: Int) : DoneToast

    /** Several physical copies were un-recorded, and no file was deleted. */
    data class ItemsRemoved(val count: Int) : DoneToast
}

/** The done message for a partial delete that removed [files] files and [copies] copies. */
internal fun doneToast(files: Int, copies: Int): DoneToast {
    val total = files + copies
    return when {
        total == 1 && files == 1 -> DoneToast.File
        total == 1 -> DoneToast.Copy
        copies == 0 -> DoneToast.Files(total)
        files == 0 -> DoneToast.ItemsRemoved(total)
        else -> DoneToast.ItemsDeleted(total)
    }
}
