package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.ui.theme.Motion
import kotlin.math.abs

/** The panel's entrance pose: its opacity, its turn about the vertical axis, and its slide in dp. */
internal data class PanelTurnPose(val alpha: Float, val rotationYDegrees: Float, val offsetDp: Float)

/**
 * The pose of an entering panel at [amount], from -1 (fully to the left) through 0 (at rest) to +1 (fully to the
 * right). The panel fades in as it comes to rest, and its turn and slide follow the tapped side (iOS `PanelTurn`).
 */
internal fun panelTurnPose(amount: Float): PanelTurnPose = PanelTurnPose(
    alpha = 1f - abs(amount),
    rotationYDegrees = -6f * amount,
    offsetDp = 40f * amount,
)

/**
 * The panel for the selected tab, listing that tab's sections in order. The old panel leaves at once and the new one
 * turns in from the side of the tapped tab (R5); the first panel, and any panel under reduced motion, is at rest.
 * Sections are not spaced from each other: each owns its top spacing. [minHeight] keeps the page scrollable (R3).
 */
@Composable
internal fun BookTabPanel(
    state: BookDetailTabState,
    context: BookDetailSectionContext,
    slots: BookDetailSlots,
    minHeight: Dp,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val tab = state.selected
    key(tab) {
        // Keyed by tab, so a change starts a new entrance and the previous panel's animation is cancelled with it.
        val amount = remember { Animatable(if (reducedMotion) 0f else state.entryDirection.toFloat()) }
        LaunchedEffect(Unit) {
            if (amount.value != 0f) amount.animateTo(0f, Motion.settle())
            // Only reached on normal completion, so a cancelled entrance never marks the panel settled.
            state.settledTab = tab
        }
        CompositionLocalProvider(LocalBookDetailAnchorRegistry provides state) {
            Column(
                modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .graphicsLayer {
                        val pose = panelTurnPose(amount.value)
                        alpha = pose.alpha
                        rotationY = pose.rotationYDegrees
                        translationX = pose.offsetDp * density
                        cameraDistance = 8f * density
                    }
                    .testTag(BookDetailTabsTestTags.PANEL)
                    .padding(start = 16.dp, end = 16.dp, top = 46.dp, bottom = 32.dp),
            ) {
                slots.sectionsFor(tab).forEach { it.Content(context, Modifier.fillMaxWidth()) }
            }
        }
    }
}

private fun BookDetailSlots.sectionsFor(tab: BookDetailTab) = when (tab) {
    BookDetailTab.Overview -> overviewSections
    BookDetailTab.Reviews -> reviewsSections
    BookDetailTab.Highlights -> highlightsSections
    BookDetailTab.Files -> filesSections
}
