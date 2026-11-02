package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

/** The shared-transition scope and the cover state, for the entries under the shell's [SharedTransitionLayout]. */
@Immutable
internal class BookCoverTransition(val sharedScope: SharedTransitionScope, val state: BookCoverTransitionState)

/** Null outside the shell host and under reduced motion, so every cover modifier is a no-op there. */
internal val LocalBookCoverTransition = staticCompositionLocalOf<BookCoverTransition?> { null }

/** Test tags on the moving covers, so UI tests can read their bounds. */
internal object BookCoverTransitionTestTags {
    fun source(key: BookCoverKey) = "book-cover-transition-source-${key.origin.name}-${key.uuid}"
    fun stage(uuid: String) = "book-cover-transition-stage-$uuid"
}

private val CoverBounds = BoundsTransform { _, _ -> Motion.page() }

private val CoverResize = SharedTransitionScope.ResizeMode.scaleToBounds(ContentScale.FillBounds, Alignment.Center)

/**
 * Gives the entries under [sharedScope] the cover transition, unless [reducedMotion] is set. Then every
 * entry shows a plain crossfade.
 */
@Composable
internal fun ProvideBookCoverTransition(
    sharedScope: SharedTransitionScope,
    reducedMotion: Boolean = rememberReducedMotion(),
    state: BookCoverTransitionState = rememberBookCoverTransitionState(),
    content: @Composable () -> Unit,
) {
    val value = remember(reducedMotion, sharedScope, state) {
        if (reducedMotion) null else BookCoverTransition(sharedScope, state)
    }
    CompositionLocalProvider(LocalBookCoverTransition provides value, content = content)
}

/**
 * The shared-bounds modifier for a cover on a list (the grid cell or the carousel card), keyed by [uuid] and
 * [origin]. Reads `LocalNavAnimatedContentScope` when a transition is provided, so use it only inside a
 * `NavDisplay` entry.
 */
@Composable
internal fun bookCoverSourceModifier(uuid: String, origin: BookCoverOrigin): Modifier {
    val transition = LocalBookCoverTransition.current ?: return Modifier
    val animated = LocalNavAnimatedContentScope.current
    val key = BookCoverKey(uuid, origin)
    return with(transition.sharedScope) {
        Modifier
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key),
                animatedVisibilityScope = animated,
                boundsTransform = CoverBounds,
                resizeMode = CoverResize,
            )
            .testTag(BookCoverTransitionTestTags.source(key))
    }
}

/**
 * The shared-bounds modifier for the page's stage cover. It matches the origin the user tapped (see
 * [BookCoverTransitionState.keyFor]). A cover that first composes after the open's join window stays out of the
 * move until the page has settled. Reads `LocalNavAnimatedContentScope` when a transition is provided, so use it
 * only inside a `NavDisplay` entry.
 */
@Composable
internal fun bookCoverStageModifier(uuid: String): Modifier {
    val transition = LocalBookCoverTransition.current ?: return Modifier
    val animated = LocalNavAnimatedContentScope.current
    val entry = animated.transition
    val key = remember(uuid) { transition.state.keyFor(uuid) }
    val joinAllowed = remember(uuid) { mutableStateOf(entry.currentState == entry.targetState) }
    LaunchedEffect(uuid) {
        if (!joinAllowed.value) {
            withFrameNanos { now -> joinAllowed.value = transition.state.allowsJoin(uuid, now) }
        }
    }
    val config = remember(entry, joinAllowed) {
        object : SharedTransitionScope.SharedContentConfig {
            override val SharedTransitionScope.SharedContentState.isEnabled: Boolean
                get() = joinAllowed.value || entry.currentState == EnterExitState.Visible
        }
    }
    return with(transition.sharedScope) {
        Modifier
            .sharedBounds(
                sharedContentState = rememberSharedContentState(key, config),
                animatedVisibilityScope = animated,
                boundsTransform = CoverBounds,
                resizeMode = CoverResize,
            )
            .testTag(BookCoverTransitionTestTags.stage(uuid))
    }
}

/**
 * Wraps [onOpenBook] so that a tap from [origin] is recorded before the page opens. The record is what the
 * page's stage cover matches on. Without a transition this is just [onOpenBook].
 */
@Composable
internal fun rememberBookCoverOpener(origin: BookCoverOrigin, onOpenBook: (String) -> Unit): (String) -> Unit {
    val transition = LocalBookCoverTransition.current
    val scope = rememberCoroutineScope()
    val open by rememberUpdatedState(onOpenBook)
    return remember(transition, origin, scope) {
        { uuid: String ->
            transition?.let { t ->
                t.state.opened(uuid, origin)
                scope.launch { withFrameNanos { frame -> t.state.openFrameStarted(uuid, frame) } }
            }
            open(uuid)
        }
    }
}
