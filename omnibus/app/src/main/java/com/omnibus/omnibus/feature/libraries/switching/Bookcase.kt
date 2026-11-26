package com.omnibus.omnibus.feature.libraries.switching

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.libraries.LibraryMonogram
import com.omnibus.omnibus.feature.libraries.Monogram
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** One library's spine: its monogram and its height, fixed per library (see [SpineHeights]). */
data class BookcaseSpine(val libraryId: String, val monogram: Monogram, val height: Dp)

/** What the bookcase is doing: resting, or lifting one library's spine off and another's on. */
sealed interface BookcaseTransition {

    /** Nothing is moving; the raised spine stays raised. */
    data object Still : BookcaseTransition

    /** The current spine settles, then the new spine lifts. */
    data class Lift(val fromId: String, val toId: String) : BookcaseTransition
}

/**
 * The bookcase's content: every library's spine in list order, the raised library, and the transition.
 *
 * @property raisedLibraryId The library whose spine is raised when resting; during a lift, the target.
 */
data class BookcaseState(
    val spines: List<BookcaseSpine>,
    val raisedLibraryId: String?,
    val transition: BookcaseTransition,
)

/** Forces the reduced-motion path on or off in tests. Null reads the system's remove-animations setting. */
internal val LocalRemoveAnimations = staticCompositionLocalOf<Boolean?> { null }

/** Whether the system's remove-animations setting is on: the animator duration scale is zero. */
@Composable
internal fun removeAnimations(): Boolean = LocalRemoveAnimations.current
    ?: (Settings.Global.getFloat(LocalContext.current.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)

/** Test tag of one spine. It exists for tests only: the bookcase is decorative and has no semantics for TalkBack. */
internal fun bookcaseSpineTag(libraryId: String): String = "bookcase-spine-$libraryId"

/** The shelf's height: the tallest spine (112 dp) plus its raise (16 dp), over the plank (6 dp). */
private val SHELF_HEIGHT: Dp = 134.dp

private val PLANK_HEIGHT: Dp = 6.dp

private val MONOGRAM_SIZE: Dp = 24.dp

/**
 * The shelf of spines (#369): one spine per library, raised in accent when it is the active one. A lift settles the
 * raised spine, then raises the new one, in [BookcaseGeometry.LIFT]. Under remove-animations the lift is one step.
 *
 * Decorative: it has no text and no focus for TalkBack.
 */
@Composable
fun Bookcase(state: BookcaseState, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val instant = removeAnimations()
    val raises = remember { SpineRaises() }

    LaunchedEffect(state, instant) {
        when (val transition = state.transition) {
            BookcaseTransition.Still -> state.spines.forEach { spine ->
                raises.of(spine.libraryId).snapTo(if (spine.libraryId == state.raisedLibraryId) 1f else 0f)
            }
            is BookcaseTransition.Lift -> {
                val settle = raises.of(transition.fromId)
                val rise = raises.of(transition.toId)
                if (instant) {
                    settle.snapTo(0f)
                    rise.snapTo(1f)
                } else {
                    val half = (BookcaseGeometry.LIFT / 2).inWholeMilliseconds.toInt()
                    settle.animateTo(0f, tween(half))
                    rise.animateTo(1f, tween(half))
                }
            }
        }
    }

    BoxWithConstraints(modifier.widthIn(max = BookcaseGeometry.SHELF_MAX_WIDTH).fillMaxWidth()) {
        val slots = BookcaseGeometry.layout(state.spines.size, maxWidth)
        Box(Modifier.fillMaxWidth().height(SHELF_HEIGHT)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = BookcaseGeometry.SHELF_MARGIN)
                    .fillMaxWidth()
                    .height(PLANK_HEIGHT)
                    .clip(RoundedCornerShape(3.dp))
                    .background(palette.line2),
            )
            state.spines.forEachIndexed { index, spine ->
                key(spine.libraryId) {
                    val raise = raises.of(spine.libraryId, initialFraction(spine.libraryId, state))
                    val fraction = raise.value
                    Spine(
                        spine = spine,
                        fraction = fraction,
                        modifier = Modifier.offset(
                            x = slots[index].x,
                            y = SHELF_HEIGHT - PLANK_HEIGHT - spine.height - BookcaseGeometry.RAISE * fraction,
                        ),
                    )
                }
            }
        }
    }
}

/** Where a spine starts before any animation: the resting raise, or, mid-lift, the spine that is leaving. */
private fun initialFraction(libraryId: String, state: BookcaseState): Float = when (val transition = state.transition) {
    BookcaseTransition.Still -> if (libraryId == state.raisedLibraryId) 1f else 0f
    is BookcaseTransition.Lift -> if (libraryId == transition.fromId) 1f else 0f
}

/** One spine. [fraction] is how raised it is: 0 on the shelf, 1 fully raised, in accent. */
@Composable
private fun Spine(spine: BookcaseSpine, fraction: Float, modifier: Modifier) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(3.dp)
    val raisedFill = palette.accent.copy(alpha = 0.18f).compositeOver(palette.bg0)
    Box(
        modifier
            .size(BookcaseGeometry.SPINE_WIDTH, spine.height)
            .clearAndSetSemantics { this[SemanticsProperties.TestTag] = bookcaseSpineTag(spine.libraryId) }
            .clip(shape)
            .background(lerp(palette.bg2, raisedFill, fraction))
            .border(1.dp, palette.accent.copy(alpha = fraction), shape),
    ) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp)
                .width(16.dp)
                .height(2.dp)
                .background(palette.accent.copy(alpha = 0.5f)),
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(3.dp)
                .background(palette.line2),
        )
        CompositionLocalProvider(LocalContentColor provides lerp(palette.ink2, palette.accentText, fraction)) {
            LibraryMonogram(
                monogram = spine.monogram,
                size = MONOGRAM_SIZE,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }
    }
}

/** The raise of each spine, kept across recompositions so a lift animates from where the spine is. */
private class SpineRaises {
    private val raises = mutableMapOf<String, Animatable<Float, AnimationVector1D>>()

    fun of(libraryId: String, initial: Float = 0f): Animatable<Float, AnimationVector1D> =
        raises.getOrPut(libraryId) { Animatable(initial) }
}
