package com.omnibus.omnibus.data.bookmarks

/**
 * The fields of a bookmark to create, already checked against the server's limits. Only [create] makes one, so a
 * request is never built from a value the server would reject.
 *
 * @property position The trimmed CFI, never truncated.
 * @property title The trimmed chapter title, cut to [TITLE_MAX_LENGTH] code points, or null when blank.
 */
@ConsistentCopyVisibility
data class BookmarkDraft private constructor(val bookUuid: String, val position: String, val title: String?) {

    companion object {
        /** The longest position the server accepts, in code points. */
        const val POSITION_MAX_LENGTH = 4096

        /** The longest title the server accepts, in code points. A longer title is cut, not refused. */
        const val TITLE_MAX_LENGTH = 512

        /** The longest client id the server accepts, in code points. */
        const val CLIENT_ID_MAX_LENGTH = 64

        /**
         * The draft for a bookmark at [position] in [bookUuid], or null when there is nothing valid to send: a blank
         * uuid, a blank position, or a position longer than [POSITION_MAX_LENGTH]. Lengths count code points, as the
         * server does.
         */
        fun create(bookUuid: String, position: String?, chapterTitle: String?): BookmarkDraft? {
            if (bookUuid.isBlank()) return null
            val cfi = position?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            if (cfi.codePointCount(0, cfi.length) > POSITION_MAX_LENGTH) return null
            val title = chapterTitle?.trim()?.takeIf { it.isNotEmpty() }?.let { trimmed ->
                if (trimmed.codePointCount(0, trimmed.length) <= TITLE_MAX_LENGTH) {
                    trimmed
                } else {
                    // offsetByCodePoints never lands inside a surrogate pair, so the cut keeps every code point whole.
                    trimmed.substring(0, trimmed.offsetByCodePoints(0, TITLE_MAX_LENGTH))
                }
            }
            return BookmarkDraft(bookUuid, cfi, title)
        }
    }
}
