package com.omnibus.omnibus.feature.shell.chrome

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe

private const val EPS = 0.001f

/** The bottom inset is 24 (gesture navigation, as the mockup) and the bar 64 tall unless a case says otherwise. */
private fun input(width: Float, fab: Boolean = false, mini: Float = 0f, bar: Float = 64f) =
    ChromeLayoutInput(availableWidth = width, bottomInset = 24f, barHeight = bar, miniPlayerHeight = mini, fabVisible = fab)

class ChromeLayoutTest : DescribeSpec({

    describe("breakpoint") {
        // P-LAY-3, I-LAY-1
        it("TC-1: breakpoint is derived, 452") {
            ChromeTokens.breakpoint shouldBe 452f
            ChromeTokens.breakpoint shouldBe (2 * 12f + 360f + 12f + 56f)
        }
    }

    describe("layouts") {
        // P-LAY-1, P-LAY-2, P-LAY-3, I-TEST-1
        it("TC-2: 320 is narrow") {
            val bare = chromeLayout(input(320f))
            val withFab = chromeLayout(input(320f, fab = true))

            bare.mode shouldBe ChromeMode.Narrow
            bare.barWidth shouldBe (296f plusOrMinus EPS)
            bare.barBottom shouldBe (32f plusOrMinus EPS)
            bare.fabEnd shouldBe (12f plusOrMinus EPS)
            bare.fabBottom shouldBe (108f plusOrMinus EPS)
            bare.contentBottomPadding shouldBe (112f plusOrMinus EPS)
            withFab.contentBottomPadding shouldBe (180f plusOrMinus EPS)
        }

        it("TC-3: 412 is narrow") {
            val bare = chromeLayout(input(412f))
            val withFab = chromeLayout(input(412f, fab = true))

            bare.mode shouldBe ChromeMode.Narrow
            bare.barWidth shouldBe (388f plusOrMinus EPS)
            bare.fabBottom shouldBe (108f plusOrMinus EPS)
            bare.contentBottomPadding shouldBe (112f plusOrMinus EPS)
            withFab.contentBottomPadding shouldBe (180f plusOrMinus EPS)
        }

        it("TC-4: 451 is narrow") {
            val layout = chromeLayout(input(451f))

            layout.mode shouldBe ChromeMode.Narrow
            layout.barWidth shouldBe (427f plusOrMinus EPS)
        }

        it("TC-5: 452 is wide, the tie goes wide") {
            val bare = chromeLayout(input(452f))
            val withFab = chromeLayout(input(452f, fab = true))

            bare.mode shouldBe ChromeMode.Wide
            bare.barWidth shouldBe (360f plusOrMinus EPS)
            bare.fabEnd shouldBe (12f plusOrMinus EPS)
            bare.fabBottom shouldBe (36f plusOrMinus EPS)
            bare.contentBottomPadding shouldBe (112f plusOrMinus EPS)
            withFab.contentBottomPadding shouldBe (112f plusOrMinus EPS)
        }

        it("TC-6: 841 is wide") {
            val layout = chromeLayout(input(841f))

            layout.mode shouldBe ChromeMode.Wide
            layout.barWidth shouldBe (360f plusOrMinus EPS)
            layout.fabBottom shouldBe (36f plusOrMinus EPS)
            layout.contentBottomPadding shouldBe (112f plusOrMinus EPS)
        }

        // P-LAY-5
        it("TC-7: FAB visibility doesn't move the breakpoint") {
            listOf(451f, 452f).forEach { width ->
                val bare = chromeLayout(input(width))
                val withFab = chromeLayout(input(width, fab = true))

                withFab.mode shouldBe bare.mode
                withFab.barWidth shouldBe (bare.barWidth plusOrMinus EPS)
            }
        }

        // I-LAY-7, P-LAY-4
        it("TC-8: mini player stacks above the bar") {
            val narrowFab = chromeLayout(input(412f, fab = true, mini = 56f))
            val wideFab = chromeLayout(input(841f, fab = true, mini = 56f))
            val narrowBare = chromeLayout(input(412f, mini = 56f))

            narrowFab.fabBottom shouldBe (172f plusOrMinus EPS)
            narrowFab.contentBottomPadding shouldBe (244f plusOrMinus EPS)
            wideFab.fabBottom shouldBe (36f plusOrMinus EPS)
            wideFab.contentBottomPadding shouldBe (176f plusOrMinus EPS)
            narrowBare.contentBottomPadding shouldBe (176f plusOrMinus EPS)
        }

        // I-LAY-6
        it("TC-9: measured bar height grows the stack, not the breakpoint") {
            val narrow = chromeLayout(input(412f, fab = true, bar = 88f))
            val wideAt452 = chromeLayout(input(452f, bar = 88f))

            narrow.fabBottom shouldBe (132f plusOrMinus EPS)
            narrow.contentBottomPadding shouldBe (204f plusOrMinus EPS)
            wideAt452.mode shouldBe ChromeMode.Wide
            wideAt452.fabBottom shouldBe (48f plusOrMinus EPS)
            chromeLayout(input(451f, bar = 88f)).mode shouldBe chromeLayout(input(451f)).mode
            chromeLayout(input(452f, bar = 88f)).mode shouldBe chromeLayout(input(452f)).mode
        }

        // P-LAY-6, I-LAY-3
        it("TC-10: horizontal values are start/end") {
            listOf(320f, 412f, 451f, 452f, 841f).forEach { width ->
                chromeLayout(input(width, fab = true)).fabEnd shouldBe (12f plusOrMinus EPS)
                chromeLayout(input(width)).fabEnd shouldBe (12f plusOrMinus EPS)
            }
        }

        it("TC-11: tiny width clamps bar width") {
            chromeLayout(input(10f)).barWidth shouldBe (0f plusOrMinus EPS)
        }

        // I-MOT-1
        it("TC-12: either mode can be computed for interpolation") {
            val wideAt412 = chromeLayout(input(412f), ChromeMode.Wide)
            val narrowAt841 = chromeLayout(input(841f), ChromeMode.Narrow)

            wideAt412.barWidth shouldBe (360f plusOrMinus EPS)
            wideAt412.fabBottom shouldBe (36f plusOrMinus EPS)
            narrowAt841.barWidth shouldBe (817f plusOrMinus EPS)
            narrowAt841.fabBottom shouldBe (108f plusOrMinus EPS)
        }
    }
})
