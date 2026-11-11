package com.omnibus.omnibus.data.requests

private val AudioFormats = setOf("M4B", "M4A", "MP3")

/**
 * Whether [formats] include one of [kind]. Print never counts, audio is M4B, M4A or MP3, and any other format is an
 * ebook. This is the one rule for "does the library hold this kind", so the request page, the sheet and the book page
 * agree. Names are compared case-insensitively.
 */
fun holds(kind: RequestKind, formats: List<String>): Boolean {
    val upper = formats.map { it.uppercase() }
    return when (kind) {
        RequestKind.Ebook -> upper.any { it != "PRINT" && it !in AudioFormats }
        RequestKind.Audiobook -> upper.any { it in AudioFormats }
    }
}
