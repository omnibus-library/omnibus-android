package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.components.OmnibusBackButton
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle

/** The height of the top bar's row, below the status bar: the Material 3 small top app bar's height. */
internal val TopBarRowHeight = TopAppBarDefaults.TopAppBarExpandedHeight

/** How far the page must scroll before the top bar fills in. */
internal val TopBarFillThreshold = 300.dp

/** The fill's opacity over bg0 once the bar is filled. */
internal const val TOP_BAR_FILL_ALPHA = 0.92f

/** Whether the bar is filled at [scrollPx], past [thresholdPx]. */
internal fun isTopBarFilled(scrollPx: Int, thresholdPx: Float): Boolean = scrollPx > thresholdPx

/** The fill's animation: a snap with reduced motion, else 200 ms. */
internal fun topBarFillSpec(reducedMotion: Boolean): AnimationSpec<Float> =
    if (reducedMotion) snap() else tween(200)

/** The color of the filled bar: bg1 at [TOP_BAR_FILL_ALPHA], shared with the pinned tab strip. */
internal fun topBarFillColor(palette: OmnibusPalette): Color = palette.bg1.copy(alpha = TOP_BAR_FILL_ALPHA)

/** The bar's content color (arrow and actions) over the art: ink on a light ground, white on a dark one. */
internal fun topBarContentColor(palette: OmnibusPalette): Color =
    if (isLightGround(palette.bg0)) palette.ink0 else Color.White

/**
 * The (offset, alpha) stops of the bg0 scrim behind the unfilled bar, so the arrow keeps at least 3:1 contrast over
 * the brightest art under it.
 */
internal fun topBarScrimStops(lightGround: Boolean): List<Pair<Float, Float>> =
    if (lightGround) {
        listOf(0f to 0.20f, 0.75f to 0.20f, 1f to 0f)
    } else {
        listOf(0f to 0.45f, 0.75f to 0.45f, 1f to 0f)
    }

/**
 * The page's top bar: a back arrow, the book's title once the page has scrolled, and the [actions] at the end.
 * It fills over the status bar and the row, and takes no touches outside its buttons.
 *
 * @param title The book's title, shown in the bar when [filled]. Null shows no title node at all.
 * @param filled Whether the bar is filled.
 * @param actions The trailing sections, from the `topBarActions` slot.
 */
@Composable
internal fun BookDetailTopBar(
    title: String?,
    filled: Boolean,
    onBack: () -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = { Spacer(Modifier.size(48.dp)) },
) {
    val palette = OmnibusTheme.palette
    val fill by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = topBarFillSpec(reducedMotion),
        label = "top-bar-fill",
    )
    val lightGround = isLightGround(palette.bg0)
    val scrimStops = remember(lightGround) { topBarScrimStops(lightGround) }
    val fillColor = topBarFillColor(palette)

    Box(
        modifier
            .fillMaxWidth()
            .testTag(BookDetailTestTags.TOP_BAR)
            .semantics { this[BookDetailTopBarFilledKey] = filled }
            .drawBehind {
                val scrim = 1f - fill
                if (scrim > 0f) {
                    drawRect(
                        Brush.verticalGradient(
                            *scrimStops.map { (offset, alpha) -> offset to palette.bg0.copy(alpha = alpha * scrim) }
                                .toTypedArray(),
                        ),
                    )
                }
                if (fill > 0f) drawRect(fillColor.copy(alpha = fillColor.alpha * fill))
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides topBarContentColor(palette)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(TopBarRowHeight)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OmnibusBackButton(onClick = onBack)
                // Without a title the bar has no title node at all, as in two-pane mode, where the panes carry the book.
                if (title != null) {
                    Text(
                        text = title,
                        style = displayTextStyle(21.sp, palette.ink0, FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .alpha(fill)
                            .clearAndSetSemantics { this[SemanticsProperties.TestTag] = BookDetailTestTags.BAR_TITLE },
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                actions()
            }
        }
    }
}
