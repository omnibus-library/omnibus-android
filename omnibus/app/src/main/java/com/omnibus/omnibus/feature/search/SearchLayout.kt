package com.omnibus.omnibus.feature.search

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The widest the Search content grows (spec "Content width"). */
internal val SEARCH_CONTENT_MAX_WIDTH = 560.dp

/** The empty space each side of the Search column in a window [maxWidth] wide: zero up to 560dp, then centring. */
internal fun searchColumnSide(maxWidth: Dp): Dp = ((maxWidth - SEARCH_CONTENT_MAX_WIDTH) / 2).coerceAtLeast(0.dp)
