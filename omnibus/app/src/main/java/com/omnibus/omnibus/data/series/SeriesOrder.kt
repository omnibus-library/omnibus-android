package com.omnibus.omnibus.data.series

/** The numeric value of a series index, or null when it is null, blank, not a number, NaN or infinite. */
internal fun seriesNumber(index: String?): Double? = index?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

/** Stable sort by numeric index; null and non-numeric indexes go last in their original order. */
internal fun List<SeriesMember>.inSeriesOrder(): List<SeriesMember> =
    sortedWith(compareBy(nullsLast<Double>()) { seriesNumber(it.seriesIndex) })
