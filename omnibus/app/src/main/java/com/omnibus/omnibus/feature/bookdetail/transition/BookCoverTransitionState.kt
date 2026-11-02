package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Remembers which cover the user tapped for each book, so the page morphs from that cover. It also
 * decides whether a cover that arrives late may join the open move.
 */
@Stable
internal class BookCoverTransitionState {
    private val origins = HashMap<String, BookCoverOrigin>()
    private var pendingOpenUuid: String? = null
    private var openFrameNanos: Long? = null

    /** Records a tap on [uuid] from [origin], and starts waiting for the frame the open begins on. */
    fun opened(uuid: String, origin: BookCoverOrigin) {
        origins[uuid] = origin
        pendingOpenUuid = uuid
        openFrameNanos = null
    }

    /** Notes the frame time of the open's first frame, once. */
    fun openFrameStarted(uuid: String, frameNanos: Long) {
        if (pendingOpenUuid == uuid && openFrameNanos == null) openFrameNanos = frameNanos
    }

    /** The key for [uuid]'s page cover: the recorded tap, or the grid when there is none. */
    fun keyFor(uuid: String): BookCoverKey = BookCoverKey(uuid, origins[uuid] ?: BookCoverOrigin.Grid)

    /** Whether a cover for [uuid] first composed at [nowFrameNanos] may join the open move. */
    fun allowsJoin(uuid: String, nowFrameNanos: Long): Boolean {
        if (pendingOpenUuid != uuid) return true
        val start = openFrameNanos ?: return true
        return nowFrameNanos - start <= JOIN_WINDOW_NANOS
    }

    /** The recorded taps as `"<origin>:<uuid>"` entries. */
    fun toSaveable(): List<String> = origins.map { (uuid, origin) -> "${origin.name}:$uuid" }

    companion object {
        /** A cover that first composes later than this after the open's first frame does not join. */
        const val JOIN_WINDOW_NANOS = 100_000_000L

        /** Restores recorded taps. The uuid is everything after the first `:`; malformed entries are skipped. */
        fun fromSaveable(entries: List<String>): BookCoverTransitionState {
            val state = BookCoverTransitionState()
            for (entry in entries) {
                val separator = entry.indexOf(':')
                if (separator < 0) continue
                val origin = BookCoverOrigin.entries.firstOrNull { it.name == entry.substring(0, separator) }
                    ?: continue
                val uuid = entry.substring(separator + 1)
                if (uuid.isEmpty()) continue
                state.origins[uuid] = origin
            }
            return state
        }

        val Saver: Saver<BookCoverTransitionState, Any> = listSaver(
            save = { it.toSaveable() },
            restore = { fromSaveable(it) },
        )
    }
}

/** The transition state, kept across rotation and process death. */
@Composable
internal fun rememberBookCoverTransitionState(): BookCoverTransitionState =
    rememberSaveable(saver = BookCoverTransitionState.Saver) { BookCoverTransitionState() }
