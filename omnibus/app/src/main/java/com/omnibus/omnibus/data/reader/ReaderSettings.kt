package com.omnibus.omnibus.data.reader

/**
 * The reader's page theme. [token] is what `OmnibusReader` calls it, and [groundHex] is the page
 * ground the glue paints, which the host paints under the WebView too so a dark book never
 * opens on a white frame. Keep the grounds in step with `PAGE_GROUNDS` in `epub-reader-glue.js`.
 */
enum class ReaderTheme(val token: String, val groundHex: String, val isLight: Boolean) {
    Light("light", "#fcfbfa", isLight = true),
    Dark("dark", "#201e1b", isLight = false),
    Black("black", "#000000", isLight = false),
    Sepia("sepia", "#ede4d0", isLight = true),
    ;

    /** [groundHex] as an opaque ARGB int. */
    val groundArgb: Int get() = (0xFF000000L or groundHex.removePrefix("#").toLong(16)).toInt()
}

/**
 * The typography and layout the reader is opened with, in the units `OmnibusReader` takes.
 *
 * @property fontFamily A CSS font stack, or null to keep the book's own face.
 * @property lineHeight A unitless multiplier.
 * @property maxWidth A CSS length for the text column (`80%`).
 * @property spread `none` for a single column, `auto` to pair columns once the stage is wide enough.
 */
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.Dark,
    val fontSize: Int = 19,
    val fontFamily: String? = null,
    val lineHeight: Double = 1.6,
    val maxWidth: String = "80%",
    val justify: Boolean = true,
    val spread: String = "none",
)
