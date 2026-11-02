package com.omnibus.omnibus.feature.library.ui

import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class ContinueCardGroundTest : DescribeSpec({

    describe("continueCardColors") {
        val tone = Oklch(0.55, 0.09, 150.0)

        it("#22 TC-15: every colour keeps the tone's hue in both themes") {
            for (light in listOf(false, true)) {
                val colors = continueCardColors(tone, lightTheme = light)
                listOf(colors.baseStart, colors.baseEnd, colors.bloom, colors.rule).forEach { color ->
                    Oklch.fromSrgb(color).h shouldBe (150.0 plusOrMinus 3.0)
                }
            }
        }

        it("#22 TC-15: the base gradient darkens and loses chroma towards its end, in both themes") {
            for (light in listOf(false, true)) {
                val colors = continueCardColors(tone, lightTheme = light)
                val start = Oklch.fromSrgb(colors.baseStart)
                val end = Oklch.fromSrgb(colors.baseEnd)
                (end.l < start.l) shouldBe true
                (end.c < start.c) shouldBe true
            }
        }

        it("#22 TC-15: a chroma-0 tone gives neutral greys") {
            val grey = continueCardColors(Oklch(0.55, 0.0, 150.0), lightTheme = false)
            listOf(grey.baseStart, grey.baseEnd, grey.bloom, grey.rule).forEach { color ->
                val tolerance = 1f / 255f
                (abs(color.red - color.green) <= tolerance) shouldBe true
                (abs(color.green - color.blue) <= tolerance) shouldBe true
            }
        }
    }

    describe("light themes") {
        val tone = Oklch(0.55, 0.09, 150.0)

        it("#22 TC-16: the light palette counts as a light ground and the dark one does not") {
            isLightGround(OmnibusPalette.Light.bg0) shouldBe true
            isLightGround(OmnibusPalette.Atrium.bg0) shouldBe false
        }

        it("#22 TC-16: a light theme lifts only the base, leaving bloom and rule unchanged") {
            val dark = continueCardColors(tone, lightTheme = false)
            val light = continueCardColors(tone, lightTheme = true)

            (Oklch.fromSrgb(light.baseStart).l > Oklch.fromSrgb(dark.baseStart).l) shouldBe true
            (Oklch.fromSrgb(light.baseEnd).l > Oklch.fromSrgb(dark.baseEnd).l) shouldBe true
            light.bloom shouldBe dark.bloom
            light.rule shouldBe dark.rule
        }
    }
})
