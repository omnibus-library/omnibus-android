package com.omnibus.omnibus.feature.shell.chrome

import com.omnibus.omnibus.ui.theme.Motion
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

private const val EPS = 0.001f

private fun input(width: Float) = ChromeLayoutInput(availableWidth = width, bottomInset = 24f)

class ChromeMotionTest : DescribeSpec({

    describe("timings") {
        // I-MOT-2
        it("TC-13: going wide, the bar starts at once and the FAB waits 160 ms") {
            ChromeMotion.barTiming(ChromeMode.Wide, interrupted = false, reducedMotion = false) shouldBe ProgressTiming(400, 0)
            ChromeMotion.fabTiming(ChromeMode.Wide, interrupted = false, reducedMotion = false) shouldBe ProgressTiming(350, 160)
        }

        it("TC-14: going narrow, the bar waits 120 ms and the FAB starts at once") {
            ChromeMotion.barTiming(ChromeMode.Narrow, interrupted = false, reducedMotion = false) shouldBe ProgressTiming(400, 120)
            ChromeMotion.fabTiming(ChromeMode.Narrow, interrupted = false, reducedMotion = false) shouldBe ProgressTiming(350, 0)
        }

        // I-MOT-5
        it("TC-15: an interruption has no delay") {
            listOf(ChromeMode.Wide, ChromeMode.Narrow).forEach { target ->
                ChromeMotion.barTiming(target, interrupted = true, reducedMotion = false) shouldBe ProgressTiming(400, 0)
                ChromeMotion.fabTiming(target, interrupted = true, reducedMotion = false) shouldBe ProgressTiming(350, 0)
            }
        }

        // I-MOT-6
        it("TC-16: reduced motion is instant") {
            listOf(ChromeMode.Wide, ChromeMode.Narrow).forEach { target ->
                ChromeMotion.barTiming(target, interrupted = false, reducedMotion = true) shouldBe ProgressTiming(0, 0)
                ChromeMotion.fabTiming(target, interrupted = false, reducedMotion = true) shouldBe ProgressTiming(0, 0)
                ChromeMotion.dipDurationMillis(target, reducedMotion = true) shouldBe 0
            }
        }

        // I-MOT-4
        it("TC-17: dip lasts the FAB's delay plus move") {
            ChromeMotion.dipDurationMillis(ChromeMode.Wide, reducedMotion = false) shouldBe 510
            ChromeMotion.dipDurationMillis(ChromeMode.Narrow, reducedMotion = false) shouldBe 430
        }
    }

    describe("dip") {
        // I-MOT-4
        it("TC-18: dip keyframes") {
            ChromeMotion.dipScale(0f) shouldBe (1f plusOrMinus EPS)
            ChromeMotion.dipScale(0.225f) shouldBe (0.95f plusOrMinus EPS)
            ChromeMotion.dipScale(0.45f) shouldBe (0.9f plusOrMinus EPS)
            ChromeMotion.dipScale(0.725f) shouldBe (0.95f plusOrMinus EPS)
            ChromeMotion.dipScale(1f) shouldBe (1f plusOrMinus EPS)
            ChromeMotion.dipScale(-1f) shouldBe (1f plusOrMinus EPS)
            ChromeMotion.dipScale(2f) shouldBe (1f plusOrMinus EPS)
        }
    }

    describe("frame") {
        // I-MOT-1
        it("TC-19: frame interpolates narrow to wide") {
            val narrow = chromeLayout(input(412f), ChromeMode.Narrow)
            val wide = chromeLayout(input(412f), ChromeMode.Wide)

            val start = ChromeMotion.frame(narrow, wide, barProgress = 0f, fabProgress = 0f)
            val middle = ChromeMotion.frame(narrow, wide, barProgress = 0.5f, fabProgress = 0.5f)
            val end = ChromeMotion.frame(narrow, wide, barProgress = 1f, fabProgress = 1f)

            start.barWidth shouldBe (388f plusOrMinus EPS)
            middle.barWidth shouldBe (374f plusOrMinus EPS)
            end.barWidth shouldBe (360f plusOrMinus EPS)
            start.fabBottom shouldBe (108f plusOrMinus EPS)
            middle.fabBottom shouldBe (72f plusOrMinus EPS)
            end.fabBottom shouldBe (36f plusOrMinus EPS)
        }

        // I-MOT-1: a resize mid-animation moves both ends
        it("TC-20: frame follows the current input") {
            val narrow = chromeLayout(input(430f), ChromeMode.Narrow)
            val wide = chromeLayout(input(430f), ChromeMode.Wide)

            ChromeMotion.frame(narrow, wide, barProgress = 0.5f, fabProgress = 0.5f).barWidth shouldBe (383f plusOrMinus EPS)
        }

        // I-MOT-5
        it("TC-21: isInterrupted") {
            ChromeMotion.isInterrupted(0f, 0f) shouldBe false
            ChromeMotion.isInterrupted(1f, 1f) shouldBe false
            ChromeMotion.isInterrupted(0.3f, 0f) shouldBe true
            ChromeMotion.isInterrupted(1f, 0.5f) shouldBe true
            ChromeMotion.isInterrupted(0f, 1f) shouldBe true
        }

        it("TC-22: progress target per mode") {
            ChromeMotion.progressTarget(ChromeMode.Narrow) shouldBe (0f plusOrMinus EPS)
            ChromeMotion.progressTarget(ChromeMode.Wide) shouldBe (1f plusOrMinus EPS)
        }
    }

    describe("tokens") {
        // §1.4, §1.1, §1.3
        it("TC-58: tokens match the spec") {
            Motion.barResizeDuration shouldBe 400
            Motion.fabMoveDuration shouldBe 350
            Motion.toWideFabDelay shouldBe 160
            Motion.toNarrowBarDelay shouldBe 120
            Motion.toNarrowDipDuration shouldBe 430
            Motion.fabDipScale shouldBe (0.9f plusOrMinus EPS)
            Motion.fabDipAt shouldBe (0.45f plusOrMinus EPS)
            Motion.indicatorDuration shouldBe 200
            ChromeTokens.navCornerRadius shouldBe (16f plusOrMinus EPS)
        }
    }
})
