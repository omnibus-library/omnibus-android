package com.omnibus.omnibus.data.reader

/**
 * Where the reader's WebView content lives.
 *
 * Everything the page needs is served from one virtual `https` origin, so epub.js sees a single
 * same-origin space and needs no cookie: the bundled reader files under [READER_PATH] and the
 * book under [BOOK_PATH].
 */
object ReaderAssets {
    /** The origin [androidx.webkit.WebViewAssetLoader] serves on by default. */
    const val HOST = "appassets.androidplatform.net"
    const val ORIGIN = "https://$HOST"

    /** Path prefix mapped to `assets/reader/`. */
    const val READER_PATH = "/reader/"

    /** Path prefix reserved for the book file, `/book/<uuid>.epub`. */
    const val BOOK_PATH = "/book/"

    const val ENTRY_URL = "$ORIGIN${READER_PATH}reader.html"

    /** The reader's `@font-face` sheet, handed to the glue as `fontsHref`. */
    const val FONTS_URL = "$ORIGIN${READER_PATH}reader-fonts.css"

    private const val ASSET_DIRECTORY = "reader"

    /** The URL epub.js fetches the book from. */
    fun bookUrl(uuid: String): String = "$ORIGIN$BOOK_PATH$uuid.epub"

    private val BOOK_FILE_NAME = Regex("([A-Za-z0-9-]+)\\.epub")

    /**
     * The book uuid in a path relative to [BOOK_PATH] (`<uuid>.epub`), or null for any other shape.
     * The uuid goes into an API path, so only letters, digits and dashes are accepted.
     */
    fun bookUuidFor(relativePath: String): String? =
        BOOK_FILE_NAME.matchEntire(relativePath.trimStart('/'))?.groupValues?.get(1)

    /**
     * Maps a path relative to [READER_PATH] (`jszip.min.js`) to its path under `assets/`
     * (`reader/jszip.min.js`), or null when it isn't a plain file name.
     *
     * The reader directory is flat, so anything that nests or climbs out of it is refused.
     */
    fun assetPathFor(relativePath: String): String? {
        val name = relativePath.trimStart('/')
        if (name.isEmpty() || name == "." || name == ".." || name.contains('/') || name.contains('\\')) {
            return null
        }
        return "$ASSET_DIRECTORY/$name"
    }

    /**
     * The MIME type to serve [fileName] with. Android's own guess for `.js` varies by release,
     * and epub.js refuses a script served as anything but JavaScript.
     */
    fun mimeTypeFor(fileName: String): String =
        when (fileName.substringAfterLast('.', "").lowercase()) {
            "html" -> "text/html"
            "js" -> "text/javascript"
            "css" -> "text/css"
            "woff2" -> "font/woff2"
            "md" -> "text/markdown"
            else -> "application/octet-stream"
        }
}
