package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R

/*
 * The delete flow's text. Each function maps one model from DeleteRules.kt to its strings. Counts go through plurals,
 * so `%d` is never formatted with `stringResource`.
 */

@Composable
internal fun rowLabel(label: DeleteRowLabel): String = stringResource(
    when (label) {
        DeleteRowLabel.Files -> R.string.book_detail_delete_row_files
        DeleteRowLabel.Record -> R.string.book_detail_delete_row_record
    },
)

/** The choose step's count heading: "2 files on disk", or "3 items" when the book has copies. */
@Composable
internal fun chooseSummaryText(summary: ChooseSummary): String = if (summary.onDisk) {
    pluralStringResource(R.plurals.book_detail_delete_summary_files_on_disk, summary.count, summary.count)
} else {
    pluralStringResource(R.plurals.book_detail_delete_summary_items, summary.count, summary.count)
}

/** The choose step's delete pill. */
@Composable
internal fun chooseActionText(action: ChooseAction): String = when (action) {
    is ChooseAction.Unpicked -> stringResource(
        if (action.noun == DeleteNoun.File) {
            R.string.book_detail_delete_choose_files_unpicked
        } else {
            R.string.book_detail_delete_choose_items_unpicked
        },
    )
    is ChooseAction.Picked -> if (action.noun == DeleteNoun.File) {
        pluralStringResource(R.plurals.book_detail_delete_choose_files, action.count, action.count)
    } else {
        pluralStringResource(R.plurals.book_detail_delete_choose_items, action.count, action.count)
    }
}

/** The confirm step's heading. [title] is the book's display title. */
@Composable
internal fun headingText(heading: ConfirmHeading, title: String): String = when (heading) {
    ConfirmHeading.NameBook -> stringResource(R.string.book_detail_delete_heading_name, title)
    is ConfirmHeading.All -> if (heading.noun == DeleteNoun.File) {
        pluralStringResource(R.plurals.book_detail_delete_heading_all_files, heading.count, heading.count)
    } else {
        pluralStringResource(R.plurals.book_detail_delete_heading_all_items, heading.count, heading.count)
    }
    is ConfirmHeading.Some -> if (heading.noun == DeleteNoun.File) {
        pluralStringResource(R.plurals.book_detail_delete_heading_some_files, heading.count, heading.count)
    } else {
        pluralStringResource(R.plurals.book_detail_delete_heading_some_items, heading.count, heading.count)
    }
}

/** The confirm step's body. [title] is the book's display title. */
@Composable
internal fun bodyText(body: ConfirmBody, title: String): String = when (body) {
    ConfirmBody.RecordOnly -> stringResource(R.string.book_detail_delete_body_record)
    ConfirmBody.TotalFiles -> stringResource(R.string.book_detail_delete_body_total_files, title)
    ConfirmBody.TotalFilesAndCopies -> stringResource(R.string.book_detail_delete_body_total_mixed, title)
    ConfirmBody.TotalCopiesOnly -> stringResource(R.string.book_detail_delete_body_total_copies, title)
    is ConfirmBody.PartialFiles -> stringResource(
        R.string.book_detail_delete_body_partial_files,
        pickedText(body.picked),
        title,
        remainingText(body.remaining, body.remainingNoun),
    )
    is ConfirmBody.PartialCopies -> stringResource(
        R.string.book_detail_delete_body_partial_copies,
        pickedText(body.picked),
        title,
        remainingText(body.remaining, body.remainingNoun),
    )
    is ConfirmBody.PartialMixed -> stringResource(
        R.string.book_detail_delete_body_partial_mixed,
        pickedText(body.picked),
        title,
        remainingText(body.remaining, body.remainingNoun),
    )
}

