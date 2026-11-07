package com.omnibus.omnibus.feature.audiobook.speed

import com.omnibus.omnibus.data.audiobook.playback.MAX_RATE
import com.omnibus.omnibus.data.audiobook.playback.MIN_RATE
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.of
import java.util.Locale

class SpeedRulesTest : DescribeSpec({

    describe("snapping a rate") {
        // R9, I-SPD-1: nearby inputs land on the nearest twentieth, exactly
        it("#168 TC-1: rounds to the nearest 0.05 exactly (I-SPD-1, P-SPD-3)") {
            checkAll(
                Exhaustive.of(1.15f.toDouble() to 1.15, 1.1 + 0.05 to 1.15, 1.33 to 1.35, 1.325 to 1.35),
            ) { (rate, expected) ->
                snapRate(rate) shouldBe expected
            }
        }

        // R9, I-SPD-1: values outside the bounds clamp to them; a non-finite input never throws and falls back to 1.0
        it("#168 TC-2 TC-3: clamps to the speed bounds, reading 1.0 for NaN or an infinity (I-SPD-1)") {
            checkAll(
                Exhaustive.of(
                    0.49 to MIN_RATE,
                    -5.0 to MIN_RATE,
                    3.01 to MAX_RATE,
                    Double.MAX_VALUE to MAX_RATE,
                    Double.NaN to 1.0,
                    Double.POSITIVE_INFINITY to 1.0,
                    Double.NEGATIVE_INFINITY to 1.0,
                ),
            ) { (rate, expected) ->
                snapRate(rate) shouldBe expected
            }
        }
    }

    describe("stepping a rate") {
        // R9, P-SPD-4, P-SPD-AC2: one step moves 0.05 and stops at the bounds, and off-grid rates land on the grid
        it("#168 TC-4 TC-5: moves one step onto the grid, stopping at the bounds (P-SPD-4, I-SPD-1, P-SPD-AC2)") {
            stepRate(1.0, -1) shouldBe 0.95
            stepRate(2.95, 1) shouldBe 3.0
            stepRate(3.0, 1) shouldBe 3.0
            stepRate(0.5, -1) shouldBe 0.5
            stepRate(1.33, -1) shouldBe 1.30
            stepRate(1.33, 1) shouldBe 1.40
        }

        // R9, I-SPD-1: repeated steps never drift off the grid
        it("#168 TC-6: lands exactly on the next twentieth from every point of the grid (I-SPD-1, P-SPD-AC2)") {
            checkAll(Exhaustive.collection((10..59).toList())) { k ->
                stepRate(k / 20.0, 1) shouldBe (k + 1) / 20.0
            }
        }
    }

    describe("highlighting a preset") {
        // R10, iOS tolerance: an exact or within-0.001 match selects, anything further does not
        it("#168 TC-7: matches within a tolerance of 0.001 (R10)") {
            isSelectedPreset(1.0, 1.0009) shouldBe true
            isSelectedPreset(1.0, 0.9991) shouldBe true
            isSelectedPreset(1.0, 1.0011) shouldBe false
        }
    }

    describe("enabling the step buttons") {
        // R10, P-SPD-4: the step buttons disable at the bounds with the same tolerance
        it("#168 TC-9: turns each button off within 0.001 of its bound (R10, P-SPD-4)") {
            canSlower(0.5005) shouldBe false
            canSlower(0.502) shouldBe true
            canFaster(2.9995) shouldBe false
            canFaster(2.998) shouldBe true
        }
    }

    describe("labelling a rate") {
        // R2, R11, AND-UI-6: two decimals, with the locale's decimal separator
        it("#168 TC-11 TC-12: the header has two decimals in the locale's format (P-SPD-1, AND-UI-6)") {
            currentRateLabel(0.5, Locale.GERMANY) shouldBe "0,50×"
        }

        // R3, R11: preset labels have one decimal
        it("#168 TC-13: a preset has one decimal in the locale's format (R3, AND-UI-6)") {
            presetLabel(0.5, Locale.GERMANY) shouldBe "0,5×"
        }
    }
})
