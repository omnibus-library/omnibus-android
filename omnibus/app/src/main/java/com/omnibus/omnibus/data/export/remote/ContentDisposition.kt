package com.omnibus.omnibus.data.export.remote

import java.io.ByteArrayOutputStream

/**
 * The file name a `Content-Disposition` header gives, or null when it gives none. A valid RFC 5987
 * `filename*` (percent-encoded UTF-8) wins over a plain `filename`, whichever comes first.
 */
internal fun contentDispositionFilename(header: String?): String? {
    if (header.isNullOrBlank()) return null
    var encoded: String? = null
    var plain: String? = null
    // The first piece is the disposition type (`attachment`), so only the parameters after it are read.
    for (param in splitParameters(header).drop(1)) {
        val equals = param.indexOf('=')
        if (equals < 0) continue
        val name = param.substring(0, equals).trim().lowercase()
        val value = unquote(param.substring(equals + 1).trim())
        when (name) {
            FILENAME_STAR -> if (encoded == null) encoded = decodeExtended(value)?.takeIf { it.isNotBlank() }
            FILENAME -> if (plain == null) plain = value.takeIf { it.isNotBlank() }
        }
    }
    return encoded ?: plain
}

private const val FILENAME = "filename"
private const val FILENAME_STAR = "filename*"

/** Splits on `;` outside double quotes, so a quoted name may contain a semicolon. */
private fun splitParameters(header: String): List<String> {
    val pieces = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    for (c in header) {
        when {
            c == '"' -> {
                inQuotes = !inQuotes
                current.append(c)
            }
            c == ';' && !inQuotes -> {
                pieces += current.toString()
                current.clear()
            }
            else -> current.append(c)
        }
    }
    pieces += current.toString()
    return pieces
}

private fun unquote(value: String): String =
    if (value.length >= 2 && value.first() == '"' && value.last() == '"') value.substring(1, value.length - 1) else value

/** `charset'language'percent-encoded`, decoded as UTF-8, or null when the value is malformed. */
private fun decodeExtended(value: String): String? {
    val pieces = value.split('\'', limit = 3)
    if (pieces.size != 3) return null
    return percentDecode(pieces[2])
}

/** Decodes `%XX` escapes as bytes and every other character as its UTF-8 bytes. Null when an escape is malformed. */
private fun percentDecode(encoded: String): String? {
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < encoded.length) {
        if (encoded[i] == '%') {
            if (i + 2 >= encoded.length) return null
            val high = hexValue(encoded[i + 1]) ?: return null
            val low = hexValue(encoded[i + 2]) ?: return null
            bytes.write(high * 16 + low)
            i += 3
        } else {
            val start = i
            while (i < encoded.length && encoded[i] != '%') i++
            bytes.write(encoded.substring(start, i).toByteArray(Charsets.UTF_8))
        }
    }
    return bytes.toByteArray().toString(Charsets.UTF_8)
}

private fun hexValue(c: Char): Int? = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> null
}
