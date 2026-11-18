package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.roundToInt

/** The slots [OmnibusNavigationRail] subcomposes, so each one is measured on its own. */
private enum class RailSlot { Tabs, Footer, Hairline }

/**
 * The navigation rail for windows 600dp wide and wider: the same destinations as [OmnibusTabBar], stacked down an 80dp
 * `bg2` column on the leading edge, with a hairline on its trailing edge. The destinations are centered vertically
 * between the system bars, and the column clears the start-side system bars and display cutouts.
 *
 * @param items The tabs, top to bottom.
 * @param selectedIndex Index into [items] of the selected tab.
 * @param onSelect Called with the tapped tab's index, including when it is already selected so the caller can treat
 * that as "return to root".
 * @param footer drawn at the foot of the rail below a hairline when it has content; [space] is the height the tabs leave
 * for it (the rail's height between the system bars, less the tabs and the hairline, never negative). If the footer is
 * taller the rail scrolls, tabs first.
 */
@Composable
fun OmnibusNavigationRail(
    items: List<OmnibusTabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    footer: @Composable (space: Dp) -> Unit = {},
) {
    val palette = OmnibusTheme.palette
    val lineColor = palette.line
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom)

    BoxWithConstraints(modifier.fillMaxHeight().background(palette.bg2)) {
        val tabs: @Composable () -> Unit = {
            Column(
                Modifier.width(80.dp).selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items.forEachIndexed { index, item ->
                    OmnibusNavigationEntry(
                        item = item,
                        isSelected = index == selectedIndex,
                        onSelect = { onSelect(index) },
                        indicatorWidth = 56.dp,
                        modifier = Modifier.width(80.dp).heightIn(min = 64.dp),
                        contentPadding = PaddingValues(vertical = 6.dp),
                    )
                }
            }
        }
        SubcomposeLayout(
            Modifier
                .windowInsetsPadding(insets)
                .width(80.dp)
                // The scroll below gives the column unbounded height, so the minimum is what centers a short list.
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState()),
        ) { constraints ->
            // verticalScroll keeps the min height (the rail's height between the system bars) and lifts the max.
            val usable = constraints.minHeight
            val loose = constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity)
            val tabsPlaceable = subcompose(RailSlot.Tabs, tabs).first().measure(loose)
            val hairline = 0.5.dp.roundToPx().coerceAtLeast(1)
            val space = (usable - tabsPlaceable.height - hairline).coerceAtLeast(0).toDp()
            val footerPlaceables = subcompose(RailSlot.Footer) { footer(space) }.map { it.measure(loose) }
            val footerHeight = footerPlaceables.maxOfOrNull { it.height } ?: 0
            val gap = if (footerHeight > 0) hairline else 0
            val hairlinePlaceable = subcompose(RailSlot.Hairline) { Box(Modifier.fillMaxSize().background(lineColor)) }
                .first().measure(Constraints.fixed(constraints.maxWidth, hairline))
            val height = maxOf(usable, tabsPlaceable.height + gap + footerHeight)
            layout(constraints.maxWidth, height) {
                val region = height - footerHeight - gap
                tabsPlaceable.place(0, ((region - tabsPlaceable.height) / 2f).roundToInt().coerceAtLeast(0))
                if (footerHeight > 0) hairlinePlaceable.place(0, region)
                footerPlaceables.forEach { it.place(0, height - footerHeight) }
            }
        }
        // The hairline sits inside the 80dp column, so the rail is exactly 80dp plus the start inset wide.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxHeight()
                .width(0.5.dp)
                .background(palette.line)
        )
    }
}

@Composable
@PreviewLightDark
private fun OmnibusNavigationRailPreview() {
    OmnibusTheme {
        OmnibusNavigationRail(
            items = listOf(
                OmnibusTabBarItem("Library", R.drawable.ic_tab_library, R.drawable.ic_tab_library_filled),
                OmnibusTabBarItem("Search", R.drawable.ic_tab_search, R.drawable.ic_tab_search_filled),
                OmnibusTabBarItem("Stats", R.drawable.ic_tab_stats, R.drawable.ic_tab_stats_filled),
                OmnibusTabBarItem("You", R.drawable.ic_tab_you, R.drawable.ic_tab_you_filled),
            ),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}
