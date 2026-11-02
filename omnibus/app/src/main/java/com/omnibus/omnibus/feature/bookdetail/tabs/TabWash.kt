package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.stage.BookStageWashPose
import com.omnibus.omnibus.ui.theme.Motion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * How far the stage wash is panned for [tab], as a fraction of its width. The tabs step the pan from the right
 * (Overview) to the left (Files), as iOS `BookTab.backdropPan` does.
 */
internal fun tabWashPan(tab: BookDetailTab): Float = (1.5f - tab.ordinal) * 0.11f

/** The stage glow's opacity for [tab] (iOS `BookTab.warmth`). */
internal fun tabWashWarmth(tab: BookDetailTab): Float = when (tab) {
    BookDetailTab.Overview, BookDetailTab.Reviews -> 0.5f
    BookDetailTab.Highlights -> 1f
    BookDetailTab.Files -> 0.1f
}

/**
 * The stage wash's pose for the tab that [selected] returns. The pan and glow glide to each new tab, or snap under
 * reduced motion. [selected] is a lambda so the caller never reads the tab in composition, and the same pose object
 * is returned on every recomposition.
 */
@Composable
internal fun rememberTabWashPose(selected: () -> BookDetailTab, reducedMotion: Boolean): BookStageWashPose {
    val current = rememberUpdatedState(selected)
    val initial = remember { Snapshot.withoutReadObservation { selected() } }
    val pan = remember { Animatable(tabWashPan(initial)) }
    val warmth = remember { Animatable(tabWashWarmth(initial)) }
    LaunchedEffect(reducedMotion) {
        snapshotFlow { current.value() }.collectLatest { tab ->
            if (reducedMotion) {
                pan.snapTo(tabWashPan(tab))
                warmth.snapTo(tabWashWarmth(tab))
            } else {
                coroutineScope {
                    launch { pan.animateTo(tabWashPan(tab), Motion.glide()) }
                    launch { warmth.animateTo(tabWashWarmth(tab), Motion.glide()) }
                }
            }
        }
    }
    return remember { BookStageWashPose(pan = { pan.value }, warmth = { warmth.value }) }
}
