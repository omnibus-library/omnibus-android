package com.omnibus.omnibus.data.reader

import kotlin.math.roundToInt

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

    companion object {
        /** The theme with [token], or null for an unknown token. */
        fun fromToken(token: String?): ReaderTheme? = entries.firstOrNull { it.token == token }
    }
}

/**
 * The text column width. [css] is set as the page's `--measure` custom property, which sizes the
 * reader's column, so it must be a CSS length: a bare number is silently dropped.
 */
enum class ReaderMargins(val token: String, val css: String) {
    Narrow("narrow", "95%"),
    Normal("normal", "80%"),
    Wide("wide", "65%"),
    ;

    companion object {
        /** The margins with [token], or null for an unknown token. */
        fun fromToken(token: String?): ReaderMargins? = entries.firstOrNull { it.token == token }
    }
}

/**
 * The reader's typeface. The tokens and labels match iOS, but the CSS stacks do not: they name
 * Android system families and end in a generic one, and Apple font names must not be copied in
 * (`georgia`, `system-ui`, `Helvetica Neue` and the like). A later "sync with iOS" must not undo
 * that. [css] is null for [Original], which keeps the book's own face.
 */
enum class ReaderTypeface(val token: String, val css: String?) {
    Original("original", null),
    Editorial("editorial", "'Instrument Serif','Noto Serif',serif"),
    Classic("classic", "'EB Garamond','Noto Serif',serif"),
    Modern("modern", "'Noto Serif',serif"),
    Sans("sans", "Roboto,'Noto Sans',sans-serif"),
    Mono("mono", "'Droid Sans Mono',monospace"),
    ;

    companion object {
        /**
         * The face with [token]. The legacy `serif`, `sans-serif` and `monospace` tokens map to
         * [Original], [Sans] and [Mono]. Null for any other value.
         */
        fun fromToken(token: String?): ReaderTypeface? = when (token) {
            "serif" -> Original
            "sans-serif" -> Sans
            "monospace" -> Mono
            else -> entries.firstOrNull { it.token == token }
        }
    }
}

/**
 * The typography and layout the reader is opened with, in the units `OmnibusReader` takes.
 *
 * Every default and bound is defined here, once. [spread] is fixed at `none` until the two-page
 * setting lands, and it is neither stored nor shown.
 *
 * @property lineHeight A unitless multiplier, always one decimal place.
 * @property spread `none` for a single column, `auto` to pair columns once the stage is wide enough.
 */
data class ReaderSettings(
    val theme: ReaderTheme = ReaderTheme.Dark,
    val typeface: ReaderTypeface = ReaderTypeface.Original,
    val fontSize: Int = 19,
    val lineHeight: Double = 1.6,
    val margins: ReaderMargins = ReaderMargins.Normal,
    val justify: Boolean = true,
    val spread: String = "none",
) {

    /** Whether the font size can go down one step. */
    val canDecreaseFontSize: Boolean get() = fontSize > FONT_SIZE_RANGE.first

    /** Whether the font size can go up one step. */
    val canIncreaseFontSize: Boolean get() = fontSize < FONT_SIZE_RANGE.last

    /** Whether the line height can go down one step. */
    val canDecreaseLineHeight: Boolean get() = lineHeightTenths(lineHeight) > LINE_HEIGHT_TENTHS.first

    /** Whether the line height can go up one step. */
    val canIncreaseLineHeight: Boolean get() = lineHeightTenths(lineHeight) < LINE_HEIGHT_TENTHS.last

    /** A copy with the font size moved by [delta] points, held inside [FONT_SIZE_RANGE]. */
    fun withFontSizeStep(delta: Int): ReaderSettings =
        copy(fontSize = (fontSize + delta).coerceIn(FONT_SIZE_RANGE))

    /** A copy with the line height moved by [delta] tenths, so 1.6 + 1 is exactly 1.7. */
    fun withLineHeightStep(delta: Int): ReaderSettings =
        copy(lineHeight = tenthsToLineHeight(lineHeightTenths(lineHeight) + delta))

    /** Clamps the font size and line height into range, and rounds the line height to one decimal. */
    fun normalized(): ReaderSettings = copy(
        fontSize = fontSize.coerceIn(FONT_SIZE_RANGE),
        lineHeight = tenthsToLineHeight(lineHeightTenths(lineHeight)),
    )

    companion object {
        /** The font sizes the sheet and the decoder allow, in points. */
        val FONT_SIZE_RANGE = 12..34

        /** The line heights the sheet and the decoder allow, in tenths (1.2 to 2.2). */
        val LINE_HEIGHT_TENTHS = 12..22

        /** Stepping in tenths keeps the value exact: 1.6 + 0.1 would be 1.7000000000000002. */
        internal fun lineHeightTenths(value: Double): Int = (value * 10).roundToInt()

        // Dividing an Int by 10.0 gives the nearest double, so 17 -> 1.7, which prints as "1.7".
        internal fun tenthsToLineHeight(tenths: Int): Double = tenths.coerceIn(LINE_HEIGHT_TENTHS) / 10.0
    }
}
