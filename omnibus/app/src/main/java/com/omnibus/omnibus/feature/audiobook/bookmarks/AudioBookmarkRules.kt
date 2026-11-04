package com.omnibus.omnibus.feature.audiobook.bookmarks

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.currentChapter
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.data.bookmarks.BookmarkDraft
import com.omnibus.omnibus.feature.audiobook.player.PlayerToolButton
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkPlacement
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkRowModel
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.MAX_LISTENING_SECONDS
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.bookmarkRows
import java.math.BigDecimal
import java.math.RoundingMode

// Pure rules for the player's bookmarks and Sync here (#171). No Compose or Android types, so they are JVM-tested.

/**
 * Book time as an audio bookmark position (I-BMK-1): a plain decimal with at most three places, rounded down. Null when
 * [bookSeconds] is not finite. Out-of-range values are clamped to the listening range.
 */
internal fun audioBookmarkPosition(bookSeconds: Double): String? {
    if (!bookSeconds.isFinite()) return null
    val clamped = bookSeconds.coerceIn(0.0, MAX_LISTENING_SECONDS)
    return BigDecimal.valueOf(clamped).setScale(3, RoundingMode.FLOOR).stripTrailingZeros().toPlainString()
}

/**
 * The draft for a bookmark at [state]'s position, named after its chapter. Null unless [bookUuid] is the loaded book and
 * the load has settled (P-BMK-1).
 */
internal fun audioBookmarkDraft(bookUuid: String, state: PlaybackState): BookmarkDraft? {
    val book = state.book ?: return null
    if (book.bookUuid != bookUuid || state.isLoading) return null
    val position = audioBookmarkPosition(state.positionSeconds) ?: return null
    return BookmarkDraft.create(bookUuid, position, state.currentChapter?.title)
}

/**
 * Book Detail's rows for [bookmarks], with no placement, so each meta line is the date alone (P-BMK-2).
 */
internal fun playerBookmarkRows(bookUuid: String, bookmarks: List<Bookmark>): List<BookmarkRowModel> =
    bookmarkRows(bookUuid, bookmarks, chapters = null).map { it.copy(placement = BookmarkPlacement(null, null)) }

/** What a tap on a row of the player's sheet does. */
internal sealed interface PlayerBookmarkTap {
    /** Seeks the loaded book to [seconds] and plays from it. */
    data class Seek(val seconds: Double) : PlayerBookmarkTap

    /** Opens the reader at [startCfi], or at the saved place when it is null. */
    data class OpenReader(val startCfi: String?) : PlayerBookmarkTap
}

/** An audio row seeks. A text row opens the reader at its EPUB CFI, or at the saved place when it has none (P-BMK-2). */
internal fun playerBookmarkTap(row: BookmarkRowModel): PlayerBookmarkTap =
    row.listeningSeconds?.let { PlayerBookmarkTap.Seek(it) }
        ?: PlayerBookmarkTap.OpenReader((row.destination as? BookDetailDestination.Reader)?.startCfi)

/**
 * The Sync here cell, or null when it is hidden (no ebook, or another book is loaded). It is disabled with [needsConnection]
 * while offline (P-A11Y-5), and reads [syncedLabel] once synced (P-BMK-4).
 */
internal fun syncHereButton(
    state: SyncHereUIState,
    syncHereLabel: String,
    syncedLabel: String,
    needsConnection: String,
    onClick: () -> Unit,
): PlayerToolButton? {
    if (!state.visible) return null
    return PlayerToolButton(
        label = if (state.synced) syncedLabel else syncHereLabel,
        enabled = state.online,
        onClick = onClick,
        disabledDescription = needsConnection.takeIf { !state.online },
    )
}
