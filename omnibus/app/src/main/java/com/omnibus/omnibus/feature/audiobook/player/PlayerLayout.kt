package com.omnibus.omnibus.feature.audiobook.player

/** The share of the stage band the cover takes: 76%, or 66% when the band is under 380 dp (iOS `PlayerView.coverShare`). */
internal fun coverShare(bandHeightDp: Float): Float = if (bandHeightDp < 380f) 0.66f else 0.76f

/**
 * The cover's height in dp: its share of the band, capped so its 2:3 width fits [widthDp] and at 450 dp. Never negative.
 */
internal fun coverHeightDp(bandHeightDp: Float, widthDp: Float): Float =
    minOf(bandHeightDp * coverShare(bandHeightDp), widthDp * 1.5f, 450f).coerceAtLeast(0f)
