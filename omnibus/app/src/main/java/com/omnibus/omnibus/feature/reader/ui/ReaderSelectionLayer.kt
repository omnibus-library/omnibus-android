package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.HighlightMenuAction
import com.omnibus.omnibus.feature.reader.menuTarget
import kotlin.math.max

/** Test tags on the selection layer's nodes, so tests can find them without depending on the layout. */
internal object ReaderSelectionTags {
    const val WASH = "reader_selection_wash"
    const val START_HANDLE = "reader_selection_start"
    const val END_HANDLE = "reader_selection_end"
    const val MENU = "reader_selection_menu"
}

/** The handles' geometry in dp, after iOS `ReaderSelectionLayer`. */
private object HandleMetrics {
    const val TOUCH_WIDTH = 40f
    const val TOUCH_MIN_HEIGHT = 40f

    /** The touch box is the line plus this, so it reaches past the knob on both sides. */
    const val TOUCH_EXTRA = 22f

    /** How far the touch box is pushed outward, above the start line or below the end line. */
    const val BIAS = 10f
    const val BAR_WIDTH = 2f
    const val BAR_MIN_HEIGHT = 12f
    const val KNOB_DIAMETER = 11f

    /** The knob's centre is this far beyond the line's edge. */
    const val KNOB_REACH = 6.5f
}

/** The last anchor each handle had, so a handle whose caret has left the page is still drawn and dragged where it was. */
private class LastAnchors {
    var start: SelectionHandle? = null
    var end: SelectionHandle? = null
}

/**
 * The selection drawn over the page: the tint over each line's rects, two handles, and the highlight menu when
 * [menuShown]. All coordinates are the page's dp, which equal its CSS px, so [modifier] must be the WebView's box.
 *
 * Only the handles and the menu take touches; everywhere else the overlay passes them to the page. A handle drag reports
 * through [onGrab], [onDrag] (the caret and the finger, in dp) and [onRelease]. The layer and its wash and handles are
 * hidden from accessibility, and the menu's controls are the only nodes it exposes.
 *
 * @param selection The page's live selection, settled or being dragged.
 * @param menuShown Whether the highlight menu is up.
 * @param theme The reading theme, which sets the tint and the menu's colours.
 * @param onGrab A handle was grabbed.
 * @param onDrag The dragged handle's caret and the finger, in dp.
 * @param onRelease The handle was let go, or the layer left while a handle was held.
 * @param actions The actions the menu's second row shows, in order.
 * @param onColorChosen A highlight colour was chosen from the menu.
 * @param onAction An action was chosen from the menu.
 */
