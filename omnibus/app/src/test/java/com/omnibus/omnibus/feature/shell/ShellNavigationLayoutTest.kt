package com.omnibus.omnibus.feature.shell

import android.graphics.Rect
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.numericFloat
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private data class FakeFold(
    override val state: FoldingFeature.State,
    override val orientation: FoldingFeature.Orientation,
) : FoldingFeature {
    override val bounds: Rect get() = error("unused")
    override val isSeparating: Boolean = false
    override val occlusionType: FoldingFeature.OcclusionType = FoldingFeature.OcclusionType.NONE
}

private object FakeNonFold : DisplayFeature {
    override val bounds: Rect get() = error("unused")
}

class ShellNavigationLayoutTest : DescribeSpec({

    describe("choosing the layout") {
        context("in tabletop posture") {
            it("TC-1: shows the bar at any width") {
                checkAll(Arb.numericFloat(0f, 4000f)) { width ->
                    shellNavigationLayout(width.dp, isTabletop = true) shouldBe ShellNavigationLayout.Bar
                }
            }
        }

        context("not in tabletop posture") {
            it("TC-1: shows the bar at every width below 600dp") {
                checkAll(Arb.numericFloat(0f, 599.99f)) { width ->
                    shellNavigationLayout(width.dp, isTabletop = false) shouldBe ShellNavigationLayout.Bar
                }
            }

            it("TC-1: shows the rail at every width from 600dp up") {
                checkAll(Arb.numericFloat(600f, 4000f)) { width ->
                    shellNavigationLayout(width.dp, isTabletop = false) shouldBe ShellNavigationLayout.Rail
                }
            }

            it("TC-1: shows the rail at exactly 600dp") {
                shellNavigationLayout(600.dp, isTabletop = false) shouldBe ShellNavigationLayout.Rail
            }

            it("TC-1: shows the bar at 599.99dp") {
                shellNavigationLayout(599.99f.dp, isTabletop = false) shouldBe ShellNavigationLayout.Bar
            }
        }
    }

    describe("detecting tabletop posture") {
        it("TC-3: is tabletop only for a half-opened fold on a horizontal hinge") {
            checkAll(
                Exhaustive.of(FoldingFeature.State.FLAT, FoldingFeature.State.HALF_OPENED),
                Exhaustive.of(FoldingFeature.Orientation.HORIZONTAL, FoldingFeature.Orientation.VERTICAL),
            ) { state, orientation ->
                isTabletop(listOf(FakeFold(state, orientation))) shouldBe
                    (state == FoldingFeature.State.HALF_OPENED && orientation == FoldingFeature.Orientation.HORIZONTAL)
            }
        }

        it("TC-3: is not tabletop without any display features") {
            isTabletop(emptyList()) shouldBe false
        }

        it("TC-3: is not tabletop with only a non-folding display feature") {
            isTabletop(listOf(FakeNonFold)) shouldBe false
        }

        it("TC-3: is tabletop when a tabletop fold is listed beside a non-folding feature") {
            isTabletop(
                listOf(
                    FakeNonFold,
                    FakeFold(FoldingFeature.State.HALF_OPENED, FoldingFeature.Orientation.HORIZONTAL),
                ),
            ) shouldBe true
        }
    }
})
