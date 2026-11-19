package com.omnibus.omnibus.data.highlights

/**
 * The fields of a highlight to create, already checked against the server's limits. Only [create] makes one, so a
 * request is never built from a value the server would reject.
 *
 * @property cfiRange The selection's EPUB CFI range, exactly as the page produced it.
 * @property text The trimmed passage, cut to [TEXT_MAX_LENGTH] code points, or null when blank.
 */
@ConsistentCopyVisibility
data class HighlightDraft private constructor(
    val bookUuid: String,
    val cfiRange: String,
    val color: HighlightColor,
    val text: String?,
) {

    companion object {
        /** The longest CFI range the server accepts, in code points. */
        const val CFI_RANGE_MAX_LENGTH = 4096

        /** The longest text the server accepts, in code points. A longer text is cut, not refused. */
        const val TEXT_MAX_LENGTH = 8192

        /**
         * The draft for a highlight of [cfiRange] in [bookUuid], or null when there is nothing valid to send: a blank
         * uuid, a range that is null, blank or not an `epubcfi(` one, or a range longer than [CFI_RANGE_MAX_LENGTH].
         * Lengths count code points, as the server does.
         */
        fun create(bookUuid: String, cfiRange: String?, color: HighlightColor, text: String?): HighlightDraft? {
            if (bookUuid.isBlank()) return null
            if (cfiRange == null || cfiRange.isBlank() || !cfiRange.startsWith("epubcfi(")) return null
            if (cfiRange.codePointCount(0, cfiRange.length) > CFI_RANGE_MAX_LENGTH) return null
            val passage = text?.trim()?.takeIf { it.isNotEmpty() }?.let { trimmed ->
                if (trimmed.codePointCount(0, trimmed.length) <= TEXT_MAX_LENGTH) {
                    trimmed
                } else {
                    // offsetByCodePoints never lands inside a surrogate pair, so the cut keeps every code point whole.
                    trimmed.substring(0, trimmed.offsetByCodePoints(0, TEXT_MAX_LENGTH))
                }
            }
            return HighlightDraft(bookUuid, cfiRange, color, passage)
        }
    }
}
