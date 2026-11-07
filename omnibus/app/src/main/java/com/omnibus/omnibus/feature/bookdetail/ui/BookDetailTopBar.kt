package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion

/** The height of the top bar's row, below the status bar. */
internal val TopBarRowHeight = 50.dp

/** How far the page must scroll before the top bar fills in. */
internal val TopBarFillThreshold = 300.dp

/** The fill's opacity over bg0 once the bar is filled. */
internal const val TOP_BAR_FILL_ALPHA = 0.92f

/** Whether the bar is filled at [scrollPx], past [thresholdPx]. */
internal fun isTopBarFilled(scrollPx: Int, thresholdPx: Float): Boolean = scrollPx > thresholdPx

/** The fill's animation: a snap with reduced motion, else 200 ms. */
internal fun topBarFillSpec(reducedMotion: Boolean): AnimationSpec<Float> =
    if (reducedMotion) snap() else tween(200)

/**
 * The page's top bar: a back disc, the book's title once the page has scrolled, and the [actions] at the end.
 * It fills over the status bar and the row, and takes no touches outside its disc and actions.
 *
 * @param title The book's title, shown in the bar when [filled].
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
    actions: @Composable RowScope.() -> Unit = { Spacer(Modifier.size(44.dp)) },
) {
    val palette = OmnibusTheme.palette
    val fill by animateFloatAsState(
        targetValue = if (filled) 1f else 0f,
        animationSpec = topBarFillSpec(reducedMotion),
        label = "top-bar-fill",
    )

    Box(
        modifier
            .fillMaxWidth()
            .testTag(BookDetailTestTags.TOP_BAR)
            .semantics { this[BookDetailTopBarFilledKey] = filled }
            .drawBehind { drawRect(palette.bg0.copy(alpha = TOP_BAR_FILL_ALPHA * fill)) },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(TopBarRowHeight)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackDisc(onBack)
            // Without a title the bar has no title node at all, as in two-pane mode, where the panes carry the book.
            if (title != null) {
                Text(
                    text = title,
                    style = displayTextStyle(21.sp, palette.ink0, FontWeight.SemiBold).copy(textAlign = TextAlign.Center),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
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

/** The round back button over the art. */
@Composable
private fun BackDisc(onBack: () -> Unit) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.bg0.copy(alpha = 0.42f))
            .border(1.dp, palette.ink0.copy(alpha = 0.10f), CircleShape)
            .clickable(role = Role.Button, onClick = onBack),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_book_detail_back),
            contentDescription = stringResource(R.string.book_detail_back),
            tint = if (isLightGround(palette.bg0)) palette.ink0 else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}
