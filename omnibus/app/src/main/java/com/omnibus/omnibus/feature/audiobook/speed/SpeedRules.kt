package com.omnibus.omnibus.feature.audiobook.speed

import com.omnibus.omnibus.data.audiobook.playback.MAX_RATE
import com.omnibus.omnibus.data.audiobook.playback.MIN_RATE
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** The presets the speed sheet offers, in the order they are drawn (P-SPD-2, iOS presets). */
internal val SPEED_PRESETS: List<Double> = listOf(0.5, 0.8, 1.0, 1.1, 1.2, 1.5, 1.8, 2.0)

/** How many preset cells sit in one row of the sheet's grid (R3). */
internal const val PRESET_COLUMNS = 4

/** The fine-tune step, and the grid every rate snaps to (P-SPD-3, P-SPD-4). */
internal const val RATE_STEP = 0.05

/** The discrete steps between the slider's ends: 50 intervals of [RATE_STEP] (P-SPD-3). */
internal const val SLIDER_STEPS = 49

/** The slider's range, the speed bounds as floats (I-SPD-1). */
internal val SLIDER_RANGE: ClosedFloatingPointRange<Float> = MIN_RATE.toFloat()..MAX_RATE.toFloat()

/** Twentieths per unit: the integer grid that keeps snapped rates exact (1 / [RATE_STEP]). */
private const val STEPS_PER_UNIT = 20

/** Two rates within this distance are the same selection (iOS `abs(preset - rate) < 0.001`, R10). */
private const val SELECTION_TOLERANCE = 0.001

/** The multiplication sign drawn after every rate (R2, R11). */
private const val TIMES = "×"

/**
 * Clamps [rate] to [MIN_RATE]..[MAX_RATE] and rounds it to the nearest [RATE_STEP] (I-SPD-1, P-SPD-3). The result is the
 * nearest double to k/20, so repeated steps never drift. A non-finite [rate] returns 1.0.
 */
internal fun snapRate(rate: Double): Double {
    if (!rate.isFinite()) return 1.0
    val clamped = rate.coerceIn(MIN_RATE, MAX_RATE)
    return (clamped * STEPS_PER_UNIT).roundToInt() / STEPS_PER_UNIT.toDouble()
}

/** [rate] moved by [steps] fine-tune steps, snapped and clamped (P-SPD-4, P-SPD-AC2, iOS `snap(rate ± step)`). */
internal fun stepRate(rate: Double, steps: Int): Double = snapRate(rate + steps * RATE_STEP)

/** Whether [preset] is the current [rate] for the sheet's highlight (P-SPD-2). Only an exact match within the tolerance. */
internal fun isSelectedPreset(preset: Double, rate: Double): Boolean = abs(preset - rate) < SELECTION_TOLERANCE

/** Whether the slower button can take another step: false at the lower bound (P-SPD-4, see A3). */
internal fun canSlower(rate: Double): Boolean = rate > MIN_RATE + SELECTION_TOLERANCE

/** Whether the faster button can take another step: false at the upper bound (P-SPD-4, see A3). */
internal fun canFaster(rate: Double): Boolean = rate < MAX_RATE - SELECTION_TOLERANCE

/** The header's rate, two decimals in [locale]'s digits: "1.25×" in English, "1,25×" in German (P-SPD-1, AND-UI-6). */
internal fun currentRateLabel(rate: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.2f", rate) + TIMES

/** A preset's label, one decimal in [locale]: "0.5×", "2.0×" (P-SPD-2). */
internal fun presetLabel(rate: Double, locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.1f", rate) + TIMES

/** The fine-tune step as the caption shows it, "0.05" in English and "0,05" in German (P-SPD-3). */
internal fun stepAmountLabel(locale: Locale = Locale.getDefault()): String =
    String.format(locale, "%.2f", RATE_STEP)
