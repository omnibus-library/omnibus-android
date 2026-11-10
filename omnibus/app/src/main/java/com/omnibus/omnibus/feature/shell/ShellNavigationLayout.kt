package com.omnibus.omnibus.feature.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature

/** Which navigation chrome the signed-in shell shows. */
enum class ShellNavigationLayout { Bar, Rail }

/** The narrowest window, in dp, that gets the rail: Material's compact/medium boundary. */
internal val RailBreakpoint: Dp = 600.dp

/** The rail from [RailBreakpoint] up, the bar below it and always in tabletop posture. */
fun shellNavigationLayout(windowWidth: Dp, isTabletop: Boolean): ShellNavigationLayout =
    if (!isTabletop && windowWidth >= RailBreakpoint) ShellNavigationLayout.Rail else ShellNavigationLayout.Bar

/** Whether a fold is half open on a horizontal hinge (tabletop). Book posture (vertical hinge) is not. */
fun isTabletop(features: List<DisplayFeature>): Boolean = features.any {
    it is FoldingFeature &&
        it.state == FoldingFeature.State.HALF_OPENED &&
        it.orientation == FoldingFeature.Orientation.HORIZONTAL
}
