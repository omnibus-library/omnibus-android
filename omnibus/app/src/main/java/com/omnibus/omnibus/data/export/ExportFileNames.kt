package com.omnibus.omnibus.data.export

import java.util.Locale

/** Names for exported files: a sanitised book title, the server's extension, and a part number for multi-part audiobooks. */
internal object ExportFileNames {

    private const val MAX_TITLE_LENGTH = 120
    private const val FALLBACK_TITLE = "Book"
    private const val KEPUB_EXTENSION = "kepub.epub"
    private val EXTENSION = Regex("[a-z0-9]{1,10}")

    /**
     * The saved name for a download of [title]. The extension comes from [suggested] (the server's file
     * name) when it has one, else from [fallbackExtension]. A [part] number makes it `"<title> - part NN.<ext>"`.
     */
    fun fileName(title: String, suggested: String?, fallbackExtension: String, part: Int? = null): String {
        val safeTitle = title
            .map { if (it == '/' || it == '\\' || it == ':') ' ' else it }
            .filterNot { it.isISOControl() }
            .joinToString("")
            .trim()
            .take(MAX_TITLE_LENGTH)
            .trimEnd()
            .ifEmpty { FALLBACK_TITLE }
        val extension = extensionOf(suggested, fallbackExtension)
        return if (part == null) {
            "$safeTitle.$extension"
        } else {
            String.format(Locale.ROOT, "%s - part %02d.%s", safeTitle, part, extension)
        }
    }

    private fun extensionOf(suggested: String?, fallbackExtension: String): String {
        val name = suggested?.trim().orEmpty()
        if (name.endsWith(".$KEPUB_EXTENSION", ignoreCase = true)) return KEPUB_EXTENSION
        val extension = name.substringAfterLast('.', "").lowercase()
        return if (EXTENSION.matches(extension)) extension else fallbackExtension.lowercase()
    }
}
