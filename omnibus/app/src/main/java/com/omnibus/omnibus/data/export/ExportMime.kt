package com.omnibus.omnibus.data.export

private const val OCTET_STREAM = "application/octet-stream"

/**
 * The MIME type to share a file with: the response's `Content-Type` when it names a real type, else one
 * inferred from [fileName]'s extension.
 */
internal fun exportMimeType(contentType: String?, fileName: String): String {
    val header = contentType?.substringBefore(';')?.trim()
    if (!header.isNullOrEmpty() && !header.equals(OCTET_STREAM, ignoreCase = true)) return header
    val name = fileName.lowercase()
    return when {
        name.endsWith(".epub") -> "application/epub+zip"
        name.endsWith(".pdf") -> "application/pdf"
        name.endsWith(".m4b") || name.endsWith(".m4a") -> "audio/mp4"
        name.endsWith(".mp3") -> "audio/mpeg"
        else -> OCTET_STREAM
    }
}
