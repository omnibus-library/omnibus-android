package com.omnibus.omnibus.feature.bookdetail.overview.chips

/** The chips to show for a row's raw [values]: trimmed, blank ones dropped and repeats removed, in order. */
internal fun chipValues(values: List<String>): List<String> =
    values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

/** Whether a Genres or Tags row is shown for [values]. A row with nothing to show is left out. */
internal fun showsChipRow(values: List<String>): Boolean = chipValues(values).isNotEmpty()
