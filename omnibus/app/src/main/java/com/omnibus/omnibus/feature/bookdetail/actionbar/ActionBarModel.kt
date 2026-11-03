package com.omnibus.omnibus.feature.bookdetail.actionbar

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.progress.BookReadingProgress

/**
 * What the bottom action bar shows for a book. [Primary] is the main button; [NoCopy] is the untappable note for a book
 * with nothing to read or listen to, and [Wishlist] replaces it for a book on the wishlist.
 */
sealed interface ActionBarModel {

    /**
     * The main button.
     *
     * @property fill How far the reader has got, 0 to 1, drawn as a lighter fill from the start edge.
     * @property destination Where a tap leads.
     */
    data class Primary(
        val label: ActionLabel,
        val fill: Float,
        val destination: BookDetailDestination,
    ) : ActionBarModel

    /**
     * A book with neither an ebook nor an audiobook.
     *
     * @property physical Whether the book is on the shelf as a physical copy.
     */
    data class NoCopy(val physical: Boolean) : ActionBarModel

    /**
     * A book on the reader's wishlist with no readable copy: "Find a copy" opens a store search and "Check in" opens
     * the check-in page.
     *
     * @property findCopy Where "Find a copy" leads.
     * @property checkIn Where "Check in" leads.
     */
    data class Wishlist(
        val findCopy: BookDetailDestination.ExternalUrl,
        val checkIn: BookDetailDestination.CheckIn,
    ) : ActionBarModel
}

/**
 * The label of a [ActionBarModel.Primary] button. The text is mapped in the composable, so this stays free of Android.
 * #99's extra buttons are added beside the main button in the composable, not here.
 */
sealed interface ActionLabel {

    /** Start reading a book with no saved place. */
    data object StartReading : ActionLabel

    /** Continue reading from a saved place. [chapter] is 1-based, or null when unknown ("Continue reading"). */
    data class ContinueReading(val chapter: Int?) : ActionLabel

    /** Read a book the reader has finished. */
    data object ReadAgain : ActionLabel

    /** Start listening to an audiobook with no saved place. */
    data object StartListening : ActionLabel

    /** Continue listening from a saved place. */
    data object ContinueListening : ActionLabel

    /** Listen to an audiobook the reader has finished. */
    data object ListenAgain : ActionLabel
}

/** Whether this label is for listening, so the button shows the audio mark and opens the player. */
val ActionLabel.isListening: Boolean
    get() = this is ActionLabel.StartListening || this is ActionLabel.ContinueListening || this is ActionLabel.ListenAgain

/**
 * The action bar's model for [book], with its read [status] and saved [progress].
 *
 * Order matters: a book with no readable copy is [ActionBarModel.NoCopy] first. The wishlist variant is chosen by the
 * section, not here. A finished book reads again whatever its position. A saved place always offers to continue from
 * it, even when the status is unread or unknown.
 */
fun actionBarModel(book: Book, status: ReadStatusState, progress: BookReadingProgress?): ActionBarModel {
    if (!hasReadableCopy(book)) return ActionBarModel.NoCopy(physical = book.hasPhysical)
    val known = (status as? ReadStatusState.Known)?.record?.status
    val toPlayer = book.hasAudiobook && (
        !book.hasEbook ||
            (progress?.latestMedium == ProgressFormat.Listening && (progress.audioElapsedSeconds ?: 0L) > 1L)
        )
    val label = when {
        known == ReadStatus.Finished -> if (toPlayer) ActionLabel.ListenAgain else ActionLabel.ReadAgain
        known == ReadStatus.Reading || progress != null ->
            if (toPlayer) ActionLabel.ContinueListening else ActionLabel.ContinueReading(progress?.chapterNumber)
        else -> if (toPlayer) ActionLabel.StartListening else ActionLabel.StartReading
    }
    val fill = if (known == ReadStatus.Finished) 1f else progress?.fraction.asFill()
    val destination = if (toPlayer) {
        BookDetailDestination.Player(book.uuid, progress?.audioFileId)
    } else {
        BookDetailDestination.Reader(book.uuid)
    }
    return ActionBarModel.Primary(label, fill, destination)
}

/** Whether the book has something to read or listen to: an ebook or an audiobook. */
internal fun hasReadableCopy(book: Book): Boolean = book.hasEbook || book.hasAudiobook

/** NaN and null become 0; everything else is clamped to 0..1 (+Inf becomes 1, -Inf becomes 0). */
private fun Float?.asFill(): Float = this?.takeUnless { it.isNaN() }?.coerceIn(0f, 1f) ?: 0f
