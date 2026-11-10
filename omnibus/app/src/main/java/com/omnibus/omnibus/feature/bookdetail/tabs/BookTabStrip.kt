package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.TextAutoSize
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.stage.HaloWarmCore
import com.omnibus.omnibus.feature.bookdetail.ui.topBarFillColor
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The tab strip under the stage. It scrolls with the page until it reaches [pinTop] (the bar's bottom), then stays
 * there, drawn over the panel on a bar-coloured ground. The pin is a layer translation read at draw time, so scrolling
 * recomposes the strip only when it pins or unpins.
 *
 * @param ribbonColor The fill of the bookmark that hangs from the selected tab.
 * @param pins False in two-pane mode, where the strip stays fixed at the top of its pane: no pin, no ground, no ribbon
 * retraction, and a hairline inset by [TabStripInset] instead.
 */
@Composable
internal fun BookTabStrip(
    state: BookDetailTabState,
    scrollState: ScrollState,
    ribbonColor: Color,
    reducedMotion: Boolean,
    pins: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val haptics = LocalHapticFeedback.current
    // Read only inside the layer and draw lambdas, so scrolling never recomposes the strip.
    val pinOffset = {
        val top = state.stripTopPx
        if (!pins || top < 0) 0 else stripPinOffsetPx(scrollState.value, top, state.pinTopPx)
    }

    // Recomposes only when the boolean flips, not on every scroll pixel.
    val density = LocalDensity.current
    val pinned by remember(scrollState, state, density, pins) {
        derivedStateOf {
            pins && tabsPinned(scrollState.value, state.stripTopPx, state.pinTopPx, with(density) { TabPinSlack.roundToPx() })
        }
    }
    val pinProgress by animateFloatAsState(
        targetValue = if (pinned) 1f else 0f,
        animationSpec = if (reducedMotion) snap<Float>() else Motion.snap<Float>(),
        label = "tab-pin",
    )

    Box(
        modifier
            .fillMaxWidth()
            .height(TabStripHeight)
            .graphicsLayer { translationY = pinOffset().toFloat() }
            .drawBehind {
                val hairline = 0.5.dp.toPx()
                if (!pins) {
                    val inset = TabStripInset.toPx()
                    drawRect(
                        palette.line,
                        topLeft = Offset(inset, size.height - hairline),
                        size = Size(size.width - 2 * inset, hairline),
                    )
                } else if (pinOffset() > 0) {
                    drawRect(topBarFillColor(palette))
                    drawRect(palette.line, topLeft = Offset(0f, size.height - hairline), size = Size(size.width, hairline))
                }
            }
            .testTag(BookDetailTabsTestTags.STRIP),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = TabStripInset).selectableGroup()) {
            BookDetailTab.entries.forEach { tab ->
                val isSelected = tab == state.selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .then(if (isSelected) Modifier.pinnedGlow(ribbonColor) { pinProgress } else Modifier)
                        .selectable(
                            selected = isSelected,
                            role = Role.Tab,
                            onClick = {
                                if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                state.select(tab)
                            },
                        )
                        .testTag(BookDetailTabsTestTags.tab(tab)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(tab.labelRes()),
                        style = uiTextStyle(
                            size = 16.sp,
                            weight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) palette.ink0 else palette.ink1,
                        ).copy(textAlign = TextAlign.Center),
                        maxLines = 1,
                        autoSize = TextAutoSize.StepBased(minFontSize = 12.8.sp, maxFontSize = 16.sp, stepSize = 0.2.sp),
                        modifier = Modifier.testTag(BookDetailTabsTestTags.tabLabel(tab)),
                    )
                }
            }
        }
        BookTabRibbon(state, ribbonColor, reducedMotion, pinProgress = { pinProgress })
    }
}

@StringRes
private fun BookDetailTab.labelRes(): Int = when (this) {
    BookDetailTab.Overview -> R.string.book_detail_tabs_overview
    BookDetailTab.Reviews -> R.string.book_detail_tabs_reviews
    BookDetailTab.Highlights -> R.string.book_detail_tabs_highlights
    BookDetailTab.Files -> R.string.book_detail_tabs_files
}

/**
 * The pinned tab's light: the cover halo's warm core and the ribbon's colour falling off, sized to the cell. It fades
 * in with [progress] as the ribbon retracts, is skipped at 0, and only draws, so it takes no touches.
 */
private fun Modifier.pinnedGlow(ribbonColor: Color, progress: () -> Float): Modifier = drawBehind {
    val alpha = progress().coerceIn(0f, 1f)
    if (alpha == 0f) return@drawBehind
    val centre = Offset(size.width / 2f, size.height / 2f)
    val radius = size.height / 2f
    scale(scaleX = size.width / size.height, scaleY = 1f, pivot = centre) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to HaloWarmCore,
                0.38f to ribbonColor.copy(alpha = 0.42f),
                0.72f to ribbonColor.copy(alpha = 0.1f),
                1f to Color.Transparent,
                center = centre,
                radius = radius,
            ),
            radius = radius,
            center = centre,
            alpha = alpha,
        )
    }
}
