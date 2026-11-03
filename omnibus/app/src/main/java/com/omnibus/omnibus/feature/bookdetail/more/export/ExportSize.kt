package com.omnibus.omnibus.feature.bookdetail.more.export

import java.util.Locale
import kotlin.math.roundToLong

private val SIZE_UNITS = listOf("KB", "MB", "GB", "TB")
private const val META_SEPARATOR = " · "

/**
 * Formats a file size in decimal units, as iOS `ByteCountFormatter(.file)` does: one decimal below 10 (`1.4 MB`),
 * whole numbers above (`412 KB`). Returns null for an unknown size (zero or negative).
 */
internal fun exportSize(bytes: Long, locale: Locale = Locale.getDefault()): String? {
    if (bytes <= 0) return null
    if (bytes < 1000) return "$bytes bytes"
    var value = bytes / 1000.0
    var unit = 0
    while (true) {
        val tenths = (value * 10).roundToLong()
        if (tenths < 100) {
            val number = if (tenths % 10 == 0L) "${tenths / 10}" else String.format(locale, "%.1f", tenths / 10.0)
            return "$number ${SIZE_UNITS[unit]}"
        }
        val whole = value.roundToLong()
        if (whole < 1000 || unit == SIZE_UNITS.lastIndex) return "$whole ${SIZE_UNITS[unit]}"
        value /= 1000.0
        unit++
    }
}

/** The ebook row's subtitle: the size (when known) and the opens copy, joined with a middle dot. */
internal fun ebookMeta(size: String?, opens: String): String =
    listOfNotNull(size, opens).joinToString(META_SEPARATOR)

/** The audiobook row's subtitle: the format, the size and the opens copy, each when known, joined with a middle dot. */
internal fun audioMeta(format: String?, size: String?, opens: String): String =
    listOfNotNull(format, size, opens).joinToString(META_SEPARATOR)
