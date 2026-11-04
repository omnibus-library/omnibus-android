package com.omnibus.omnibus.feature.audiobook.speed

import com.omnibus.omnibus.data.audiobook.playback.MAX_RATE
import com.omnibus.omnibus.data.audiobook.playback.MIN_RATE
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import kotlin.math.roundToInt

class SpeedRulesTest : DescribeSpec({

    describe("snapRate") {

        // R9, I-SPD-1: nearby inputs land on the nearest twentieth, exactly
        it("#168 TC-1: snapRate rounds to the nearest 0.05 exactly (I-SPD-1, P-SPD-3)") {
            snapRate(1.0) shouldBe 1.0
            snapRate(1.05) shouldBe 1.05
            snapRate(1.15f.toDouble()) shouldBe 1.15
            snapRate(1.1 + 0.05) shouldBe 1.15
            snapRate(1.33) shouldBe 1.35
            snapRate(1.325) shouldBe 1.35
            snapRate(0.5) shouldBe 0.5
            snapRate(3.0) shouldBe 3.0
        }

        // R9, I-SPD-1: values outside the bounds clamp to them
        it("#168 TC-2: snapRate clamps to the speed bounds (I-SPD-1)") {
            listOf(0.1, 0.0, -5.0, 0.49).forEach { snapRate(it) shouldBe MIN_RATE }
            listOf(9.0, 3.01, Double.MAX_VALUE).forEach { snapRate(it) shouldBe MAX_RATE }
        }

        // R9: a non-finite input never throws and falls back to 1.0
        it("#168 TC-3: snapRate of NaN or an infinity is 1.0 and does not throw (I-SPD-1)") {
            listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
                snapRate(it) shouldBe 1.0
            }
        }
    }

    describe("stepRate") {

        // R9, P-SPD-4: one step moves 0.05 and stops at the bounds
        it("#168 TC-4: stepRate moves one 0.05 step and stops at the bounds (P-SPD-4, I-SPD-1)") {
            stepRate(1.0, 1) shouldBe 1.05
            stepRate(1.0, -1) shouldBe 0.95
            stepRate(2.95, 1) shouldBe 3.0
            stepRate(3.0, 1) shouldBe 3.0
            stepRate(0.5, -1) shouldBe 0.5
            stepRate(0.55, -1) shouldBe 0.5
            stepRate(1.33, 0) shouldBe 1.35
        }

        // R9, P-SPD-AC2: off-grid rates snap to the nearest twentieth after one step
        it("#168 TC-5: stepRate snaps off-grid rates to the nearest twentieth (P-SPD-AC2)") {
            stepRate(1.33, -1) shouldBe 1.30
            stepRate(1.33, 1) shouldBe 1.40
            stepRate(1.37, -1) shouldBe 1.30
            stepRate(1.37, 1) shouldBe 1.40
            stepRate(1.15f.toDouble(), -1) shouldBe 1.10
            stepRate(1.15f.toDouble(), 1) shouldBe 1.20
        }

        // R9, I-SPD-1: repeated steps never drift off the grid
        it("#168 TC-6: repeated stepping never drifts off the grid (I-SPD-1, P-SPD-AC2)") {
            var rate = 1.0
            for (k in 21..40) {
                rate = stepRate(rate, 1)
                rate shouldBe k / 20.0
            }
            rate shouldBe 2.0

            for (k in 39 downTo 10) {
                rate = stepRate(rate, -1)
                rate shouldBe k / 20.0
            }
            rate shouldBe 0.5
        }
    }

    describe("selection and bounds") {

        // R10, iOS tolerance: an exact or within-0.001 match selects, anything further does not
        it("#168 TC-7: isSelectedPreset uses the 0.001 tolerance exclusively (R10)") {
            isSelectedPreset(1.0, 1.0) shouldBe true
            isSelectedPreset(1.0, 1.0000000002) shouldBe true
            isSelectedPreset(1.0, 1.0009) shouldBe true
            isSelectedPreset(1.0, 1.0011) shouldBe false
            isSelectedPreset(1.0, 1.05) shouldBe false
            isSelectedPreset(1.0, 0.9991) shouldBe true
        }

        // R10, R3, P-SPD-2: no rate selects more than one preset, and a rate between presets selects none
        it("#168 TC-8: at most one preset is selected for any rate (P-SPD-2)") {
            val rates = (10..60).map { it / 20.0 } + listOf(1.25, 1.33, 1.0000000002)
            rates.forEach { rate ->
                val matches = SPEED_PRESETS.count { isSelectedPreset(it, rate) }
                (matches <= 1) shouldBe true
            }
            listOf(1.25, 1.33).forEach { rate ->
                SPEED_PRESETS.count { isSelectedPreset(it, rate) } shouldBe 0
            }
            SPEED_PRESETS.forEach { preset ->
                SPEED_PRESETS.count { isSelectedPreset(it, preset) } shouldBe 1
                isSelectedPreset(preset, preset) shouldBe true
            }
        }

        // R10, P-SPD-4: the step buttons disable at the bounds with the same tolerance
        it("#168 TC-9: canSlower and canFaster turn off within 0.001 of the bounds (R10, P-SPD-4)") {
            canSlower(0.5) shouldBe false
            canSlower(0.5005) shouldBe false
            canSlower(0.502) shouldBe true
            canFaster(3.0) shouldBe false
            canFaster(2.9995) shouldBe false
            canFaster(2.998) shouldBe true
            canSlower(1.0) shouldBe true
            canFaster(1.0) shouldBe true
        }

        // R3, R5, P-SPD-2, P-SPD-3: the preset list and the slider's shape
        it("#168 TC-10: the presets are the eight iOS values on the grid, and the slider has 49 steps (P-SPD-2, P-SPD-3)") {
            SPEED_PRESETS shouldBe listOf(0.5, 0.8, 1.0, 1.1, 1.2, 1.5, 1.8, 2.0)
            SPEED_PRESETS.size shouldBe 8
            PRESET_COLUMNS shouldBe 4
            SPEED_PRESETS.forEach { snapRate(it) shouldBe it }
            SLIDER_RANGE.start shouldBe 0.5f
            SLIDER_RANGE.endInclusive shouldBe 3.0f
            SLIDER_STEPS shouldBe 49
            ((MAX_RATE - MIN_RATE) / RATE_STEP).roundToInt() shouldBe SLIDER_STEPS + 1
        }
    }

    describe("labels") {

        // R2, R11: the header rate has two decimals in an English locale
        it("#168 TC-11: currentRateLabel formats two decimals in US English (P-SPD-1, AND-UI-6)") {
            currentRateLabel(1.25, Locale.US) shouldBe "1.25×"
            currentRateLabel(0.5, Locale.US) shouldBe "0.50×"
            currentRateLabel(3.0, Locale.US) shouldBe "3.00×"
            currentRateLabel(1.0, Locale.US) shouldBe "1.00×"
            currentRateLabel(0.75, Locale.US) shouldBe "0.75×"
        }

        // R2, R11, AND-UI-6: the decimal separator and digits follow the locale
        it("#168 TC-12: currentRateLabel uses the locale's decimal separator and digits (AND-UI-6)") {
            currentRateLabel(0.75, Locale.GERMANY) shouldBe "0,75×"
            currentRateLabel(1.25, Locale.GERMANY) shouldBe "1,25×"

            val arabic = currentRateLabel(1.25, Locale.forLanguageTag("ar-EG"))
            arabic.endsWith("×") shouldBe true
            arabic.none { it in '0'..'9' } shouldBe true
        }

        // R3, R11: preset labels have one decimal
        it("#168 TC-13: presetLabel formats one decimal per locale (R3, AND-UI-6)") {
            SPEED_PRESETS.map { presetLabel(it, Locale.US) } shouldBe
                listOf("0.5×", "0.8×", "1.0×", "1.1×", "1.2×", "1.5×", "1.8×", "2.0×")
            SPEED_PRESETS.map { presetLabel(it, Locale.GERMANY) } shouldBe
                listOf("0,5×", "0,8×", "1,0×", "1,1×", "1,2×", "1,5×", "1,8×", "2,0×")
        }

        // R5, R11: the range ends and the step caption take their arguments in the locale's format
        it("#168 TC-14: the range ends and the step caption are formatted per locale (R5, R11)") {
            presetLabel(MIN_RATE, Locale.US) shouldBe "0.5×"
            presetLabel(MAX_RATE, Locale.US) shouldBe "3.0×"
            stepAmountLabel(Locale.US) shouldBe "0.05"
            presetLabel(MIN_RATE, Locale.GERMANY) shouldBe "0,5×"
            presetLabel(MAX_RATE, Locale.GERMANY) shouldBe "3,0×"
            stepAmountLabel(Locale.GERMANY) shouldBe "0,05"
        }

        // R11: the default-argument overloads follow the device locale, and the explicit ones don't
        it("#168 TC-15: labels default to the default locale when no locale is passed (R11)") {
            val saved = Locale.getDefault()
            try {
                Locale.setDefault(Locale.GERMANY)
                currentRateLabel(1.25) shouldBe "1,25×"
                presetLabel(1.5) shouldBe "1,5×"
                stepAmountLabel() shouldBe "0,05"
                currentRateLabel(1.25, Locale.US) shouldBe "1.25×"
            } finally {
                Locale.setDefault(saved)
            }
        }
    }
})
