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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * The navigation rail for windows 600dp wide and wider: the same destinations as [OmnibusTabBar], stacked down an 80dp
 * `bg2` column on the leading edge, with a hairline on its trailing edge. The destinations are centered vertically
 * between the system bars, and the column clears the start-side system bars and display cutouts.
 *
 * @param items The tabs, top to bottom.
 * @param selectedIndex Index into [items] of the selected tab.
 * @param onSelect Called with the tapped tab's index, including when it is already selected so the caller can treat
 * that as "return to root".
 */
@Composable
fun OmnibusNavigationRail(
    items: List<OmnibusTabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val insets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Start + WindowInsetsSides.Bottom)

    BoxWithConstraints(modifier.fillMaxHeight().background(palette.bg2)) {
        Column(
            Modifier
                .windowInsetsPadding(insets)
                .width(80.dp)
                // The scroll below gives the column unbounded height, so the minimum is what centers a short list.
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
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
