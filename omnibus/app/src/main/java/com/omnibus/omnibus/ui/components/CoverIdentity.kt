package com.omnibus.omnibus.ui.components

import com.omnibus.omnibus.ui.theme.Oklch

/**
 * What a cover needs to draw itself. Taking this rather than a whole book lets rows that carry
 * slim payloads, such as search hits, use the same treatment.
 *
 * @property uuid The book's id, which names its thumbnail on the server.
 * @property title Drawn on the generated plate and hashed into its hue.
 * @property creator The first creator's name, drawn on the generated plate.
 * @property accent The book's cover-derived accent as a CSS color (`oklch(...)` or `#rrggbb`).
 * @property hasCover Whether the book has art (`cover_url != null`). Thumbnails are only requested then.
 */
data class CoverIdentity(
    val uuid: String,
    val title: String,
    val creator: String? = null,
    val accent: String? = null,
    val hasCover: Boolean,
) {

    /**
     * The color this book is drawn in: its [accent], else a hue derived from the [title]. The
     * generated plate, the Continue card and the ambient wash all read this one value, so a book
     * without art never shows a purple plate under an orange wash.
     */
    val tone: Oklch
        get() = accent?.let(Oklch::parse) ?: Oklch(0.55, 0.09, titleHue(title))

    companion object {

        /**
         * The hue of [title] in degrees: djb2 over its UTF-8 bytes, wrapping at 64 bits, mod 360.
         * Matches the iOS client, so a title gets the same plate on every platform.
         */
        fun titleHue(title: String): Double {
            var hash = 5381UL
            for (byte in title.toByteArray(Charsets.UTF_8)) {
                hash = hash * 33UL + (byte.toInt() and 0xFF).toULong()
            }
            return (hash % 360UL).toDouble()
        }
    }
}
