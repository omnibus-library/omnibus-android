package com.omnibus.omnibus.feature.bookdetail.tabs

import kotlin.math.abs

/** The panel's entrance pose: its opacity, its turn about the vertical axis, and its slide in dp. */
internal data class PanelTurnPose(val alpha: Float, val rotationYDegrees: Float, val offsetDp: Float)

/**
 * The pose of an entering panel at [amount], from -1 (fully to the left) through 0 (at rest) to +1 (fully to the
 * right). The panel fades in as it comes to rest, and its turn and slide follow the tapped side (iOS `PanelTurn`).
 */
internal fun panelTurnPose(amount: Float): PanelTurnPose = PanelTurnPose(
    alpha = 1f - abs(amount),
    rotationYDegrees = -6f * amount,
    offsetDp = 40f * amount,
)
