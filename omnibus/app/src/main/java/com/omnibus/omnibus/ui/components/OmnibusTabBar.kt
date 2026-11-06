package com.omnibus.omnibus.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.shell.chrome.ChromeTokens
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * One destination in an [OmnibusTabBar].
 *
 * @property label Shown under the icon and read by screen readers.
 * @property icon Outline glyph for the resting state.
 * @property selectedIcon Glyph for the selected state.
 */
data class OmnibusTabBarItem(
    val label: String,
    @param:DrawableRes val icon: Int,
    @param:DrawableRes val selectedIcon: Int,
)

/** Test tags on the bar and its tabs, so tests can find them without depending on the layout. */
internal object OmnibusTabBarTestTags {
    const val BAR = "tab-bar"

    fun tab(index: Int) = "tab-bar-tab-$index"
}

/**
 * The floating tab bar (P-BAR-1..6): a rounded `bg2` card with a hairline border and the two-layer shadow, holding
 * an icon over a single-line label per tab. The selected tab wears an accent pill behind its icon (I-MOT-7).
 *
 * The bar pads no insets: the chrome overlay positions it above the navigation bar (I-ARCH-1). Its height is at least
 * 64 dp and grows with the font scale (I-LAY-6).
 *
 * @param items The tabs, left to right.
 * @param selectedIndex Index into [items] of the selected tab.
 * @param onSelect Called with the tapped tab's index, including when it is already selected so the caller can treat
 * that as "return to root". A tap on the selected tab fires no haptic (I-ARCH-5).
 * @param enabled False while the chrome is hidden, so its tabs take no taps.
 * @param reducedMotion Snaps the selection pill instead of animating it.
 */
@Composable
fun OmnibusTabBar(
    items: List<OmnibusTabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    reducedMotion: Boolean = false,
) {
    val palette = OmnibusTheme.palette
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(Radius.chrome)

    Row(
        modifier = modifier
            // Shadows come before the clip, or the clip would cut them off.
            .chromeShadow(shape, ChromeShadows.bar(isOmnibusDark))
            .clip(shape)
            .background(palette.bg2)
            .border(0.5.dp, palette.line, shape)
            // Registers the bar as a touch target, so a tap on its padding stops here instead of reaching the content
            // drawn underneath (I-ARCH-4). It consumes nothing, so the tabs still get their clicks.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .heightIn(min = ChromeTokens.navBarHeight.dp)
            .padding(horizontal = ChromeTokens.navBarHorizontalPadding.dp)
            .selectableGroup()
            .testTag(OmnibusTabBarTestTags.BAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selectedIndex
            TabBarEntry(
                item = item,
                isSelected = isSelected,
                enabled = enabled,
                reducedMotion = reducedMotion,
                onClick = {
                    if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(index)
                },
                modifier = Modifier.weight(1f).testTag(OmnibusTabBarTestTags.tab(index)),
            )
        }
    }
}

@Composable
private fun TabBarEntry(
    item: OmnibusTabBarItem,
    isSelected: Boolean,
    enabled: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val color = if (isSelected) palette.accent else palette.ink3
    // Accent at 16 % mixed into bg2; Compose's lerp interpolates in Oklab (tech spec §1.2).
    val pillColor = lerp(palette.bg2, palette.accent, 0.16f)
    val pill = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = if (reducedMotion) snap() else tween(Motion.indicatorDuration, easing = Motion.emphasized),
        label = "tabPill",
    )

    Column(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(selected = isSelected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        // The glyphs carry no description: the label beneath names the tab (I-ICON-4).
        Icon(
            painter = painterResource(if (isSelected) item.selectedIcon else item.icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(24.dp)
                // The pill is drawn, not laid out, so the icon-to-label gap stays 4 dp. It is 56 × 30 with corner 15,
                // centred on the icon. It grows from the centre and fades in, and the old one only fades (I-MOT-7).
                // Read in the draw phase so the animation doesn't recompose.
                .drawBehind {
                    val p = pill.value
                    if (p <= 0f) return@drawBehind
                    val w = 56.dp.toPx() * (if (isSelected) p else 1f)
                    val h = 30.dp.toPx()
                    drawRoundRect(
                        color = pillColor,
                        topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(15.dp.toPx()),
                        alpha = p.coerceIn(0f, 1f),
                    )
                },
        )
        Text(
            text = item.label,
            style = uiTextStyle(
                size = 11,
                weight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = color,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
@PreviewLightDark
private fun OmnibusTabBarPreview() {
    OmnibusTheme {
        Box(Modifier.padding(16.dp)) {
            OmnibusTabBar(
                items = listOf(
                    OmnibusTabBarItem("Library", R.drawable.ic_tab_library, R.drawable.ic_tab_library_filled),
                    OmnibusTabBarItem("Search", R.drawable.ic_tab_search, R.drawable.ic_tab_search_selected),
                    OmnibusTabBarItem("Stats", R.drawable.ic_tab_stats, R.drawable.ic_tab_stats_filled),
                    OmnibusTabBarItem("You", R.drawable.ic_tab_you, R.drawable.ic_tab_you_filled),
                ),
                selectedIndex = 0,
                onSelect = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
