package com.omnibus.omnibus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
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
 * A flush, full-width bottom bar: a hairline rule above outline icons that fill when selected,
 * each with a tight label.
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
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.bg0)
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
                .padding(top = 9.dp, bottom = 6.dp),
        ) {
            items.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex
                TabBarEntry(
                    item = item,
                    isSelected = isSelected,
                    onClick = {
                        if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(index)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TabBarEntry(
    item: OmnibusTabBarItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val color = if (isSelected) palette.accent else palette.ink3

    Column(
        modifier = modifier
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // The glyphs carry no description: the label beneath names the tab.
        Icon(
            painter = painterResource(if (isSelected) item.selectedIcon else item.icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = item.label,
            style = uiTextStyle(
                size = 11,
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
                OmnibusTabBarItem("Authors", R.drawable.ic_tab_authors, R.drawable.ic_tab_authors_filled),
                OmnibusTabBarItem("Series", R.drawable.ic_tab_series, R.drawable.ic_tab_series_filled),
                OmnibusTabBarItem("Stats", R.drawable.ic_tab_stats, R.drawable.ic_tab_stats_filled),
                OmnibusTabBarItem("You", R.drawable.ic_tab_you, R.drawable.ic_tab_you_filled),
            ),
            selectedIndex = 0,
            onSelect = {},
        )
    }
}
