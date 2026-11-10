package com.omnibus.omnibus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * One destination in an [OmnibusTabBar].
 *
 * @property label Shown under the icon and read by screen readers.
 * @property icon Outline glyph for the resting state.
 * @property selectedIcon Filled glyph for the selected state.
 */
data class OmnibusTabBarItem(
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
)

/**
 * A flush, full-width bottom bar on `bg2`: a hairline rule above outline icons that fill, on an accent-tinted pill, when selected,
 * each with a label. Its items are [OmnibusNavigationEntry]s, the same as the navigation rail's.
 *
 * The bar's background runs under the navigation bar while the items stay above it.
 *
 * @param items The tabs, left to right.
 * @param selectedIndex Index into [items] of the selected tab.
 * @param onSelect Called with the tapped tab's index, including when it is already selected so the
 * caller can treat that as "return to root".
 */
@Composable
fun OmnibusTabBar(
    items: List<OmnibusTabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.bg2)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(palette.line)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                OmnibusNavigationEntry(
                    item = item,
                    isSelected = index == selectedIndex,
                    onSelect = { onSelect(index) },
                    modifier = Modifier.weight(1f),
                    indicatorWidth = 64.dp,
                    contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
                )
            }
        }
    }
}

/** Opacity of `accent` behind the selected entry's icon, over the `bg2` fill. */
internal const val NavigationIndicatorAlpha = 0.30f

/**
 * One destination in [OmnibusTabBar] or the navigation rail: an outline icon and label, which turn filled, SemiBold
 * and `ink0` on an accent-tinted pill when selected. Its touch target is the whole entry, including [contentPadding].
 *
 * @param indicatorWidth Width of the selection pill: 64dp in the bar, 56dp in the rail.
 *
 * @param onSelect Called when tapped, including when already selected. A haptic tick plays only for a new selection.
 */
@Composable
internal fun OmnibusNavigationEntry(
    item: OmnibusTabBarItem,
    isSelected: Boolean,
    onSelect: () -> Unit,
    indicatorWidth: Dp,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val palette = OmnibusTheme.palette
    val haptics = LocalHapticFeedback.current
    val color = if (isSelected) palette.ink0 else palette.ink2

    Column(
        modifier = modifier
            .selectable(
                selected = isSelected,
                role = Role.Tab,
                onClick = {
                    if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect()
                },
            )
            .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        // The glyphs carry no description: the label beneath names the tab.
        Box(
            modifier = Modifier
                .size(width = indicatorWidth, height = 32.dp)
                .background(
                    color = if (isSelected) palette.accent.copy(alpha = NavigationIndicatorAlpha) else Color.Transparent,
                    shape = RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (isSelected) item.selectedIcon else item.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = item.label,
            style = uiTextStyle(
                size = 12,
                weight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
            ),
        )
    }
}

@Composable
@PreviewLightDark
private fun OmnibusTabBarPreview() {
    OmnibusTheme {
        OmnibusTabBar(
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