@Composable
internal fun ReaderSelectionOverlay(
    selection: SelectionData,
    menuShown: Boolean,
    theme: ReaderTheme,
    onGrab: (SelectionEdge) -> Unit,
    onDrag: (caretX: Float, caretY: Float, fingerX: Float) -> Unit,
    onRelease: () -> Unit,
    actions: List<HighlightMenuAction>,
    onColorChosen: (HighlightColor) -> Unit,
    onAction: (HighlightMenuAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = readerSelectionTint(theme)
    val washAlpha = readerSelectionWashAlpha(theme)
    val lastAnchors = remember { LastAnchors() }
    selection.start?.let { lastAnchors.start = it }
    selection.end?.let { lastAnchors.end = it }
    val held = remember { mutableStateOf<SelectionEdge?>(null) }
    val currentOnRelease by rememberUpdatedState(onRelease)
    // A drag that the selection's disappearing cuts short still settles, so the page is never left mid-drag.
    DisposableEffect(Unit) {
        onDispose { if (held.value != null) currentOnRelease() }
    }

    BoxWithConstraints(modifier) {
        selection.rects.forEach { rect ->
            Box(
                Modifier
                    .offset((rect.x - 1).toFloat().dp, (rect.y - 0.5).toFloat().dp)
                    .size((rect.width + 2).toFloat().dp, (rect.height + 1).toFloat().dp)
                    .background(tint.copy(alpha = washAlpha), RoundedCornerShape(3.5.dp))
                    .clearAndSetSemantics { testTag = ReaderSelectionTags.WASH },
            )
        }
        // A handle whose caret is off the page is kept composed, offstage, while it is held (R2).
        val start = lastAnchors.start
        if (start != null && (selection.start != null || held.value == SelectionEdge.Start)) {
            SelectionHandleNode(
                edge = SelectionEdge.Start,
                anchor = start,
                live = selection.start != null,
                tint = tint,
                held = held,
                onGrab = onGrab,
                onDrag = onDrag,
                onRelease = onRelease,
            )
        }
        val end = lastAnchors.end
        if (end != null && (selection.end != null || held.value == SelectionEdge.End)) {
            SelectionHandleNode(
                edge = SelectionEdge.End,
                anchor = end,
                live = selection.end != null,
                tint = tint,
                held = held,
                onGrab = onGrab,
                onDrag = onDrag,
                onRelease = onRelease,
            )
        }
        if (menuShown) {
            selectionMenuPlacement(selection.rects, maxWidth.value, maxHeight.value)?.let { placement ->
                ReaderSelectionMenu(
                    placement = placement,
                    colors = readerChromeColors(theme),
                    target = selection.menuTarget,
                    actions = actions,
                    onColorChosen = onColorChosen,
                    onAction = onAction,
                )
            }
        }
    }
}

/**
 * One selection handle: a bar the height of its line with a knob beyond the line's edge, in a touch box that the drag
 * reads. The drag is keyed on [edge] alone, so the moving anchor is read through [anchor]'s updated state and the
 * gesture survives each frame's new anchor. A handle with no [live] caret is drawn transparent at its last anchor.
 */
@Composable
private fun SelectionHandleNode(
    edge: SelectionEdge,
    anchor: SelectionHandle,
    live: Boolean,
    tint: Color,
    held: MutableState<SelectionEdge?>,
    onGrab: (SelectionEdge) -> Unit,
    onDrag: (caretX: Float, caretY: Float, fingerX: Float) -> Unit,
    onRelease: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val currentAnchor by rememberUpdatedState(anchor)
    val currentOnGrab by rememberUpdatedState(onGrab)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnRelease by rememberUpdatedState(onRelease)

    val lineTop = anchor.y.toFloat()
    val lineHeight = anchor.height.toFloat()
    val caretX = anchor.x.toFloat()
    val lineCentre = lineTop + lineHeight / 2
    val touchHeight = max(HandleMetrics.TOUCH_MIN_HEIGHT, lineHeight + HandleMetrics.TOUCH_EXTRA)
    val isStart = edge == SelectionEdge.Start
    val touchTop = lineCentre - touchHeight / 2 + if (isStart) -HandleMetrics.BIAS else HandleMetrics.BIAS
    val touchLeft = caretX - HandleMetrics.TOUCH_WIDTH / 2
    val currentTouchLeft by rememberUpdatedState(touchLeft)
    val barHeight = max(HandleMetrics.BAR_MIN_HEIGHT, lineHeight)
    val barTop = lineCentre - barHeight / 2 - touchTop
    val knobCentre = if (isStart) lineTop - HandleMetrics.KNOB_REACH else lineTop + lineHeight + HandleMetrics.KNOB_REACH
    val knobTop = knobCentre - HandleMetrics.KNOB_DIAMETER / 2 - touchTop

    Box(
        Modifier
            .offset(touchLeft.dp, touchTop.dp)
            .size(HandleMetrics.TOUCH_WIDTH.dp, touchHeight.dp)
            .alpha(if (live) 1f else 0f)
            .clearAndSetSemantics {
                testTag = if (isStart) ReaderSelectionTags.START_HANDLE else ReaderSelectionTags.END_HANDLE
            }
            .pointerInput(edge) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var travel = Offset.Zero
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        ?: return@awaitEachGesture
                    travel += slop.position - down.position
                    // The caret where the drag starts, and the finger's offset from it, so the caret follows the finger.
                    val now = currentAnchor
                    val origin = Offset(now.x.toFloat(), (now.y + now.height / 2).toFloat())
                    val fingerOffset = currentTouchLeft + down.position.x.toDp().value - origin.x
                    held.value = edge
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnGrab(edge)
                    fun report() {
                        val caretX = origin.x + travel.x.toDp().value
                        currentOnDrag(caretX, origin.y + travel.y.toDp().value, caretX + fingerOffset)
                    }
                    report()
                    // Released on a normal up and on a cancel alike.
                    drag(slop.id) { change ->
                        travel += change.positionChange()
                        change.consume()
                        report()
                    }
                    held.value = null
                    currentOnRelease()
                }
            },
    ) {
        Box(
            Modifier
                .offset((HandleMetrics.TOUCH_WIDTH / 2 - HandleMetrics.BAR_WIDTH / 2).dp, barTop.dp)
                .size(HandleMetrics.BAR_WIDTH.dp, barHeight.dp)
                .background(tint, RoundedCornerShape(1.dp)),
        )
        Box(
            Modifier
                .offset((HandleMetrics.TOUCH_WIDTH / 2 - HandleMetrics.KNOB_DIAMETER / 2).dp, knobTop.dp)
                .size(HandleMetrics.KNOB_DIAMETER.dp)
                .shadow(1.dp, CircleShape)
                .background(tint, CircleShape),
        )
    }
}
