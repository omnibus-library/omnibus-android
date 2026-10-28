package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.ui.unit.dp

/**
 * The room `#stage` leaves around the page for the chrome's labels and floating controls, on top
 * of the system-bar insets.
 *
 * The chrome is laid over these bands and never resizes the page, because re-paginating on every
 * chrome toggle would cost a reflow per tap. They must match the literals in
 * `assets/reader/reader.html`, which a unit test checks.
 */
object ReaderBands {
    val Top = 58.dp
    val Bottom = 58.dp
    val Side = 10.dp
}