/** What a partial delete names: the file's name, or the counts of files and copies. */
@Composable
private fun pickedText(picked: PickedLabel): String = when (picked) {
    is PickedLabel.NamedFile -> stringResource(R.string.book_detail_delete_picked_named, picked.name)
    is PickedLabel.Counts -> when {
        picked.files > 0 && picked.copies > 0 -> stringResource(
            R.string.book_detail_delete_picked_files_and_copies,
            pluralStringResource(R.plurals.book_detail_delete_picked_files, picked.files, picked.files),
            pluralStringResource(R.plurals.book_detail_delete_picked_copies, picked.copies, picked.copies),
        )
        picked.files > 0 -> pluralStringResource(R.plurals.book_detail_delete_picked_files, picked.files, picked.files)
        else -> pluralStringResource(R.plurals.book_detail_delete_picked_copies, picked.copies, picked.copies)
    }
}

/** How many are left with the book, in the noun that fits them. */
@Composable
private fun remainingText(remaining: Int, noun: DeleteNoun): String = if (noun == DeleteNoun.File) {
    pluralStringResource(R.plurals.book_detail_delete_remaining_files, remaining, remaining)
} else {
    pluralStringResource(R.plurals.book_detail_delete_remaining_items, remaining, remaining)
}

/** The confirm step's action pill. */
@Composable
internal fun actionText(action: ConfirmAction): String = when (action) {
    ConfirmAction.DeleteRecord -> stringResource(R.string.book_detail_delete_action_record)
    ConfirmAction.DeleteBook -> stringResource(R.string.book_detail_delete_action_book)
    is ConfirmAction.DeleteSome -> when (action.noun) {
        DeleteNoun.File -> stringResource(
            if (action.count == 1) R.string.book_detail_delete_action_file else R.string.book_detail_delete_action_files,
        )
        DeleteNoun.Item -> stringResource(
            if (action.count == 1) R.string.book_detail_delete_action_item else R.string.book_detail_delete_action_items,
        )
    }
}

/**
 * The losses line for a whole-book delete, such as "Also gone for good: 4 highlights, 2 bookmarks and 1 review.", or
 * null when nothing else goes.
 */
@Composable
internal fun lossesText(losses: List<Loss>): String? {
    if (losses.isEmpty()) return null
    val items = losses.map { lossText(it) }
    val separator = stringResource(R.string.book_detail_delete_list_separator)
    val last = stringResource(R.string.book_detail_delete_list_last)
    val joined = if (items.size == 1) items.single() else items.dropLast(1).joinToString(separator) + last + items.last()
    return stringResource(R.string.book_detail_delete_losses, joined)
}

@Composable
private fun lossText(loss: Loss): String {
    val count = loss.count.toInt()
    val id = when (loss.kind) {
        LossKind.Highlights -> R.plurals.book_detail_delete_loss_highlights
        LossKind.JournalEntries -> R.plurals.book_detail_delete_loss_journal_entries
        LossKind.Bookmarks -> R.plurals.book_detail_delete_loss_bookmarks
        LossKind.ReadingSessions -> R.plurals.book_detail_delete_loss_reading_sessions
        LossKind.ListeningSessions -> R.plurals.book_detail_delete_loss_listening_sessions
        LossKind.Reviews -> R.plurals.book_detail_delete_loss_reviews
        LossKind.Shelves -> R.plurals.book_detail_delete_loss_shelves
    }
    return pluralStringResource(id, count, count)
}

/** The done message for a partial delete. */
@Composable
internal fun doneToastText(toast: DoneToast): String = when (toast) {
    DoneToast.File -> stringResource(R.string.book_detail_delete_done_file)
    DoneToast.Copy -> stringResource(R.string.book_detail_delete_done_copy)
    is DoneToast.Files -> pluralStringResource(R.plurals.book_detail_delete_done_files, toast.count, toast.count)
    is DoneToast.ItemsDeleted -> pluralStringResource(
        R.plurals.book_detail_delete_done_items_deleted,
        toast.count,
        toast.count,
    )
    is DoneToast.ItemsRemoved -> pluralStringResource(
        R.plurals.book_detail_delete_done_items_removed,
        toast.count,
        toast.count,
    )
}

/** The inline error under the confirm body. */
@Composable
internal fun failureText(failure: DeleteFailure): String = stringResource(
    if (failure == DeleteFailure.Unreachable) {
        R.string.book_detail_delete_error_unreachable
    } else {
        R.string.book_detail_delete_error_other
    },
)
